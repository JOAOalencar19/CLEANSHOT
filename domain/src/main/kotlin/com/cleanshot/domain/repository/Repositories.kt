package com.cleanshot.domain.repository

import com.cleanshot.domain.model.BatchProgress
import com.cleanshot.domain.model.CleaningOptions
import com.cleanshot.domain.model.HistoryRecord
import com.cleanshot.domain.model.HistoryStats
import com.cleanshot.domain.model.ImageRef
import com.cleanshot.domain.model.ImportedImage
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeRecent(limit: Int = 50): Flow<List<HistoryRecord>>
    fun observeStats(): Flow<HistoryStats>
    suspend fun add(record: HistoryRecord)
    suspend fun delete(id: Long)
    suspend fun clear()
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultOptions: CleaningOptions = CleaningOptions.DEFAULT,
    val fileNameSuffix: String = "_clean",
    val keepHistory: Boolean = true,
)

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDefaultOptions(options: CleaningOptions)
    suspend fun setFileNameSuffix(suffix: String)
    suspend fun setKeepHistory(keep: Boolean)
}

/** Reads basic information about user-selected images without copying or modifying them. */
interface ImageImportRepository {
    suspend fun describe(uri: String): ImportedImage
    /** Lists images inside a SAF tree (folder), recursively. */
    suspend fun listTree(treeUri: String, recursive: Boolean = true): List<ImageRef>
}

/** Result of a finished export. */
data class ExportResult(val uri: String, val displayName: String, val sizeBytes: Long)

/** Writes processed copies to user-chosen locations or prepares them for sharing. */
interface ExportManager {
    suspend fun saveTo(processedFilePath: String, destinationUri: String, onProgress: (Float) -> Unit = {}): ExportResult
    suspend fun prepareShare(processedFilePath: String, displayName: String): String
    /** Creates a new document inside a user-granted folder (SAF tree) and writes the file. */
    suspend fun saveToFolder(processedFilePath: String, treeUri: String, displayName: String, mimeType: String): ExportResult
}

/**
 * Confirms with the platform decoder that a processed copy still decodes to the same
 * dimensions as the original (an extra check on top of the structural verification).
 */
interface DecodeVerifier {
    /** Returns null when OK, or a human-readable reason. */
    fun verify(original: java.io.File, processed: java.io.File): String?
}

/** Background batch processing queue (WorkManager on Android). */
interface ProcessingQueue {
    val progress: Flow<BatchProgress>
    fun enqueue(images: List<ImageRef>, options: CleaningOptions, destinationTreeUri: String)
    fun cancel()
    fun clearFinished()
}

/**
 * Private working area. Every operation works on a private copy of the user's file, which is
 * deleted afterwards; the user's original is opened read-only and never modified.
 */
interface WorkspaceRepository {
    /** Copies the selected image to private storage (read-only access to the original). */
    suspend fun importCopy(ref: ImageRef): java.io.File
    /** Creates an empty private file for a processed copy. */
    fun newOutputFile(displayName: String): java.io.File
    /** Deletes a private working file. */
    fun release(file: java.io.File)
    /** Deletes every private working file (called at start-up and after processing). */
    suspend fun clearAll()
}
