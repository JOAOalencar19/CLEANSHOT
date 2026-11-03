package com.cleanshot.data.history

import com.cleanshot.domain.model.HistoryRecord
import com.cleanshot.domain.model.HistoryStats
import com.cleanshot.domain.model.OperationOutcome
import com.cleanshot.domain.model.OperationType
import com.cleanshot.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomHistoryRepository @Inject constructor(private val dao: HistoryDao) : HistoryRepository {

    override fun observeRecent(limit: Int): Flow<List<HistoryRecord>> = dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeStats(): Flow<HistoryStats> = dao.observeCounts().map { HistoryStats(it.analyzed, it.cleaned, it.saved ?: 0L) }

    override suspend fun add(record: HistoryRecord) { dao.insert(record.toEntity()) }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun clear() = dao.clear()

    private fun HistoryEntity.toDomain() = HistoryRecord(
        id = id, fileName = fileName, timestampMillis = timestamp,
        operation = runCatching { OperationType.valueOf(operation) }.getOrDefault(OperationType.ANALYSIS),
        originalFormat = originalFormat, resultFormat = resultFormat, sizeBefore = sizeBefore, sizeAfter = sizeAfter,
        outcome = runCatching { OperationOutcome.valueOf(outcome) }.getOrDefault(OperationOutcome.FAILED),
        removedCount = removedCount, message = message,
    )

    private fun HistoryRecord.toEntity() = HistoryEntity(
        id = 0, fileName = fileName.take(255), timestamp = timestampMillis, operation = operation.name,
        originalFormat = originalFormat, resultFormat = resultFormat, sizeBefore = sizeBefore, sizeAfter = sizeAfter,
        outcome = outcome.name, removedCount = removedCount, message = message?.take(300),
    )
}
