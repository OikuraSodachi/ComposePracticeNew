package com.todokanai.composepracticenew.di

import android.content.Context
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.RemoteStorageRepository
import com.todokanai.composepracticenew.repository.SortModeRepository
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import com.todokanai.composepracticenew.usecase.AddRemoteStorageUseCase
import com.todokanai.composepracticenew.usecase.FileActionUseCase
import com.todokanai.composepracticenew.usecase.GetStorageListUseCase
import com.todokanai.composepracticenew.tools.fileaction.OpenAction
import com.todokanai.composepracticenew.usecase.NavigateBackUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import com.todokanai.composepracticenew.usecase.UpdateSortModeUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides domain-layer UseCase instances as singletons. */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides @Singleton
    fun provideNavigateBackUseCase(nav: FileNavigatorRepository) =
        NavigateBackUseCase(nav)

    @Provides @Singleton
    fun provideOpenFileUseCase(
        nav: FileNavigatorRepository,
        @ApplicationContext context: Context
    ) = OpenFileUseCase(nav) { file -> OpenAction().openFile(context, file) }

    @Provides @Singleton
    fun provideUpdateSortModeUseCase(
        sortModeRepo: SortModeRepository
    ) = UpdateSortModeUseCase(sortModeRepo)

    @Provides @Singleton
    fun provideGetStorageListUseCase(storageRepo: StorageVolumeRepository) =
        GetStorageListUseCase(storageRepo)

    @Provides @Singleton
    fun provideFileActionUseCase(repo: FileActionRepository) =
        FileActionUseCase(repo)

    @Provides @Singleton
    fun provideAddRemoteStorageUseCase(repo: RemoteStorageRepository) =
        AddRemoteStorageUseCase(repo)
}
