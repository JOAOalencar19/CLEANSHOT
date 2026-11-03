package com.cleanshot.processing.iptc

import com.cleanshot.domain.model.CleaningTarget
import com.cleanshot.domain.model.CleaningTarget as T
import com.cleanshot.domain.model.MetadataCategory as C
import com.cleanshot.domain.model.MetadataEntry
import com.cleanshot.domain.model.MetadataStandard
import com.cleanshot.domain.model.PrivacyLevel as P
import com.cleanshot.domain.model.RemovalPolicy
import com.cleanshot.processing.catalog.FieldInfo
import com.cleanshot.processing.util.ByteSink
import com.cleanshot.processing.util.MalformedImageException
import com.cleanshot.processing.util.u16be
import com.cleanshot.processing.util.u32be
import com.cleanshot.processing.util.u8
import java.security.MessageDigest

/** IPTC-IIM dataset. */
class IimDataSet(val record: Int, val dataset: Int, val data: ByteArray)

/** Photoshop Image Resource Block (8BIM). */
class IrbBlock(val signature: String, val id: Int, val name: ByteArray, val data: ByteArray)

/** Reads and rewrites IPTC-IIM data, including its Photoshop (APP13) container. */
class IptcProcessor {

    data class IrbCleanResult(val payload: ByteArray?, val removedKeys: List<String>, val changes: List<String>)

    fun parseIim(data: ByteArray): List<IimDataSet> {
        val out = mutableListOf<IimDataSet>()
        var p = 0
        while (p + 5 <= data.size) {
            if (data[p] != 0x1C.toByte()) {
                // Padding after the last dataset is common; anything else is corruption.
                if (data.copyOfRange(p, data.size).all { it == 0.toByte() }) break
                throw MalformedImageException("Dados IPTC corrompidos")
            }
            val rec = data.u8(p + 1)
            val ds = data.u8(p + 2)
            var len = data.u16be(p + 3)
            var start = p + 5
            if (len and 0x8000 != 0) {
                val n = len and 0x7FFF
                if (n > 4 || start + n > data.size) throw MalformedImageException("Tamanho IPTC estendido inválido")
                len = 0
                for (k in 0 until n) len = (len shl 8) or data.u8(start + k)
                start += n
            }
            if (start + len > data.size) throw MalformedImageException("Dataset IPTC truncado")
            out += IimDataSet(rec, ds, data.copyOfRange(start, start + len))
            p = start + len
        }
        return out
    }

    fun writeIim(sets: List<IimDataSet>): ByteArray {
        val s = ByteSink()
        for (d in sets) {
            s.u8(0x1C); s.u8(d.record); s.u8(d.dataset)
            if (d.data.size < 0x8000) s.u16be(d.data.size) else { s.u16be(0x8004); s.u32be(d.data.size.toLong()) }
            s.write(d.data)
        }
        return s.toByteArray()
    }

    fun parseIrb(payload: ByteArray, offset: Int = 0): List<IrbBlock> {
        val out = mutableListOf<IrbBlock>()
        var p = offset
        while (p + 12 <= payload.size) {
            val sig = String(payload, p, 4, Charsets.ISO_8859_1)
            if (sig !in IRB_SIGNATURES) throw MalformedImageException("Bloco Photoshop inválido")
            val id = payload.u16be(p + 4)
            val nameLen = payload.u8(p + 6)
            val namePadded = (nameLen + 1 + 1) and 1.inv()
            val name = payload.copyOfRange(p + 7, p + 7 + nameLen)
            val sizePos = p + 6 + namePadded
            if (sizePos + 4 > payload.size) throw MalformedImageException("Bloco Photoshop truncado")
            val size = payload.u32be(sizePos).toInt()
            val dataPos = sizePos + 4
            if (size < 0 || dataPos + size > payload.size) throw MalformedImageException("Bloco Photoshop truncado")
            out += IrbBlock(sig, id, name, payload.copyOfRange(dataPos, dataPos + size))
            p = dataPos + size + (size and 1)
        }
        return out
    }

    fun writeIrb(blocks: List<IrbBlock>): ByteArray {
        val s = ByteSink()
        for (b in blocks) {
            s.ascii(b.signature); s.u16be(b.id)
            s.u8(b.name.size); s.write(b.name)
            if ((b.name.size + 1) % 2 == 1) s.u8(0)
            s.u32be(b.data.size.toLong()); s.write(b.data)
            if (b.data.size % 2 == 1) s.u8(0)
        }
        return s.toByteArray()
    }

    fun entries(iim: ByteArray): List<MetadataEntry> {
        val sets = parseIim(iim)
        val utf8 = isUtf8(sets)
        return sets.filter { it.record == 2 && it.dataset != 0 }.groupBy { it.dataset }.map { (ds, list) ->
            val info = info(ds)
            val value = list.joinToString("; ") { decode(it.data, utf8) }
            MetadataEntry(
                key = key(2, ds),
                name = info.name,
                value = value.take(2000),
                standard = MetadataStandard.IPTC,
                category = info.category,
                description = info.description,
                privacy = info.privacy,
                removal = if (info.target != null) RemovalPolicy.REMOVABLE else RemovalPolicy.NOT_APPLICABLE,
                cleaningTarget = info.target,
                details = "IPTC-IIM 2:%03d".format(ds) + if (list.size > 1) " · ${list.size} valores" else "",
            )
        }
    }

    /** Returns the cleaned IIM bytes (null if nothing meaningful remains) and removed keys. */
    fun cleanIim(iim: ByteArray, targets: Set<CleaningTarget>): Pair<ByteArray?, List<String>> {
        val sets = parseIim(iim)
        val removed = linkedSetOf<String>()
        val kept = sets.filter { d ->
            val t = if (d.record == 2) info(d.dataset).target else null
            val remove = t != null && t in targets
            if (remove) removed += key(d.record, d.dataset)
            !remove
        }
        if (removed.isEmpty()) return iim to emptyList()
        val meaningful = kept.any { it.record == 2 && it.dataset != 0 }
        return (if (meaningful) writeIim(kept) else null) to removed.toList()
    }

    /** Cleans a Photoshop IRB payload (APP13 without the "Photoshop 3.0\0" header). */
    fun cleanIrb(irb: ByteArray, targets: Set<CleaningTarget>): IrbCleanResult {
        val blocks = parseIrb(irb)
        val removed = mutableListOf<String>()
        val changes = mutableListOf<String>()
        val out = mutableListOf<IrbBlock>()
        var newIim: ByteArray? = null
        var iimChanged = false
        for (b in blocks) {
            when {
                b.id == IPTC_ID -> {
                    val (cleaned, keys) = cleanIim(b.data, targets)
                    if (keys.isNotEmpty()) {
                        iimChanged = true
                        removed += keys
                        changes += "IPTC: ${keys.size} campo(s) removido(s)"
                        if (cleaned != null) { out += IrbBlock(b.signature, b.id, b.name, cleaned); newIim = cleaned }
                        else changes += "IPTC: registro vazio eliminado"
                    } else { out += b; newIim = b.data }
                }
                b.id in THUMBNAIL_IDS && CleaningTarget.THUMBNAIL in targets -> {
                    removed += "IPTC:IRB:Thumbnail"; changes += "Photoshop: miniatura removida"
                }
                else -> out += b
            }
        }
        if (iimChanged) {
            val idx = out.indexOfFirst { it.id == DIGEST_ID }
            if (idx >= 0) {
                val iim = newIim
                if (iim == null) out.removeAt(idx)
                else out[idx] = IrbBlock(out[idx].signature, DIGEST_ID, out[idx].name, MessageDigest.getInstance("MD5").digest(iim))
                changes += "Photoshop: resumo (digest) IPTC atualizado"
            }
        }
        if (removed.isEmpty()) return IrbCleanResult(irb, emptyList(), emptyList())
        return IrbCleanResult(if (out.isEmpty()) null else writeIrb(out), removed, changes)
    }

    private fun isUtf8(sets: List<IimDataSet>): Boolean =
        sets.firstOrNull { it.record == 1 && it.dataset == 90 }?.data?.contentEquals(byteArrayOf(0x1B, 0x25, 0x47)) == true

    private fun decode(b: ByteArray, utf8: Boolean): String {
        if (utf8) return String(b, Charsets.UTF_8).trim()
        // Many writers emit UTF-8 without declaring it.
        val asUtf8 = String(b, Charsets.UTF_8)
        return (if ('�' !in asUtf8) asUtf8 else String(b, Charsets.ISO_8859_1)).trim()
    }

    companion object {
        const val IPTC_ID = 0x0404
        const val DIGEST_ID = 0x0425
        val THUMBNAIL_IDS = setOf(0x0409, 0x040C)
        val IRB_SIGNATURES = setOf("8BIM", "MeSa", "PHUT", "AgHg", "DCSR")

        fun key(record: Int, ds: Int) = "IPTC:%d:%03d".format(record, ds)

        private fun f(name: String, desc: String, c: C, p: P, t: T?) = FieldInfo(name, desc, c, p, t)

        val CATALOG: Map<Int, FieldInfo> = mapOf(
            5 to f("Título do objeto", "Nome curto da imagem.", C.DESCRIPTION, P.LOW, T.COMMENTS),
            7 to f("Status editorial", "Estado editorial.", C.OTHER, P.LOW, T.IPTC_EDITORIAL),
            10 to f("Urgência", "Prioridade editorial.", C.OTHER, P.NONE, T.IPTC_EDITORIAL),
            12 to f("Referência de assunto", "Códigos de assunto IPTC.", C.DESCRIPTION, P.LOW, T.KEYWORDS),
            15 to f("Categoria", "Categoria editorial.", C.OTHER, P.NONE, T.IPTC_EDITORIAL),
            20 to f("Categorias suplementares", "Categorias adicionais.", C.OTHER, P.NONE, T.IPTC_EDITORIAL),
            22 to f("Identificador fixo", "Identificador de pauta.", C.OTHER, P.LOW, T.IPTC_EDITORIAL),
            25 to f("Palavras-chave", "Palavras-chave descritivas.", C.DESCRIPTION, P.LOW, T.KEYWORDS),
            26 to f("Código do local do conteúdo", "Código do local retratado.", C.LOCATION, P.HIGH, T.LOCATION),
            27 to f("Local do conteúdo", "Nome do local retratado.", C.LOCATION, P.HIGH, T.LOCATION),
            30 to f("Data de liberação", "Data de liberação editorial.", C.DATES, P.LOW, T.IPTC_EDITORIAL),
            35 to f("Hora de liberação", "Hora de liberação editorial.", C.DATES, P.LOW, T.IPTC_EDITORIAL),
            37 to f("Data de expiração", "Data de expiração editorial.", C.DATES, P.LOW, T.IPTC_EDITORIAL),
            38 to f("Hora de expiração", "Hora de expiração editorial.", C.DATES, P.LOW, T.IPTC_EDITORIAL),
            40 to f("Instruções especiais", "Instruções editoriais; podem conter dados de contato.", C.OTHER, P.MEDIUM, T.IPTC_EDITORIAL),
            45 to f("Serviço de referência", "Serviço de referência.", C.OTHER, P.LOW, T.IPTC_EDITORIAL),
            47 to f("Data de referência", "Data de referência.", C.DATES, P.LOW, T.IPTC_EDITORIAL),
            50 to f("Número de referência", "Número de referência.", C.OTHER, P.LOW, T.IPTC_EDITORIAL),
            55 to f("Data de criação", "Data em que o conteúdo foi criado.", C.DATES, P.MEDIUM, T.DATES),
            60 to f("Hora de criação", "Hora em que o conteúdo foi criado.", C.DATES, P.MEDIUM, T.DATES),
            62 to f("Data de criação digital", "Data da digitalização.", C.DATES, P.MEDIUM, T.DATES),
            63 to f("Hora de criação digital", "Hora da digitalização.", C.DATES, P.MEDIUM, T.DATES),
            65 to f("Programa de origem", "Software que criou o registro.", C.SOFTWARE, P.LOW, T.SOFTWARE),
            70 to f("Versão do programa", "Versão do software.", C.SOFTWARE, P.LOW, T.SOFTWARE),
            75 to f("Ciclo do objeto", "Ciclo editorial (manhã/tarde).", C.OTHER, P.NONE, T.IPTC_EDITORIAL),
            80 to f("Autor (By-line)", "Nome do criador.", C.AUTHORSHIP, P.HIGH, T.AUTHOR_IDENTITY),
            85 to f("Cargo do autor", "Função do criador.", C.AUTHORSHIP, P.MEDIUM, T.AUTHOR_IDENTITY),
            90 to f("Cidade", "Cidade retratada ou da captura.", C.LOCATION, P.HIGH, T.LOCATION),
            92 to f("Sublocalidade", "Bairro ou local específico.", C.LOCATION, P.HIGH, T.LOCATION),
            95 to f("Estado/Província", "Estado ou província.", C.LOCATION, P.MEDIUM, T.LOCATION),
            100 to f("Código do país", "Código ISO do país.", C.LOCATION, P.MEDIUM, T.LOCATION),
            101 to f("País", "Nome do país.", C.LOCATION, P.MEDIUM, T.LOCATION),
            103 to f("Referência de transmissão", "Código de transmissão original.", C.OTHER, P.LOW, T.IPTC_EDITORIAL),
            105 to f("Manchete", "Resumo publicável do conteúdo.", C.DESCRIPTION, P.LOW, T.COMMENTS),
            110 to f("Crédito", "Provedor da imagem.", C.AUTHORSHIP, P.MEDIUM, T.IPTC_EDITORIAL),
            115 to f("Fonte", "Detentor original.", C.AUTHORSHIP, P.MEDIUM, T.IPTC_EDITORIAL),
            116 to f("Copyright", "Aviso de direitos autorais.", C.AUTHORSHIP, P.MEDIUM, T.COPYRIGHT),
            118 to f("Contato", "Dados de contato.", C.AUTHORSHIP, P.HIGH, T.AUTHOR_IDENTITY),
            120 to f("Legenda/descrição", "Descrição do conteúdo; pode citar pessoas e lugares.", C.DESCRIPTION, P.MEDIUM, T.COMMENTS),
            122 to f("Redator da legenda", "Pessoa que escreveu a legenda.", C.AUTHORSHIP, P.HIGH, T.AUTHOR_IDENTITY),
            130 to f("Tipo de imagem", "Tipo de imagem.", C.FILE, P.NONE, null),
            131 to f("Orientação IPTC", "Orientação (retrato/paisagem).", C.FILE, P.NONE, null),
            135 to f("Idioma", "Idioma do conteúdo.", C.OTHER, P.NONE, null),
        )

        fun info(ds: Int): FieldInfo = CATALOG[ds]
            ?: f("Dataset IPTC 2:%03d".format(ds), "Campo IPTC não catalogado. Mantido.", C.OTHER, P.LOW, null)
    }
}
