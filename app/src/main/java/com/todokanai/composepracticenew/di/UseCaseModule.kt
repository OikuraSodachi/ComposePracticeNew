package com.todokanai.composepracticenew.di

import com.todokanai.composepracticenew.repository.LocalDataRepository
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import com.todokanai.composepracticenew.repository.FtpRepository
import com.todokanai.composepracticenew.usecase.FileActionUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.FtpUseCase
import com.todokanai.composepracticenew.usecase.GetStorageListUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import com.todokanai.composepracticenew.usecase.RemoteStorageUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import com.todokanai.composepracticenew.usecase.StorageVolumeUseCase
import com.todokanai.composepracticenew.di.RemoteNavigator
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.usecase.FileOperationUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/** Provides domain-layer UseCase instances as singletons. */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides @Singleton
    fun provideOpenFileUseCase(nav: FileNavigatorRepository) =
        OpenFileUseCase(nav) { _ -> } // stub — not yet implemented

    @Provides @Singleton
    fun provideGetStorageListUseCase(storageRepo: StorageVolumeRepository) =
        GetStorageListUseCase(storageRepo)

    @Provides @Singleton
    fun provideFileActionUseCase(repo: FileActionRepository) =
        FileActionUseCase(repo)

    @Provides @Singleton
    fun provideFileNavigatorUseCase(nav: FileNavigatorRepository) =
        FileNavigatorUseCase(nav)

    @Provides @Singleton @RemoteNavigator
    fun provideRemoteFileNavigatorUseCase(@RemoteNavigator nav: FileNavigatorRepository) =
        FileNavigatorUseCase(nav)

    @Provides @Singleton
    fun provideRemoteStorageUseCase(repo: LocalDataRepository) =
        RemoteStorageUseCase(repo)

    @Provides @Singleton
    fun provideStorageVolumeUseCase(repo: StorageVolumeRepository) =
        StorageVolumeUseCase(repo)

    @Provides @Singleton
    fun provideSortModeUseCase(repo: LocalDataRepository) =
        SortModeUseCase(repo)

    @Provides @Singleton
    fun provideProgressUseCase(repo: ProgressRepository) =
        ProgressUseCase(repo)

    @Provides @Singleton
    fun provideFtpUseCase(repo: FtpRepository) =
        FtpUseCase(repo)

    @Provides @Singleton
    fun provideFileOperationUseCase(
        notifier: FileOperationNotifier,
        progressUseCase: ProgressUseCase,
        fileActionUseCase: FileActionUseCase,
        ftpUseCase: FtpUseCase,
        @ApplicationScope appScope: CoroutineScope
    ) = FileOperationUseCase(notifier, progressUseCase, fileActionUseCase, ftpUseCase, appScope)
}
