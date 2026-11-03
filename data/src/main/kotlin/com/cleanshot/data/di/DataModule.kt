package com.cleanshot.data.di

import android.content.Context
import androidx.room.Room
import com.cleanshot.data.batch.WorkManagerProcessingQueue
import com.cleanshot.data.export.AndroidDecodeVerifier
import com.cleanshot.data.export.AndroidExportManager
import com.cleanshot.data.history.HistoryDao
import com.cleanshot.data.history.HistoryDatabase
import com.cleanshot.data.history.RoomHistoryRepository
import com.cleanshot.data.importer.AndroidImageImportRepository
import com.cleanshot.data.settings.DataStoreSettingsRepository
import com.cleanshot.data.workspace.PrivateWorkspace
import com.cleanshot.domain.engine.C2paInspector
import com.cleanshot.domain.engine.ImageFormatDetector
import com.cleanshot.domain.engine.ImagePreservationEngine
import com.cleanshot.domain.engine.MetadataAnalyzer
import com.cleanshot.domain.engine.PrivacyCleaner
import com.cleanshot.domain.repository.DecodeVerifier
import com.cleanshot.domain.repository.ExportManager
import com.cleanshot.domain.repository.HistoryRepository
import com.cleanshot.domain.repository.ImageImportRepository
import com.cleanshot.domain.repository.ProcessingQueue
import com.cleanshot.domain.repository.SettingsRepository
import com.cleanshot.domain.repository.WorkspaceRepository
import com.cleanshot.processing.analyzer.DefaultMetadataAnalyzer
import com.cleanshot.processing.c2pa.DefaultC2paInspector
import com.cleanshot.processing.cleaner.DefaultPrivacyCleaner
import com.cleanshot.processing.format.DefaultImageFormatDetector
import com.cleanshot.processing.preservation.DefaultImagePreservationEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindings {
    @Binds abstract fun history(impl: RoomHistoryRepository): HistoryRepository
    @Binds abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository
    @Binds abstract fun workspace(impl: PrivateWorkspace): WorkspaceRepository
    @Binds abstract fun importer(impl: AndroidImageImportRepository): ImageImportRepository
    @Binds abstract fun exporter(impl: AndroidExportManager): ExportManager
    @Binds abstract fun queue(impl: WorkManagerProcessingQueue): ProcessingQueue
    @Binds abstract fun decodeVerifier(impl: AndroidDecodeVerifier): DecodeVerifier
}

/** Wires the platform-independent processing engine behind the domain interfaces. */
@Module
@InstallIn(SingletonComponent::class)
object EngineModule {
    @Provides @Singleton fun detector(): ImageFormatDetector = DefaultImageFormatDetector()
    @Provides @Singleton fun c2pa(): C2paInspector = DefaultC2paInspector()
    @Provides @Singleton fun preservation(): ImagePreservationEngine = DefaultImagePreservationEngine()
    @Provides @Singleton fun defaultAnalyzer(detector: ImageFormatDetector, c2pa: C2paInspector) = DefaultMetadataAnalyzer(detector, c2pa)
    @Provides @Singleton fun analyzer(impl: DefaultMetadataAnalyzer): MetadataAnalyzer = impl
    @Provides @Singleton fun cleaner(analyzer: DefaultMetadataAnalyzer, preservation: ImagePreservationEngine): PrivacyCleaner =
        DefaultPrivacyCleaner(analyzer, preservation)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): HistoryDatabase =
        Room.databaseBuilder(context, HistoryDatabase::class.java, "cleanshot-history.db").build()

    @Provides fun dao(db: HistoryDatabase): HistoryDao = db.historyDao()
}
