package com.cleanshot.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cleanshot.data.history.HistoryDatabase
import com.cleanshot.data.history.RoomHistoryRepository
import com.cleanshot.domain.model.HistoryRecord
import com.cleanshot.domain.model.OperationOutcome
import com.cleanshot.domain.model.OperationType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDaoTest {
    private lateinit var db: HistoryDatabase
    private lateinit var repo: RoomHistoryRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HistoryDatabase::class.java).build()
        repo = RoomHistoryRepository(db.historyDao())
    }

    @After fun tearDown() = db.close()

    private fun record(op: OperationType, before: Long = 1000, after: Long? = 900) = HistoryRecord(
        fileName = "foto.jpg", timestampMillis = System.currentTimeMillis(), operation = op, originalFormat = "JPEG",
        resultFormat = "JPEG", sizeBefore = before, sizeAfter = after, outcome = OperationOutcome.SUCCESS, removedCount = 3,
    )

    @Test fun insertsCountsAndClears() = runTest {
        repo.add(record(OperationType.ANALYSIS, after = null))
        repo.add(record(OperationType.CLEANING))
        repo.add(record(OperationType.BATCH_CLEANING, 2000, 1500))
        val stats = repo.observeStats().first()
        assertEquals(1, stats.analyzedFiles)
        assertEquals(2, stats.cleanedFiles)
        assertEquals(600L, stats.bytesSaved)
        assertEquals(3, repo.observeRecent().first().size)
        repo.clear()
        assertTrue(repo.observeRecent().first().isEmpty())
    }
}
