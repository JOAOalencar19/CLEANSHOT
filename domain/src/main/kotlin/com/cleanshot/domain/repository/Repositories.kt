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
}

/** Background batch processing queue (WorkManager on Android). */
interface ProcessingQueue {
    val progress: Flow<BatchProgress>
    fun enqueue(images: List<ImageRef>, options: CleaningOptions, destinationTreeUri: String)
    fun cancel()
    fun clearFinished()
}
