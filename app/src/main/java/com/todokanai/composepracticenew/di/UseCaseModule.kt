package com.todokanai.composepracticenew.di

import com.todokanai.composepracticenew.repository.LocalDataRepository
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.RemoteFileNavigatorRepository
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import com.todokanai.composepracticenew.repository.FtpRepository
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.variables.FileListSorter
import com.todokanai.composepracticenew.variables.FileListSorterImpl
import com.todokanai.composepracticenew.usecase.FtpUseCase
import com.todokanai.composepracticenew.usecase.GetStorageListUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import com.todokanai.composepracticenew.usecase.RemoteStorageUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import com.todokanai.composepracticenew.usecase.StorageVolumeUseCase
import com.todokanai.composepracticenew.di.RemoteNavigator
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.service.FtpServiceController
import com.todokanai.composepracticenew.tools.AppExitHandler
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FtpConnectionResilienceUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionUseCase
import com.todokanai.composepracticenew.usecase.LocalFileOperationUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
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
    fun provideRemoteConnectionUseCase(nav: RemoteFileNavigatorRepository) =
        RemoteConnectionUseCase(nav)

    @Provides @Singleton
    fun provideFtpConnectionResilienceUseCase(
        ftpRepo: FtpRepository,
        localDataRepo: LocalDataRepository,
        remoteConnectionUseCase: RemoteConnectionUseCase
    ) = FtpConnectionResilienceUseCase(ftpRepo, localDataRepo, remoteConnectionUseCase)

    @Provides @Singleton
    fun provideConflictCheckUseCase() = ConflictCheckUseCase()

    @Provides @Singleton
    fun provideAppExitHandler(ftpServiceController: FtpServiceController) =
        AppExitHandler(ftpServiceController)

    @Provides @Singleton
    fun provideFileOperationOrchestrator(
        notifier: FileOperationNotifier,
        progressRepo: ProgressRepository,
        @ApplicationScope appScope: CoroutineScope
    ) = FileOperationOrchestrator(notifier, progressRepo, appScope)

    @Provides @Singleton
    fun provideLocalFileOperationUseCase(
        orchestrator: FileOperationOrchestrator,
        fileActionRepo: FileActionRepository
    ) = LocalFileOperationUseCase(orchestrator, fileActionRepo)

    @Provides @Singleton
    fun provideRemoteFileOperationUseCase(
        orchestrator: FileOperationOrchestrator,
        ftpRepo: FtpRepository
    ) = RemoteFileOperationUseCase(orchestrator, ftpRepo)

    @Provides @Singleton
    fun provideFileListSorter(): FileListSorter = FileListSorterImpl()
}
