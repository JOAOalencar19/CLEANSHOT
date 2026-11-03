package com.cleanshot.processing.container

import com.cleanshot.processing.util.MalformedImageException
import com.cleanshot.processing.util.ascii
import com.cleanshot.processing.util.u16be
import com.cleanshot.processing.util.u32be
import com.cleanshot.processing.util.u64be
import com.cleanshot.processing.util.u8

/**
 * Minimal ISO Base Media File Format reader for HEIF/AVIF still images.
 *
 * CleanShot never restructures BMFF files: metadata items (Exif / XMP) are rewritten *in place*
 * with exactly the same length, so `iloc` offsets, `mdat` and every other byte stay valid.
 */
class BmffFile private constructor(val data: ByteArray, val topLevel: List<Box>) {

    class Box(val type: String, val start: Int, val headerSize: Int, val size: Int, val userType: ByteArray? = null) {
        val contentStart: Int get() = start + headerSize
        val end: Int get() = start + size
    }

    /** Absolute byte range of one extent. */
    data class Extent(val offset: Int, val length: Int)

    data class Item(val id: Int, val type: String, val name: String, val contentType: String?, val contentEncoding: String?, val extents: List<Extent>, val constructionMethod: Int)

    val brands: List<String> by lazy {
        val ftyp = topLevel.firstOrNull { it.type == "ftyp" } ?: return@lazy emptyList()
        buildList {
            add(data.ascii(ftyp.contentStart, 4))
            var p = ftyp.contentStart + 8
            while (p + 4 <= ftyp.end) { add(data.ascii(p, 4)); p += 4 }
        }
    }

    private val meta: Box? get() = topLevel.firstOrNull { it.type == "meta" }

    private fun metaChildren(): List<Box> = meta?.let { children(it.contentStart + 4, it.end) } ?: emptyList()

    val primaryItemId: Int? by lazy {
        metaChildren().firstOrNull { it.type == "pitm" }?.let { b ->
            val v = data.u8(b.contentStart)
            if (v == 0) data.u16be(b.contentStart + 4) else data.u32be(b.contentStart + 4).toInt()
        }
    }

    val items: List<Item> by lazy { readItems() }

    fun exifItems() = items.filter { it.type == "Exif" }
    fun xmpItems() = items.filter { it.type == "mime" && it.contentType?.lowercase()?.contains("rdf+xml") == true }

    fun itemBytes(item: Item): ByteArray {
        val total = item.extents.sumOf { it.length }
        val out = ByteArray(total)
        var q = 0
        for (e in item.extents) { System.arraycopy(data, e.offset, out, q, e.length); q += e.length }
        return out
    }

    /** Writes [payload] (same total length) back into the item's extents of [target]. */
    fun writeItemInPlace(target: ByteArray, item: Item, payload: ByteArray) {
        val total = item.extents.sumOf { it.length }
        if (payload.size != total) throw MalformedImageException("Reescrita no lugar exige o mesmo tamanho")
        var q = 0
        for (e in item.extents) { System.arraycopy(payload, q, target, e.offset, e.length); q += e.length }
    }

    /** Dimensions of the primary item (ispe property). */
    fun primaryDimensions(): Pair<Int, Int>? {
        val iprp = metaChildren().firstOrNull { it.type == "iprp" } ?: return null
        val kids = children(iprp.contentStart, iprp.end)
        val ipco = kids.firstOrNull { it.type == "ipco" } ?: return null
        val props = children(ipco.contentStart, ipco.end)
        val ipma = kids.firstOrNull { it.type == "ipma" }
        val primary = primaryItemId
        fun ispe(b: Box) = if (b.type == "ispe") data.u32be(b.contentStart + 4).toInt() to data.u32be(b.contentStart + 8).toInt() else null
        if (ipma != null && primary != null) {
            val v = data.u8(ipma.contentStart)
            val flags = data.u32be(ipma.contentStart) and 0xFFFFFF
            var p = ipma.contentStart + 4
            val count = data.u32be(p).toInt(); p += 4
            repeat(count) {
                val id = if (v < 1) data.u16be(p).also { p += 2 } else data.u32be(p).toInt().also { p += 4 }
                val n = data.u8(p); p += 1
                repeat(n) {
                    val idx = if (flags and 1L == 1L) (data.u16be(p) and 0x7FFF).also { p += 2 } else (data.u8(p) and 0x7F).also { p += 1 }
                    if (id == primary && idx in 1..props.size) ispe(props[idx - 1])?.let { return it }
                }
            }
        }
        return props.mapNotNull { ispe(it) }.maxByOrNull { it.first.toLong() * it.second }
    }

    /** True when an auxiliary alpha image is declared (auxC URN for AVIF or HEVC). */
    fun hasAlphaItem(): Boolean {
        val head = String(data, 0, minOf(data.size, 256 * 1024), Charsets.ISO_8859_1)
        return head.contains("urn:mpeg:mpegB:cicp:systems:auxiliary:alpha") || head.contains("urn:mpeg:hevc:2015:auxid:1")
    }

    fun children(from: Int, to: Int): List<Box> = parseBoxes(data, from, to)

    private fun readItems(): List<Item> {
        val kids = metaChildren()
        val iinf = kids.firstOrNull { it.type == "iinf" } ?: return emptyList()
        val iloc = kids.firstOrNull { it.type == "iloc" } ?: return emptyList()
        val idat = kids.firstOrNull { it.type == "idat" }

        // iinf
        val infos = mutableMapOf<Int, Triple<String, String, Pair<String?, String?>>>()
        run {
            val v = data.u8(iinf.contentStart)
            var p = iinf.contentStart + 4
            p += if (v == 0) 2 else 4
            for (infe in children(p, iinf.end).filter { it.type == "infe" }) {
                val iv = data.u8(infe.contentStart)
                if (iv < 2) continue
                var q = infe.contentStart + 4
                val id = if (iv == 2) data.u16be(q).also { q += 2 } else data.u32be(q).toInt().also { q += 4 }
                q += 2 // protection index
                val type = data.ascii(q, 4); q += 4
                val (name, q1) = cstring(q, infe.end); q = q1
                var ct: String? = null; var ce: String? = null
                if (type == "mime") {
                    val (c, q2) = cstring(q, infe.end); ct = c; q = q2
                    if (q < infe.end) ce = cstring(q, infe.end).first
                }
                infos[id] = Triple(type, name, ct to ce)
            }
        }

        // iloc
        val locs = mutableMapOf<Int, Pair<Int, List<Extent>>>()
        run {
            val v = data.u8(iloc.contentStart)
            var p = iloc.contentStart + 4
            val sizes = data.u16be(p); p += 2
            val offSize = sizes shr 12 and 0xF
            val lenSize = sizes shr 8 and 0xF
            val baseSize = sizes shr 4 and 0xF
            val idxSize = if (v == 1 || v == 2) sizes and 0xF else 0
            val count = if (v < 2) data.u16be(p).also { p += 2 } else data.u32be(p).toInt().also { p += 4 }
            fun readN(n: Int): Long = when (n) {
                0 -> 0L
                4 -> data.u32be(p).also { p += 4 }
                8 -> data.u64be(p).also { p += 8 }
                else -> throw MalformedImageException("Tamanho de campo iloc não suportado")
            }
            repeat(count) {
                val id = if (v < 2) data.u16be(p).also { p += 2 } else data.u32be(p).toInt().also { p += 4 }
                val method = if (v == 1 || v == 2) (data.u16be(p) and 0xF).also { p += 2 } else 0
                val dataRef = data.u16be(p); p += 2
                val base = readN(baseSize)
                val extentCount = data.u16be(p); p += 2
                val extents = mutableListOf<Extent>()
                repeat(extentCount) {
                    if (idxSize > 0) readN(idxSize)
                    val off = readN(offSize)
                    val len = readN(lenSize)
                    val abs = when (method) {
                        0 -> base + off
                        1 -> (idat?.contentStart?.toLong() ?: throw MalformedImageException("iloc referencia idat inexistente")) + base + off
                        else -> -1L
                    }
                    extents += Extent(abs.toInt(), len.toInt())
                }
                if (dataRef != 0) { locs[id] = -1 to emptyList(); return@repeat }
                locs[id] = method to extents
            }
        }

        return infos.mapNotNull { (id, info) ->
            val (method, extents) = locs[id] ?: return@mapNotNull null
            if (method !in 0..1 || extents.any { it.offset < 0 || it.length < 0 || it.offset.toLong() + it.length > data.size }) {
                return@mapNotNull Item(id, info.first, info.second, info.third.first, info.third.second, emptyList(), -1)
            }
            // A zero-length final extent means "to the end of the file".
            val fixed = extents.map { if (it.length == 0) Extent(it.offset, data.size - it.offset) else it }
            Item(id, info.first, info.second, info.third.first, info.third.second, fixed, method)
        }
    }

    private fun cstring(from: Int, end: Int): Pair<String, Int> {
        var q = from
        while (q < end && data[q] != 0.toByte()) q++
        return String(data, from, q - from, Charsets.UTF_8) to (q + 1)
    }

    companion object {
        /** UUID of the C2PA manifest box ("d8fec3d6-1b0e-483c-9297-5828877ec481"). */
        val C2PA_UUID = byteArrayOf(
            0xD8.toByte(), 0xFE.toByte(), 0xC3.toByte(), 0xD6.toByte(), 0x1B, 0x0E, 0x48, 0x3C,
            0x92.toByte(), 0x97.toByte(), 0x58, 0x28, 0x87.toByte(), 0x7E, 0xC4.toByte(), 0x81.toByte(),
        )

        fun parse(data: ByteArray): BmffFile {
            val boxes = parseBoxes(data, 0, data.size)
            if (boxes.firstOrNull()?.type != "ftyp") throw MalformedImageException("Arquivo ISOBMFF sem ftyp")
            return BmffFile(data, boxes)
        }

        fun parseBoxes(data: ByteArray, from: Int, to: Int): List<Box> {
            val out = mutableListOf<Box>()
            var p = from
            while (p + 8 <= to) {
                var size = data.u32be(p)
                val type = data.ascii(p + 4, 4)
                var header = 8
                if (size == 1L) {
                    if (p + 16 > to) throw MalformedImageException("Box ISOBMFF truncado")
                    size = data.u64be(p + 8); header = 16
                } else if (size == 0L) size = (to - p).toLong()
                if (size < header || p + size > to) throw MalformedImageException("Box ISOBMFF '$type' truncado")
                var user: ByteArray? = null
                if (type == "uuid") {
                    if (p + header + 16 > to) throw MalformedImageException("Box uuid truncado")
                    user = data.copyOfRange(p + header, p + header + 16); header += 16
                }
                out += Box(type, p, header, size.toInt(), user)
                p += size.toInt()
            }
            return out
        }
    }
}
