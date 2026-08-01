package com.todokanai.composepracticenew.di

import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.composepracticenew.repository.FileExplorerRepositoryImpl
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.SortModeRepository
import com.todokanai.composepracticenew.repository.StorageVolumeRepositoryImpl
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds domain repository interfaces to their data-layer implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindFileNavigatorRepository(impl: FileExplorerRepositoryImpl): FileNavigatorRepository

    @Binds
    abstract fun bindSortModeRepository(impl: DataStoreRepository): SortModeRepository

    @Binds
    abstract fun bindStorageRepository(impl: StorageVolumeRepositoryImpl): StorageVolumeRepository
}
