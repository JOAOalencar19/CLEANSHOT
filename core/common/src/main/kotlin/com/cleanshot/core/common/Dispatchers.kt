package com.cleanshot.core.common

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides @Named("io") fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
    @Provides @Named("default") fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /** Application-wide scope for work that must outlive a screen (e.g. temp-file cleanup). */
    @Provides @Singleton @Named("app")
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
