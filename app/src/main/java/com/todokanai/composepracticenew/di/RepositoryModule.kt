package com.todokanai.composepracticenew.di

import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.tools.FileAction
import com.todokanai.composepracticenew.repository.FileNavigator
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.SortModeRepository
import com.todokanai.composepracticenew.repository.StorageRepository
import com.todokanai.composepracticenew.repository.StorageRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds domain repository interfaces to their app-layer implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindFileNavigatorRepository(impl: FileNavigator): FileNavigatorRepository

    @Binds
    abstract fun bindFileActionRepository(impl: FileAction): FileActionRepository

    @Binds
    abstract fun bindSortModeRepository(impl: DataStoreRepository): SortModeRepository

    @Binds
    abstract fun bindStorageRepository(impl: StorageRepositoryImpl): StorageRepository
}
