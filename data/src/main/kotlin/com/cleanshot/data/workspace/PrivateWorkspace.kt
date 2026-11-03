package com.cleanshot.data.workspace

import android.content.Context
import android.net.Uri
import com.cleanshot.domain.model.ImageRef
import com.cleanshot.domain.repository.WorkspaceRepository
import com.cleanshot.processing.analyzer.DefaultMetadataAnalyzer
import com.cleanshot.domain.util.OutputNaming
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Private working area inside the app sandbox (`noBackupFilesDir/work`).
 * - originals are opened read-only through the ContentResolver;
 * - every file lives in its own random directory;
 * - files are deleted after use and the whole area is wiped at start-up.
 * The share area (`cacheDir/shared`) only holds copies the user explicitly shares.
 */
@Singleton
class PrivateWorkspace @Inject constructor(@ApplicationContext private val context: Context) : WorkspaceRepository {

    private val root: File get() = File(context.noBackupFilesDir, "work").apply { mkdirs() }
    val shareDir: File get() = File(context.cacheDir, "shared").apply { mkdirs() }

    override suspend fun importCopy(ref: ImageRef): File = withContext(Dispatchers.IO) {
        if (ref.sizeBytes > DefaultMetadataAnalyzer.MAX_BYTES) throw IOException("Arquivo grande demais para processamento local seguro")
        val dir = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        val target = File(dir, OutputNaming.sanitize(ref.displayName))
        try {
            val input = context.contentResolver.openInputStream(Uri.parse(ref.uri)) ?: throw IOException("Não foi possível abrir o arquivo")
            input.use { inp ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > DefaultMetadataAnalyzer.MAX_BYTES) throw IOException("Arquivo grande demais para processamento local seguro")
                        out.write(buf, 0, n)
                    }
                }
            }
            target
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    override fun newOutputFile(displayName: String): File {
        val dir = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        return File(dir, OutputNaming.sanitize(displayName))
    }

    override fun release(file: File) {
        val parent = file.parentFile
        file.delete()
        if (parent != null && parent.parentFile == root) parent.deleteRecursively()
    }

    /**
     * Start-up sweep: deletes leftovers older than [maxAgeMillis] (e.g. after a crash) without
     * touching files a batch worker may have just created in this process.
     */
    suspend fun clearStale(maxAgeMillis: Long = 10 * 60 * 1000L) = withContext(Dispatchers.IO) {
        val limit = System.currentTimeMillis() - maxAgeMillis
        root.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.deleteRecursively() }
        shareDir.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.deleteRecursively() }
        Unit
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        root.listFiles()?.forEach { it.deleteRecursively() }
        shareDir.listFiles()?.forEach { it.deleteRecursively() }
        Unit
    }
}
