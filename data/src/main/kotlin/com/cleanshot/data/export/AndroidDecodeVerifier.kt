package com.cleanshot.data.export

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import com.cleanshot.domain.repository.DecodeVerifier
import java.io.File
import javax.inject.Inject

/**
 * Decodes both files with the platform decoder and compares dimensions. When the platform
 * cannot decode the format at all (e.g. AVIF before Android 12), the check is skipped rather
 * than failing, because the structural verification has already proved byte-level integrity.
 */
class AndroidDecodeVerifier @Inject constructor() : DecodeVerifier {

    override fun verify(original: File, processed: File): String? {
        val a = bounds(original) ?: return null
        val b = bounds(processed) ?: return "a cópia processada não pôde ser decodificada"
        if (a != b) return "dimensões diferentes (${a.first}×${a.second} → ${b.first}×${b.second})"
        if (Build.VERSION.SDK_INT >= 28) {
            val ok = runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(processed)) { decoder, info, _ ->
                    decoder.setTargetSampleSize(maxOf(1, maxOf(info.size.width, info.size.height) / 256))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }.recycle()
            }.isSuccess
            val originalOk = runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(original)) { decoder, info, _ ->
                    decoder.setTargetSampleSize(maxOf(1, maxOf(info.size.width, info.size.height) / 256))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }.recycle()
            }.isSuccess
            if (originalOk && !ok) return "o decodificador do sistema rejeitou a cópia processada"
        }
        return null
    }

    private fun bounds(f: File): Pair<Int, Int>? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
    }
}
