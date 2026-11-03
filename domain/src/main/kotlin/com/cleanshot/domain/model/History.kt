package com.cleanshot.domain.model

enum class OperationType(val label: String) { ANALYSIS("Análise"), CLEANING("Limpeza"), EXPORT("Exportação"), BATCH_CLEANING("Limpeza em lote") }

enum class OperationOutcome(val label: String) { SUCCESS("Concluído"), FAILED("Falhou"), CANCELLED("Cancelado"), PARTIAL("Parcial") }

/**
 * History record. Deliberately contains no image data, no GPS coordinates and no metadata
 * values — only what the user needs to recognise past operations.
 */
data class HistoryRecord(
    val id: Long = 0,
    val fileName: String,
    val timestampMillis: Long,
    val operation: OperationType,
    val originalFormat: String,
    val resultFormat: String?,
    val sizeBefore: Long,
    val sizeAfter: Long?,
    val outcome: OperationOutcome,
    val removedCount: Int = 0,
    val message: String? = null,
)

data class HistoryStats(val analyzedFiles: Int, val cleanedFiles: Int, val bytesSaved: Long)
