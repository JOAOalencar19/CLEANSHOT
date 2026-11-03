package com.cleanshot.data.importer

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import com.cleanshot.domain.engine.ImageFormatDetector
import com.cleanshot.domain.model.ImageFormat
import com.cleanshot.domain.model.ImageRef
import com.cleanshot.domain.model.ImportedImage
import com.cleanshot.domain.repository.ImageImportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Describes user-selected images through the ContentResolver (read-only). No broad storage
 * permission is needed: access comes from the Photo Picker / SAF grants.
 */
@Singleton
class AndroidImageImportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detector: ImageFormatDetector,
) : ImageImportRepository {

    private val resolver get() = context.contentResolver

    override suspend fun describe(uri: String): ImportedImage = withContext(Dispatchers.IO) {
        val u = Uri.parse(uri)
        var name: String? = null
        var size = -1L
        resolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        val mime = resolver.getType(u)
        val header = ByteArray(64)
        val read = resolver.openInputStream(u)?.use { it.read(header) } ?: throw IOException("Não foi possível abrir o arquivo")
        val format = detector.detect(header.copyOf(maxOf(read, 0)), name)
        if (size < 0) size = resolver.openAssetFileDescriptor(u, "r")?.use { it.length } ?: -1L
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { resolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, opts) } }
        val gps = if (format == ImageFormat.JPEG || format == ImageFormat.PNG || format == ImageFormat.WEBP || format == ImageFormat.HEIF) {
            runCatching { resolver.openInputStream(u)?.use { ExifInterface(it).latLong != null } }.getOrNull()
        } else null
        ImportedImage(
            ref = ImageRef(uri, name ?: "imagem", mime, size),
            format = format,
            width = opts.outWidth.takeIf { it > 0 },
            height = opts.outHeight.takeIf { it > 0 },
            hasLocationHint = gps,
        )
    }

    override suspend fun listTree(treeUri: String, recursive: Boolean): List<ImageRef> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return@withContext emptyList()
        val out = mutableListOf<ImageRef>()
        fun walk(dir: DocumentFile, depth: Int) {
            for (f in dir.listFiles()) {
                when {
                    f.isDirectory && recursive && depth < 8 -> walk(f, depth + 1)
                    f.isFile && (f.type?.startsWith("image/") == true) ->
                        out += ImageRef(f.uri.toString(), f.name ?: "imagem", f.type, f.length())
                }
                if (out.size >= 500) return
            }
        }
        walk(root, 0)
        out
    }
}
