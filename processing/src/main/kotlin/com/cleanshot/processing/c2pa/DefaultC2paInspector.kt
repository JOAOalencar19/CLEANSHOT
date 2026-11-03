package com.cleanshot.processing.c2pa

import com.cleanshot.domain.engine.C2paInspector
import com.cleanshot.domain.model.AiDeclaration
import com.cleanshot.domain.model.C2paManifestInfo
import com.cleanshot.domain.model.CheckState
import com.cleanshot.domain.model.ImageFormat
import com.cleanshot.domain.model.ProvenanceCheck
import com.cleanshot.domain.model.ProvenanceReport
import com.cleanshot.domain.model.ProvenanceVerdict
import com.cleanshot.domain.model.SignerInfo
import com.cleanshot.processing.ai.AiDeclarationDetector
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Native, read-only C2PA inspector.
 *
 * Implemented checks (all offline):
 *  1. location and structural parsing of the JUMBF manifest store;
 *  2. claim decoding (generator, title, assertions, actions, ingredients);
 *  3. hashed-URI check of every referenced assertion;
 *  4. COSE_Sign1 signature check against the embedded leaf certificate;
 *  5. hard binding check for `c2pa.hash.data` (JPEG/PNG/WebP).
 *
 * Not implemented: certificate trust list / revocation / timestamp validation, BMFF and box hash
 * bindings, remote or sidecar manifests. Those are reported as "não verificado".
 */
class DefaultC2paInspector : C2paInspector {

    override fun inspect(bytes: ByteArray, format: ImageFormat): ProvenanceReport {
        val notes = mutableListOf<String>()
        val limitations = ProvenanceReport.STANDARD_LIMITATIONS.toMutableList()
        val located = try {
            C2paLocator.locate(bytes, format)
        } catch (e: Exception) {
            C2paLocator.Result.Unsupported("Não foi possível percorrer a estrutura do arquivo: ${e.message}")
        }
        when (located) {
            is C2paLocator.Result.NotFound -> return empty(ProvenanceVerdict.NO_CREDENTIALS, notes, limitations)
            is C2paLocator.Result.Unsupported -> {
                limitations += located.reason
                return empty(ProvenanceVerdict.NO_CREDENTIALS, notes, limitations)
            }
            is C2paLocator.Result.Found -> notes += "Manifesto C2PA localizado em: ${located.location}"
        }
        val jumbf = (located as C2paLocator.Result.Found).jumbf
        val store = try {
            Jumbf.parseAll(jumbf).firstOrNull { it.label == "c2pa" }
        } catch (e: Exception) {
            notes += "Estrutura JUMBF inválida: ${e.message}"
            null
        }
        if (store == null) {
            return ProvenanceReport(ProvenanceVerdict.INCONCLUSIVE, true, jumbf.size, emptyList(), emptyList(), emptyList(), notes, limitations)
        }
        val manifestBoxes = store.children.filter { it.isSuperbox }
        val activeLabel = manifestBoxes.lastOrNull()?.label
        val declarations = mutableListOf<AiDeclaration>()
        val tools = linkedSetOf<String>()
        val manifests = manifestBoxes.map { m ->
            val active = m.label == activeLabel
            inspectManifest(m, active, bytes, format, declarations, tools, notes)
        }
        val active = manifests.firstOrNull { it.isActive }
        val verdict = when {
            active == null -> ProvenanceVerdict.INCONCLUSIVE
            active.checks.any { it.state == CheckState.FAILED } -> ProvenanceVerdict.CREDENTIALS_INVALID
            active.checks.filter { it.name in REQUIRED_FOR_VERIFIED }.let { req -> req.size == REQUIRED_FOR_VERIFIED.size && req.all { it.state == CheckState.PASSED } } ->
                ProvenanceVerdict.CREDENTIALS_VERIFIED
            else -> ProvenanceVerdict.CREDENTIALS_FOUND
        }
        return ProvenanceReport(verdict, true, jumbf.size, manifests, declarations, tools.toList(), notes, limitations)
    }

    private fun empty(v: ProvenanceVerdict, notes: List<String>, lim: List<String>) =
        ProvenanceReport(v, false, 0, emptyList(), emptyList(), emptyList(), notes, lim)

    private fun inspectManifest(
        m: JumbfBox,
        active: Boolean,
        asset: ByteArray,
        format: ImageFormat,
        declarations: MutableList<AiDeclaration>,
        tools: MutableSet<String>,
        notes: MutableList<String>,
    ): C2paManifestInfo {
        val checks = mutableListOf<ProvenanceCheck>()
        val claimBox = m.child("c2pa.claim.v2") ?: m.child("c2pa.claim")
        val claimBytes = claimBox?.firstContent("cbor")?.payload
        val claim = claimBytes?.let { runCatching { CborReader.decode(it).cborMap() }.getOrNull() }
        checks += ProvenanceCheck(
            "Estrutura do manifesto",
            if (claim != null) CheckState.PASSED else CheckState.FAILED,
            if (claim != null) "Claim (${claimBox?.label}) decodificado." else "Claim ausente ou ilegível.",
        )

        val generator = claim?.let { generatorOf(it) }
        generator?.let { tools += it }
        val title = (claim?.get("dc:title") ?: claim?.get("title")).cborString()
        val dcFormat = claim?.get("dc:format").cborString()
        val alg = claim?.get("alg").cborString() ?: "sha256"

        // Assertions
        val assertionStore = m.child("c2pa.assertions")
        val assertionLabels = assertionStore?.children?.filter { it.isSuperbox }?.mapNotNull { it.label } ?: emptyList()
        val refs = (claim?.get("assertions").cborList().orEmpty() +
            claim?.get("created_assertions").cborList().orEmpty() +
            claim?.get("gathered_assertions").cborList().orEmpty()).mapNotNull { it.cborMap() }
        if (refs.isNotEmpty() && assertionStore != null) {
            var ok = 0; var bad = 0; var missing = 0
            for (ref in refs) {
                val url = ref["url"].cborString() ?: continue
                val label = url.substringAfterLast("/")
                val hash = ref["hash"] as? ByteArray
                val box = assertionStore.child(label)
                if (box == null || hash == null) { missing++; continue }
                val refAlg = ref["alg"].cborString() ?: alg
                if (digest(refAlg, box.superboxContent)?.contentEquals(hash) == true) ok++ else bad++
            }
            checks += ProvenanceCheck(
                "Integridade das asserções",
                when { bad > 0 -> CheckState.FAILED; missing > 0 -> CheckState.NOT_CHECKED; else -> CheckState.PASSED },
                "$ok conferem" + (if (bad > 0) ", $bad divergentes" else "") + (if (missing > 0) ", $missing não localizadas" else ""),
            )
        }

        val actions = mutableListOf<String>()
        val ingredients = mutableListOf<String>()
        var hardBinding: ProvenanceCheck? = null
        for (label in assertionLabels) {
            val box = assertionStore!!.child(label) ?: continue
            val content = box.firstContent("cbor")?.payload?.let { runCatching { CborReader.decode(it) }.getOrNull() }
            when {
                label.startsWith("c2pa.actions") -> content.cborMap()?.get("actions").cborList()?.forEach { a ->
                    val am = a.cborMap() ?: return@forEach
                    val action = am["action"].cborString() ?: return@forEach
                    val agent = am["softwareAgent"].let { it.cborString() ?: it.cborMap()?.get("name").cborString() }
                    val source = am["digitalSourceType"].cborString()
                    agent?.let { tools += it }
                    actions += buildString {
                        append(ACTION_NAMES[action] ?: action)
                        agent?.let { append(" · ").append(it) }
                        source?.let { append(" · ").append(sourceLabel(it)) }
                    }
                    val key = source?.let { AiDeclarationDetector.digitalSourceTypeKey(it) }
                    if (key != null && key in AiDeclarationDetector.AI_SOURCE_KEYS) {
                        declarations += AiDeclaration("C2PA ${m.label?.takeLast(12)} · $label", "${ACTION_NAMES[action] ?: action}: ${AiDeclarationDetector.AI_DIGITAL_SOURCE_TYPES[key]}", signed = true)
                    } else if (agent != null && AiDeclarationDetector.detect(agent) != null) {
                        declarations += AiDeclaration("C2PA · $label", "Ferramenta declarada: $agent", signed = true)
                    }
                }
                label.startsWith("c2pa.ingredient") -> content.cborMap()?.let { im ->
                    ingredients += listOfNotNull(
                        im["dc:title"].cborString() ?: im["title"].cborString(),
                        im["relationship"].cborString()?.let { RELATIONSHIPS[it] ?: it },
                    ).joinToString(" · ")
                }
                label.startsWith("c2pa.training-mining") || label.startsWith("cawg.training-mining") -> content.cborMap()?.get("entries").cborMap()?.forEach { (k, v) ->
                    val use = v.cborMap()?.get("use").cborString()
                    notes += "Preferência de mineração/treinamento: $k = $use"
                }
                label == "c2pa.hash.data" && active -> hardBinding = dataHashCheck(content.cborMap(), alg, asset)
                (label.startsWith("c2pa.hash.bmff") || label == "c2pa.hash.boxes" || label.startsWith("c2pa.hash.collection")) && active ->
                    hardBinding = ProvenanceCheck(HARD_BINDING, CheckState.NOT_CHECKED, "Vinculação do tipo '$label' não é verificada por esta versão.")
            }
        }
        if (active) {
            checks += hardBinding ?: ProvenanceCheck(HARD_BINDING, CheckState.NOT_CHECKED, "Nenhuma vinculação de conteúdo reconhecida.")
        }

        // Signature
        val sigBox = m.child("c2pa.signature")?.firstContent("cbor")?.payload
        var signer: SignerInfo? = null
        if (sigBox == null || claimBytes == null) {
            checks += ProvenanceCheck(SIGNATURE, CheckState.FAILED, "Assinatura ausente.")
        } else {
            try {
                val parsed = CoseVerifier.parse(sigBox)
                parsed.certificates.firstOrNull()?.let { c ->
                    val df = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                    signer = SignerInfo(
                        subject = c.subjectX500Principal.name,
                        issuer = c.issuerX500Principal.name,
                        validFrom = df.format(c.notBefore),
                        validUntil = df.format(c.notAfter),
                        algorithm = CoseVerifier.algName(parsed.alg),
                    )
                }
                checks += when (val r = CoseVerifier.verify(parsed, claimBytes)) {
                    CoseVerifier.Outcome.Valid -> ProvenanceCheck(SIGNATURE, CheckState.PASSED, "Assinatura ${CoseVerifier.algName(parsed.alg)} confere com o certificado incluído.")
                    CoseVerifier.Outcome.Invalid -> ProvenanceCheck(SIGNATURE, CheckState.FAILED, "A assinatura não corresponde ao conteúdo do claim.")
                    is CoseVerifier.Outcome.Unsupported -> ProvenanceCheck(SIGNATURE, CheckState.NOT_CHECKED, r.reason)
                }
            } catch (e: Exception) {
                checks += ProvenanceCheck(SIGNATURE, CheckState.NOT_CHECKED, "Assinatura COSE ilegível: ${e.message}")
            }
            if (parsed(sigBox)?.unprotectedHeader?.containsKey("sigTst") == true || parsed(sigBox)?.unprotectedHeader?.containsKey("sigTst2") == true) {
                checks += ProvenanceCheck("Carimbo de tempo", CheckState.NOT_CHECKED, "Há carimbo de tempo (RFC 3161), não validado offline.")
            }
        }
        checks += ProvenanceCheck("Confiança no emissor", CheckState.NOT_CHECKED, "A cadeia do certificado não é validada contra a lista de confiança C2PA (offline).")

        return C2paManifestInfo(
            label = m.label ?: "(sem rótulo)",
            isActive = active,
            claimGenerator = generator,
            title = title,
            format = dcFormat,
            assertions = assertionLabels,
            actions = actions,
            ingredients = ingredients,
            signer = signer,
            checks = checks,
        )
    }

    private fun parsed(sig: ByteArray) = runCatching { CoseVerifier.parse(sig) }.getOrNull()

    private fun generatorOf(claim: Map<Any?, Any?>): String? {
        claim["claim_generator"].cborString()?.let { return it }
        val info = claim["claim_generator_info"]
        val first = info.cborMap() ?: info.cborList()?.firstOrNull().cborMap() ?: return null
        val name = first["name"].cborString() ?: return null
        return listOfNotNull(name, first["version"].cborString()).joinToString(" ")
    }

    private fun dataHashCheck(dh: Map<Any?, Any?>?, claimAlg: String, asset: ByteArray): ProvenanceCheck {
        if (dh == null) return ProvenanceCheck(HARD_BINDING, CheckState.NOT_CHECKED, "Asserção c2pa.hash.data ilegível.")
        val expected = dh["hash"] as? ByteArray ?: return ProvenanceCheck(HARD_BINDING, CheckState.NOT_CHECKED, "Hash ausente.")
        val alg = dh["alg"].cborString() ?: claimAlg
        val md = messageDigest(alg) ?: return ProvenanceCheck(HARD_BINDING, CheckState.NOT_CHECKED, "Algoritmo $alg não suportado.")
        val exclusions = dh["exclusions"].cborList().orEmpty().mapNotNull { e ->
            val m = e.cborMap() ?: return@mapNotNull null
            val s = m["start"].cborLong() ?: return@mapNotNull null
            val l = m["length"].cborLong() ?: return@mapNotNull null
            s to l
        }.sortedBy { it.first }
        var pos = 0L
        for ((s, l) in exclusions) {
            if (s < pos || s + l > asset.size) {
                return ProvenanceCheck(HARD_BINDING, CheckState.FAILED, "As faixas de exclusão não correspondem ao arquivo (arquivo alterado após a assinatura).")
            }
            md.update(asset, pos.toInt(), (s - pos).toInt())
            pos = s + l
        }
        md.update(asset, pos.toInt(), (asset.size - pos).toInt())
        return if (md.digest().contentEquals(expected)) {
            ProvenanceCheck(HARD_BINDING, CheckState.PASSED, "O conteúdo do arquivo corresponde ao hash assinado.")
        } else {
            ProvenanceCheck(HARD_BINDING, CheckState.FAILED, "O conteúdo do arquivo foi alterado depois da assinatura (hash divergente).")
        }
    }

    private fun messageDigest(alg: String): MessageDigest? = when (alg.lowercase()) {
        "sha256" -> MessageDigest.getInstance("SHA-256")
        "sha384" -> MessageDigest.getInstance("SHA-384")
        "sha512" -> MessageDigest.getInstance("SHA-512")
        else -> null
    }

    private fun digest(alg: String, data: ByteArray): ByteArray? = messageDigest(alg)?.digest(data)

    private fun sourceLabel(uri: String): String {
        val key = AiDeclarationDetector.digitalSourceTypeKey(uri)
        return key?.let { AiDeclarationDetector.AI_DIGITAL_SOURCE_TYPES[it] } ?: uri.substringAfterLast('/')
    }

    companion object {
        const val SIGNATURE = "Assinatura do claim"
        const val HARD_BINDING = "Vinculação ao conteúdo"
        val REQUIRED_FOR_VERIFIED = setOf("Estrutura do manifesto", SIGNATURE, HARD_BINDING, "Integridade das asserções")

        val ACTION_NAMES = mapOf(
            "c2pa.created" to "Criado",
            "c2pa.opened" to "Aberto",
            "c2pa.edited" to "Editado",
            "c2pa.edited.metadata" to "Metadados editados",
            "c2pa.placed" to "Elemento inserido",
            "c2pa.cropped" to "Recortado",
            "c2pa.resized" to "Redimensionado",
            "c2pa.color_adjustments" to "Ajustes de cor",
            "c2pa.filtered" to "Filtro aplicado",
            "c2pa.converted" to "Convertido",
            "c2pa.drawing" to "Desenho",
            "c2pa.orientation" to "Orientação alterada",
            "c2pa.published" to "Publicado",
            "c2pa.transcoded" to "Transcodificado",
            "c2pa.repackaged" to "Reempacotado",
            "c2pa.removed" to "Elemento removido",
            "c2pa.unknown" to "Ação desconhecida",
            "c2pa.watermarked" to "Marca d'água adicionada",
        )
        val RELATIONSHIPS = mapOf("parentOf" to "origem", "componentOf" to "componente", "inputTo" to "entrada")
    }
}
