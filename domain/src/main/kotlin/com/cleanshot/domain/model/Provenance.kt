package com.cleanshot.domain.model

/**
 * Result categories displayed by the AI Provenance Inspector.
 *
 * IMPORTANT: absence of credentials never means "not AI-generated". Conventional metadata says
 * nothing about invisible watermarks or signals embedded in the pixels, which CleanShot does
 * not analyse.
 */
enum class ProvenanceVerdict(val title: String, val explanation: String) {
    CREDENTIALS_VERIFIED(
        "Credenciais verificadas",
        "A assinatura do manifesto C2PA confere com o certificado incluído e o conteúdo da imagem corresponde ao hash declarado. " +
            "A confiabilidade do emissor (lista de confiança C2PA) não é avaliada offline.",
    ),
    CREDENTIALS_FOUND(
        "Credenciais encontradas",
        "Há um manifesto C2PA (Content Credentials) no arquivo. Alguns aspectos não puderam ser verificados localmente.",
    ),
    CREDENTIALS_INVALID(
        "Credenciais inválidas",
        "Há um manifesto C2PA, mas a assinatura não confere ou o arquivo foi alterado depois de assinado.",
    ),
    DECLARED_ORIGIN(
        "Origem declarada",
        "O arquivo contém declarações não assinadas (XMP/IPTC ou texto) sobre a origem ou a ferramenta de criação. " +
            "Elas podem ser editadas por qualquer pessoa e não comprovam a origem.",
    ),
    INCONCLUSIVE(
        "Informações inconclusivas",
        "Foram encontrados indícios parciais (por exemplo, referência a manifesto remoto ou estrutura C2PA incompleta).",
    ),
    NO_CREDENTIALS(
        "Nenhuma credencial encontrada",
        "Não há credenciais de procedência nem declarações de origem. Isso NÃO indica que a imagem seja autêntica " +
            "ou que não tenha sido gerada por IA: metadados podem ter sido removidos e marcas d'água nos pixels não são analisadas.",
    ),
}

/** Overall verification state of one check. */
enum class CheckState(val label: String) { PASSED("Confere"), FAILED("Não confere"), NOT_CHECKED("Não verificado"), NOT_APPLICABLE("Não se aplica") }

data class ProvenanceCheck(
    val name: String,
    val state: CheckState,
    val detail: String,
)

/** A single statement related to AI generation / editing found in the file. */
data class AiDeclaration(
    val source: String,
    val statement: String,
    val signed: Boolean,
)

data class SignerInfo(
    val subject: String,
    val issuer: String,
    val validFrom: String?,
    val validUntil: String?,
    val algorithm: String?,
)

data class C2paManifestInfo(
    val label: String,
    val isActive: Boolean,
    val claimGenerator: String?,
    val title: String?,
    val format: String?,
    val assertions: List<String>,
    val actions: List<String>,
    val ingredients: List<String>,
    val signer: SignerInfo?,
    val checks: List<ProvenanceCheck>,
)

data class ProvenanceReport(
    val verdict: ProvenanceVerdict,
    val c2paPresent: Boolean,
    val manifestStoreBytes: Int,
    val manifests: List<C2paManifestInfo>,
    val aiDeclarations: List<AiDeclaration>,
    val declaredTools: List<String>,
    val notes: List<String>,
    val limitations: List<String>,
) {
    val activeManifest: C2paManifestInfo? get() = manifests.firstOrNull { it.isActive }

    companion object {
        val STANDARD_LIMITATIONS = listOf(
            "A ausência de metadados ou credenciais não prova que a imagem não foi gerada ou editada por IA.",
            "Marcas d'água invisíveis e sinais presentes nos pixels (ex.: SynthID) não são metadados e não são analisados.",
            "A cadeia de confiança dos certificados C2PA não é validada contra a lista oficial de confiança (processamento offline).",
        )
    }
}
