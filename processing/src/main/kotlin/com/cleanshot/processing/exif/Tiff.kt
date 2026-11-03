package com.cleanshot.processing.exif

import com.cleanshot.processing.util.ByteSink
import com.cleanshot.processing.util.MalformedImageException
import com.cleanshot.processing.util.require
import com.cleanshot.processing.util.u16be
import com.cleanshot.processing.util.u16le
import com.cleanshot.processing.util.u32be
import com.cleanshot.processing.util.u32le
import com.cleanshot.processing.util.u8

/** Logical IFDs of an EXIF block. */
enum class IfdType(val label: String) { IFD0("IFD0"), EXIF("ExifIFD"), GPS("GPS"), INTEROP("Interop"), IFD1("IFD1") }

/**
 * A TIFF entry with its value bytes already resolved (independent of the original offsets),
 * so that the block can be re-serialised safely.
 */
class TiffEntry(
    val tag: Int,
    val type: Int,
    val count: Long,
    /** Value bytes in the block's byte order. */
    val value: ByteArray,
    /** Original offset of the value (for out-of-line values), used to keep MakerNote in place. */
    val originalOffset: Long,
)

class TiffBlock(
    val littleEndian: Boolean,
    val ifds: MutableMap<IfdType, MutableList<TiffEntry>>,
    /** Thumbnail bytes referenced by IFD1 (JPEGInterchangeFormat or a single strip). */
    var thumbnail: ByteArray?,
    /** Offset/length tags used by the thumbnail in IFD1. */
    val thumbnailTags: Pair<Int, Int>?,
) {
    fun entries(ifd: IfdType): List<TiffEntry> = ifds[ifd].orEmpty()
}

object TiffTags {
    const val EXIF_IFD_POINTER = 0x8769
    const val GPS_IFD_POINTER = 0x8825
    const val INTEROP_IFD_POINTER = 0xA005
    const val JPEG_IF_OFFSET = 0x0201
    const val JPEG_IF_LENGTH = 0x0202
    const val STRIP_OFFSETS = 0x0111
    const val STRIP_BYTE_COUNTS = 0x0117
    const val MAKER_NOTE = 0x927C
    const val SUB_IFDS = 0x014A
    val POINTERS = setOf(EXIF_IFD_POINTER, GPS_IFD_POINTER, INTEROP_IFD_POINTER)

    val TYPE_SIZES = intArrayOf(0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8, 4)
    fun typeSize(type: Int): Int = if (type in 1..13) TYPE_SIZES[type] else 0
}

/** Parses a TIFF/EXIF block (starting at the "II*\0"/"MM\0*" header). */
object TiffParser {

    fun parse(data: ByteArray): TiffBlock {
        require(data.size >= 8) { "Bloco EXIF truncado" }
        val le = when {
            data[0] == 'I'.code.toByte() && data[1] == 'I'.code.toByte() -> true
            data[0] == 'M'.code.toByte() && data[1] == 'M'.code.toByte() -> false
            else -> throw MalformedImageException("Cabeçalho TIFF inválido")
        }
        val r = Reader(data, le)
        require(r.u16(2) == 42) { "Assinatura TIFF inválida" }
        val ifds = linkedMapOf<IfdType, MutableList<TiffEntry>>()
        val visited = HashSet<Long>()

        val ifd0Offset = r.u32(4)
        val (ifd0, ifd1Offset) = readIfd(r, ifd0Offset, visited)
        ifds[IfdType.IFD0] = ifd0

        fun follow(parent: List<TiffEntry>, pointerTag: Int, type: IfdType) {
            val p = parent.firstOrNull { it.tag == pointerTag } ?: return
            val off = r.valueAsLong(p)
            if (off <= 0 || off >= data.size) return
            ifds[type] = readIfd(r, off, visited).first
        }
        follow(ifd0, TiffTags.EXIF_IFD_POINTER, IfdType.EXIF)
        follow(ifd0, TiffTags.GPS_IFD_POINTER, IfdType.GPS)
        ifds[IfdType.EXIF]?.let { follow(it, TiffTags.INTEROP_IFD_POINTER, IfdType.INTEROP) }

        var thumb: ByteArray? = null
        var thumbTags: Pair<Int, Int>? = null
        if (ifd1Offset in 8 until data.size) {
            val ifd1 = readIfd(r, ifd1Offset, visited).first
            ifds[IfdType.IFD1] = ifd1
            val pair = when {
                ifd1.any { it.tag == TiffTags.JPEG_IF_OFFSET } -> TiffTags.JPEG_IF_OFFSET to TiffTags.JPEG_IF_LENGTH
                ifd1.any { it.tag == TiffTags.STRIP_OFFSETS } -> TiffTags.STRIP_OFFSETS to TiffTags.STRIP_BYTE_COUNTS
                else -> null
            }
            if (pair != null) {
                val offE = ifd1.first { it.tag == pair.first }
                val lenE = ifd1.firstOrNull { it.tag == pair.second }
                if (offE.count != 1L || (lenE != null && lenE.count != 1L)) {
                    throw MalformedImageException("Miniatura EXIF em múltiplas faixas não é suportada")
                }
                if (lenE != null) {
                    val off = r.valueAsLong(offE).toInt()
                    val len = r.valueAsLong(lenE).toInt()
                    if (off > 0 && len > 0 && off.toLong() + len <= data.size) {
                        thumb = data.copyOfRange(off, off + len)
                        thumbTags = pair
                    }
                }
            }
        }
        return TiffBlock(le, ifds, thumb, thumbTags)
    }

    private fun readIfd(r: Reader, offset: Long, visited: MutableSet<Long>): Pair<MutableList<TiffEntry>, Long> {
        require(offset >= 8 && offset + 2 <= r.data.size) { "Offset de IFD inválido" }
        require(visited.add(offset)) { "IFD circular" }
        val o = offset.toInt()
        val n = r.u16(o)
        require(o + 2 + n * 12 + 4 <= r.data.size || o + 2 + n * 12 <= r.data.size) { "IFD truncado" }
        val list = ArrayList<TiffEntry>(n)
        for (i in 0 until n) {
            val e = o + 2 + i * 12
            val tag = r.u16(e)
            val type = r.u16(e + 2)
            val count = r.u32(e + 4)
            val unit = TiffTags.typeSize(type)
            if (unit == 0) continue // unknown type: cannot be relocated safely -> dropped below
            val total = unit.toLong() * count
            require(total in 0..Int.MAX_VALUE.toLong()) { "Tamanho de tag inválido" }
            val (bytes, origOff) = if (total <= 4) {
                r.data.copyOfRange(e + 8, e + 8 + total.toInt()) to -1L
            } else {
                val vo = r.u32(e + 8)
                require(vo + total <= r.data.size) { "Valor de tag fora do bloco" }
                r.data.copyOfRange(vo.toInt(), (vo + total).toInt()) to vo
            }
            list += TiffEntry(tag, type, count, bytes, origOff)
        }
        val next = if (o + 2 + n * 12 + 4 <= r.data.size) r.u32(o + 2 + n * 12) else 0L
        return list to next
    }

    internal class Reader(val data: ByteArray, val le: Boolean) {
        fun u16(i: Int): Int = if (le) data.u16le(i) else data.u16be(i)
        fun u32(i: Int): Long = if (le) data.u32le(i) else data.u32be(i)
        fun valueAsLong(e: TiffEntry): Long = when (e.type) {
            3, 8 -> if (le) e.value.u16le(0).toLong() else e.value.u16be(0).toLong()
            4, 9, 13 -> if (le) e.value.u32le(0) else e.value.u32be(0)
            1, 6, 7 -> e.value.u8(0).toLong()
            else -> 0L
        }
    }
}

/**
 * Serialises a [TiffBlock]. Pointer tags and thumbnail offsets are recomputed. The MakerNote
 * value is kept at its original offset whenever the new layout allows (padding with zeros),
 * because many manufacturers store offsets relative to the TIFF header inside it.
 */
object TiffWriter {

    data class Result(val bytes: ByteArray, val makerNoteRelocated: Boolean)

    fun write(block: TiffBlock): Result {
        val le = block.littleEndian
        val ifds = block.ifds
        // Drop pointer entries whose IFD is missing/empty; add for present ones.
        val ifd0 = ifds[IfdType.IFD0].orEmpty().toMutableList()
        val exif = ifds[IfdType.EXIF].orEmpty().toMutableList()
        val gps = ifds[IfdType.GPS].orEmpty().toMutableList()
        val interop = ifds[IfdType.INTEROP].orEmpty().toMutableList()
        val ifd1 = ifds[IfdType.IFD1].orEmpty().toMutableList()

        val hasInterop = interop.isNotEmpty()
        val hasGps = gps.isNotEmpty()
        exif.removeAll { it.tag == TiffTags.INTEROP_IFD_POINTER }
        if (hasInterop) exif += placeholder(TiffTags.INTEROP_IFD_POINTER)
        val hasExif = exif.isNotEmpty()
        ifd0.removeAll { it.tag in TiffTags.POINTERS }
        if (hasExif) ifd0 += placeholder(TiffTags.EXIF_IFD_POINTER)
        if (hasGps) ifd0 += placeholder(TiffTags.GPS_IFD_POINTER)

        val thumb = block.thumbnail
        val thumbTags = block.thumbnailTags
        val writeIfd1 = ifd1.isNotEmpty() && !(thumbTags != null && thumb == null)

        val out = Out(le)
        out.raw(if (le) byteArrayOf(0x49, 0x49) else byteArrayOf(0x4D, 0x4D))
        out.u16(42)
        out.u32(8)

        var relocated = false
        val pending = mutableMapOf<Int, Int>() // pointer tag -> entry value position
        var ifd1PointerPos = -1
        var thumbOffsetPos = -1

        fun writeIfd(entries: List<TiffEntry>, isIfd0: Boolean, isIfd1: Boolean) {
            val sorted = entries.sortedBy { it.tag }
            val start = out.size
            out.u16(sorted.size)
            val valuePositions = IntArray(sorted.size)
            for ((i, e) in sorted.withIndex()) {
                out.u16(e.tag); out.u16(e.type); out.u32(e.count)
                valuePositions[i] = out.size
                out.u32(0)
            }
            val nextPos = out.size
            out.u32(0)
            if (isIfd0) ifd1PointerPos = nextPos
            // Out-of-line values keep their original relative order, so (with entries only ever
            // removed) every value lands at or before its original offset and the MakerNote can
            // be pinned to its original position.
            val order = sorted.indices.sortedBy { sorted[it].originalOffset.let { o -> if (o < 0) Long.MAX_VALUE else o } }
            for (i in order) {
                val e = sorted[i]
                when {
                    e.tag in TiffTags.POINTERS -> pending[e.tag] = valuePositions[i]
                    isIfd1 && thumbTags != null && e.tag == thumbTags.first -> thumbOffsetPos = valuePositions[i]
                    isIfd1 && thumbTags != null && e.tag == thumbTags.second -> out.patchValue(valuePositions[i], e.type, thumb?.size?.toLong() ?: 0)
                    e.value.size <= 4 -> out.patchInline(valuePositions[i], e.value)
                    else -> {
                        if (e.tag == TiffTags.MAKER_NOTE && e.originalOffset >= 0) {
                            if (out.size <= e.originalOffset) out.padTo(e.originalOffset.toInt()) else relocated = true
                        }
                        if (out.size % 2 == 1) out.raw(byteArrayOf(0))
                        val pos = out.size
                        out.raw(e.value)
                        out.patchU32(valuePositions[i], pos.toLong())
                    }
                }
            }
            if (out.size % 2 == 1) out.raw(byteArrayOf(0))
            check(start >= 8)
        }

        writeIfd(ifd0, isIfd0 = true, isIfd1 = false)
        if (hasExif) {
            out.patchU32(pending.getValue(TiffTags.EXIF_IFD_POINTER), out.size.toLong())
            writeIfd(exif, false, false)
            if (hasInterop) {
                out.patchU32(pending.getValue(TiffTags.INTEROP_IFD_POINTER), out.size.toLong())
                writeIfd(interop, false, false)
            }
        }
        if (hasGps) {
            out.patchU32(pending.getValue(TiffTags.GPS_IFD_POINTER), out.size.toLong())
            writeIfd(gps, false, false)
        }
        if (writeIfd1) {
            out.patchU32(ifd1PointerPos, out.size.toLong())
            writeIfd(ifd1, isIfd0 = false, isIfd1 = true)
            if (thumb != null && thumbOffsetPos >= 0) {
                out.patchU32(thumbOffsetPos, out.size.toLong())
                out.raw(thumb)
            }
        }
        return Result(out.bytes(), relocated)
    }

    private fun placeholder(tag: Int) = TiffEntry(tag, 4, 1, ByteArray(4), -1)

    private class Out(val le: Boolean) {
        private val sink = ByteSink(4096)
        private var patched: ByteArray? = null
        private val patches = mutableListOf<Pair<Int, ByteArray>>()
        val size: Int get() = sink.size
        fun raw(b: ByteArray) = sink.write(b)
        fun u16(v: Int) = if (le) { sink.u8(v); sink.u8(v shr 8) } else sink.u16be(v)
        fun u32(v: Long) = if (le) sink.u32le(v) else sink.u32be(v)
        fun padTo(pos: Int) { while (sink.size < pos) sink.u8(0) }
        fun patchU32(pos: Int, v: Long) {
            val b = if (le) byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
            else byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
            patches += pos to b
        }
        fun patchValue(pos: Int, type: Int, v: Long) {
            if (type == 3) {
                val b = if (le) byteArrayOf(v.toByte(), (v shr 8).toByte(), 0, 0) else byteArrayOf((v shr 8).toByte(), v.toByte(), 0, 0)
                patches += pos to b
            } else patchU32(pos, v)
        }
        fun patchInline(pos: Int, value: ByteArray) { patches += pos to value.copyOf(4) }
        fun bytes(): ByteArray {
            val b = sink.toByteArray()
            for ((pos, v) in patches) System.arraycopy(v, 0, b, pos, v.size)
            patched = b
            return b
        }
    }
}
