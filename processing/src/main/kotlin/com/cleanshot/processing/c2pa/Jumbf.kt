package com.cleanshot.processing.c2pa

import com.cleanshot.processing.util.MalformedImageException
import com.cleanshot.processing.util.u32be
import com.cleanshot.processing.util.u64be
import com.cleanshot.processing.util.toHex

/** A parsed JUMBF box (ISO/IEC 19566-5). */
class JumbfBox(
    val type: String,
    /** Full box bytes including the header. */
    val raw: ByteArray,
    val headerSize: Int,
    val label: String? = null,
    val typeUuid: String? = null,
    val children: List<JumbfBox> = emptyList(),
) {
    val payload: ByteArray get() = raw.copyOfRange(headerSize, raw.size)
    val isSuperbox: Boolean get() = type == "jumb"

    fun child(label: String): JumbfBox? = children.firstOrNull { it.label == label }
    fun childStartingWith(prefix: String): JumbfBox? = children.firstOrNull { it.label?.startsWith(prefix) == true }
    fun contentBoxes(): List<JumbfBox> = children.filter { !it.isSuperbox && it.type != "jumd" }
    fun firstContent(type: String): JumbfBox? = contentBoxes().firstOrNull { it.type == type }

    /** Bytes of the superbox content (everything after the superbox header). */
    val superboxContent: ByteArray get() = raw.copyOfRange(headerSize, raw.size)
}

object Jumbf {
    const val UUID_STORE = "6332706100110010800000aa00389b71"
    const val UUID_MANIFEST = "63326d6100110010800000aa00389b71"
    const val UUID_UPDATE_MANIFEST = "6332756d00110010800000aa00389b71"
    const val UUID_ASSERTIONS = "6332617300110010800000aa00389b71"
    const val UUID_CLAIM = "6332636c00110010800000aa00389b71"
    const val UUID_SIGNATURE = "6332637300110010800000aa00389b71"

    fun parseAll(data: ByteArray, from: Int = 0, to: Int = data.size, depth: Int = 0): List<JumbfBox> {
        if (depth > 16) throw MalformedImageException("JUMBF aninhado demais")
        val out = mutableListOf<JumbfBox>()
        var p = from
        while (p + 8 <= to) {
            var size = data.u32be(p)
            val type = String(data, p + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) { size = data.u64be(p + 8); header = 16 } else if (size == 0L) size = (to - p).toLong()
            if (size < header || p + size > to) throw MalformedImageException("Box JUMBF '$type' truncado")
            val raw = data.copyOfRange(p, (p + size).toInt())
            out += if (type == "jumb") parseSuperbox(raw, header, depth) else JumbfBox(type, raw, header)
            p += size.toInt()
        }
        return out
    }

    private fun parseSuperbox(raw: ByteArray, header: Int, depth: Int): JumbfBox {
        val kids = parseAll(raw, header, raw.size, depth + 1)
        val jumd = kids.firstOrNull() ?: throw MalformedImageException("Superbox JUMBF vazio")
        if (jumd.type != "jumd") throw MalformedImageException("Superbox JUMBF sem descrição")
        val d = jumd.payload
        if (d.size < 17) throw MalformedImageException("Descrição JUMBF truncada")
        val uuid = d.copyOfRange(0, 16).toHex()
        val toggles = d[16].toInt() and 0xFF
        var label: String? = null
        if (toggles and 0x02 != 0) {
            var e = 17
            while (e < d.size && d[e] != 0.toByte()) e++
            label = String(d, 17, e - 17, Charsets.UTF_8)
        }
        return JumbfBox("jumb", raw, header, label, uuid, kids)
    }
}
