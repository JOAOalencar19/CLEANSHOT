package com.cleanshot.data.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.cleanshot.data.workspace.PrivateWorkspace
import com.cleanshot.domain.repository.ExportManager
import com.cleanshot.domain.repository.ExportResult
import com.cleanshot.domain.util.OutputNaming
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Saves processed copies to user-chosen destinations (SAF) or prepares them for sharing. */
@Singleton
class AndroidExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workspace: PrivateWorkspace,
) : ExportManager {

    private val resolver get() = context.contentResolver

    override suspend fun saveTo(processedFilePath: String, destinationUri: String, onProgress: (Float) -> Unit): ExportResult =
        withContext(Dispatchers.IO) {
            val src = File(processedFilePath)
            val dest = Uri.parse(destinationUri)
            val out = openOutput(dest) ?: throw IOException("Não foi possível gravar no destino escolhido")
            out.use { copy(src, it, onProgress) }
            ExportResult(destinationUri, queryName(dest) ?: src.name, src.length())
        }

    override suspend fun saveToFolder(processedFilePath: String, treeUri: String, displayName: String, mimeType: String): ExportResult =
        withContext(Dispatchers.IO) {
            val tree = Uri.parse(treeUri)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val doc = DocumentsContract.createDocument(resolver, parent, mimeType, displayName)
                ?: throw IOException("Não foi possível criar o arquivo na pasta escolhida")
            try {
                val out = openOutput(doc) ?: throw IOException("Não foi possível gravar na pasta escolhida")
                out.use { copy(File(processedFilePath), it) {} }
            } catch (e: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, doc) }
                throw e
            }
            ExportResult(doc.toString(), queryName(doc) ?: displayName, File(processedFilePath).length())
        }

    override suspend fun prepareShare(processedFilePath: String, displayName: String): String = withContext(Dispatchers.IO) {
        val dir = File(workspace.shareDir, UUID.randomUUID().toString()).apply { mkdirs() }
        val target = File(dir, OutputNaming.sanitize(displayName))
        File(processedFilePath).copyTo(target, overwrite = true)
        FileProvider.getUriForFile(context, "${context.packageName}.files", target).toString()
    }

    private fun openOutput(uri: Uri): OutputStream? =
        runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull() ?: resolver.openOutputStream(uri, "w")

    private suspend fun copy(src: File, out: OutputStream, onProgress: (Float) -> Unit) {
        val total = src.length().coerceAtLeast(1)
        var done = 0L
        src.inputStream().use { input ->
            val buf = ByteArray(128 * 1024)
            while (true) {
                kotlin.coroutines.coroutineContext.ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                done += n
                onProgress(done.toFloat() / total)
            }
            out.flush()
        }
    }

    private fun queryName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
