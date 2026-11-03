package com.cleanshot.data.batch

import android.content.Context
import com.cleanshot.domain.model.CleaningTarget
import com.cleanshot.domain.model.ImageRef
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists the batch request (URIs and options only; no metadata) so a worker restarted by the
 * system can resume, skipping items already done.
 */
internal object BatchRequestStore {

    data class Request(val items: List<ImageRef>, val targets: Set<CleaningTarget>, val treeUri: String, val done: Set<Int>)

    private fun file(context: Context) = File(context.noBackupFilesDir, "batch/request.json")

    fun write(context: Context, r: Request) {
        val f = file(context)
        f.parentFile?.mkdirs()
        val json = JSONObject()
            .put("treeUri", r.treeUri)
            .put("targets", JSONArray(r.targets.map { it.name }))
            .put("done", JSONArray(r.done.toList()))
            .put("items", JSONArray(r.items.map { JSONObject().put("uri", it.uri).put("name", it.displayName).put("mime", it.mimeType ?: "").put("size", it.sizeBytes) }))
        val tmp = File(f.parentFile, "request.json.tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(f)
    }

    fun read(context: Context): Request? = runCatching {
        val j = JSONObject(file(context).readText())
        val items = j.getJSONArray("items").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            .map { ImageRef(it.getString("uri"), it.getString("name"), it.optString("mime").ifBlank { null }, it.optLong("size", -1)) }
        val targets = j.getJSONArray("targets").let { a -> (0 until a.length()).mapNotNull { runCatching { CleaningTarget.valueOf(a.getString(it)) }.getOrNull() } }.toSet()
        val done = j.optJSONArray("done")?.let { a -> (0 until a.length()).map { a.getInt(it) } }?.toSet() ?: emptySet()
        Request(items, targets, j.getString("treeUri"), done)
    }.getOrNull()

    fun markDone(context: Context, index: Int) {
        val r = read(context) ?: return
        write(context, r.copy(done = r.done + index))
    }

    fun delete(context: Context) { file(context).delete() }
}
