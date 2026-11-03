package com.cleanshot.data.history

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * History row. Contains no image data, no paths/URIs and no metadata values (no GPS, names...).
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val timestamp: Long,
    val operation: String,
    val originalFormat: String,
    val resultFormat: String?,
    val sizeBefore: Long,
    val sizeAfter: Long?,
    val outcome: String,
    val removedCount: Int,
    val message: String?,
)

data class HistoryCounts(val analyzed: Int, val cleaned: Int, val saved: Long?)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query(
        "SELECT (SELECT COUNT(*) FROM history WHERE operation = 'ANALYSIS' AND outcome = 'SUCCESS') AS analyzed, " +
            "(SELECT COUNT(*) FROM history WHERE operation IN ('CLEANING','BATCH_CLEANING') AND outcome = 'SUCCESS') AS cleaned, " +
            "(SELECT SUM(sizeBefore - sizeAfter) FROM history WHERE operation IN ('CLEANING','BATCH_CLEANING') AND outcome = 'SUCCESS' AND sizeAfter IS NOT NULL) AS saved",
    )
    fun observeCounts(): Flow<HistoryCounts>

    @Insert
    suspend fun insert(entity: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = true)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
