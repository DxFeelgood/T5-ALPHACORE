package com.example.data.remote

/**
 * Report dettagliato della procedura di sincronizzazione e risoluzione dei conflitti tra Room e Firestore.
 */
data class SyncReport(
    val totalProcessed: Int = 0,
    val uploadedCount: Int = 0,
    val resolvedConflictsCount: Int = 0,
    val upToDateCount: Int = 0,
    val errorCount: Int = 0,
    val imagesCount: Int = 0,
    val message: String = ""
) {
    val isSuccess: Boolean get() = errorCount == 0
}

/**
 * Risultato dell'esportazione diretta verso Cloud Firestore (senza generazione di stringhe JSON).
 */
data class DirectExportResult(
    val exportedCount: Int = 0,
    val failedCount: Int = 0,
    val totalBooks: Int = 0,
    val imagesCount: Int = 0,
    val collectionName: String = "books",
    val timestamp: Long = System.currentTimeMillis(),
    val message: String = ""
) {
    val isSuccess: Boolean get() = failedCount == 0 && exportedCount > 0
}

/**
 * Risultato dell'importazione diretta da Cloud Firestore nel database locale Room.
 */
data class DirectImportResult(
    val importedCount: Int = 0,
    val updatedCount: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val imagesRestoredCount: Int = 0,
    val totalRemote: Int = 0,
    val collectionName: String = "books",
    val timestamp: Long = System.currentTimeMillis(),
    val message: String = ""
) {
    val isSuccess: Boolean get() = (importedCount > 0 || updatedCount > 0 || totalRemote == 0) && failedCount == 0
}

