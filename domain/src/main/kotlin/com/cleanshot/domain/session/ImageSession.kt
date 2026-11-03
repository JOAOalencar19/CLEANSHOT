package com.cleanshot.domain.session

import com.cleanshot.domain.repository.WorkspaceRepository
import com.cleanshot.domain.usecase.AnalyzedImage
import com.cleanshot.domain.usecase.CleanedImage
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory registry of images the user is working on, so screens can share analysis and
 * cleaning results without serialising them. Private files are released when an entry is
 * discarded; nothing here is persisted.
 */
@Singleton
class ImageSession @Inject constructor(private val workspace: WorkspaceRepository) {

    data class Entry(val id: String, val analyzed: AnalyzedImage, val cleaned: CleanedImage? = null)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun put(analyzed: AnalyzedImage): String {
        val id = UUID.randomUUID().toString()
        entries[id] = Entry(id, analyzed)
        return id
    }

    fun get(id: String): Entry? = entries[id]

    fun attachCleaned(id: String, cleaned: CleanedImage) {
        val e = entries[id] ?: return
        e.cleaned?.let { workspace.release(it.output) }
        entries[id] = e.copy(cleaned = cleaned)
    }

    fun discard(id: String) {
        val e = entries.remove(id) ?: return
        workspace.release(e.analyzed.localCopy)
        e.cleaned?.let { workspace.release(it.output) }
    }
}
