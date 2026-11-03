package com.cleanshot.domain

import com.cleanshot.domain.engine.MetadataAnalyzer
import com.cleanshot.domain.model.*
import com.cleanshot.domain.repository.*
import com.cleanshot.domain.usecase.AnalyzeImageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UseCaseTest {
    private class FakeHistory : HistoryRepository {
        val records = mutableListOf<HistoryRecord>()
        override fun observeRecent(limit: Int): Flow<List<HistoryRecord>> = flowOf(records)
        override fun observeStats(): Flow<HistoryStats> = flowOf(HistoryStats(0, 0, 0))
        override suspend fun add(record: HistoryRecord) { records += record }
        override suspend fun delete(id: Long) {}
        override suspend fun clear() { records.clear() }
    }

    private class FakeSettings(keep: Boolean) : SettingsRepository {
        override val settings = MutableStateFlow(UserSettings(keepHistory = keep))
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setDefaultOptions(options: CleaningOptions) {}
        override suspend fun setFileNameSuffix(suffix: String) {}
        override suspend fun setKeepHistory(keep: Boolean) {}
    }

    private class FakeWorkspace : WorkspaceRepository {
        val released = mutableListOf<File>()
        override suspend fun importCopy(ref: ImageRef) = File.createTempFile("copy", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        override fun newOutputFile(displayName: String) = File.createTempFile("out", ".jpg")
        override fun release(file: File) { released += file; file.delete() }
        override suspend fun clearAll() {}
    }

    private val ref = ImageRef("content://x/1", "foto.jpg", "image/jpeg", 3)

    @Test fun failedAnalysisReleasesPrivateCopyAndRecordsFailure() = runTest {
        val ws = FakeWorkspace(); val h = FakeHistory()
        val failing = object : MetadataAnalyzer { override fun analyze(file: File, displayName: String): AnalysisReport = throw IllegalStateException("corrompido") }
        try { AnalyzeImageUseCase(ws, failing, h, FakeSettings(true), Dispatchers.Unconfined)(ref); throw AssertionError() } catch (_: IllegalStateException) {}
        assertEquals(1, ws.released.size)
        assertFalse(ws.released.first().exists())
        assertEquals(OperationOutcome.FAILED, h.records.single().outcome)
    }

    @Test fun historyIsNotWrittenWhenDisabled() = runTest {
        val h = FakeHistory()
        val ok = object : MetadataAnalyzer {
            override fun analyze(file: File, displayName: String) = AnalysisReport(
                ImageFileInfo(displayName, 3, ImageFormat.JPEG, 1, 1), emptyList(),
                ProvenanceReport(ProvenanceVerdict.NO_CREDENTIALS, false, 0, emptyList(), emptyList(), emptyList(), emptyList(), emptyList()), emptyList())
        }
        val r = AnalyzeImageUseCase(FakeWorkspace(), ok, h, FakeSettings(false), Dispatchers.Unconfined)(ref)
        assertTrue(r.localCopy.exists())
        assertTrue(h.records.isEmpty())
    }

    @Test fun outputNamesKeepRealExtension() {
        assertEquals("IMG_1_clean.jpg", com.cleanshot.domain.util.OutputNaming.cleanedName("IMG_1.jpg", "_clean", ImageFormat.JPEG))
        assertEquals("foto_clean.png", com.cleanshot.domain.util.OutputNaming.cleanedName("foto.jpg", "_clean", ImageFormat.PNG))
    }

    @Test fun historyRecordsContainNoMetadataValues() {
        val fields = HistoryRecord::class.java.declaredFields.map { it.name }
        assertFalse(fields.any { it.contains("lat", true) || it.contains("gps", true) || it.contains("path", true) || it.contains("uri", true) })
    }
}
