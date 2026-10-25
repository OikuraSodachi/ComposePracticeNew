package com.todokanai.composepracticenew.di

import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.LastConnectionRepository
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.repository.RemoteConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteFileMutationRepository
import com.todokanai.composepracticenew.repository.RemoteFileNavigatorRepository
import com.todokanai.composepracticenew.repository.RemoteFileTransferRepository
import com.todokanai.composepracticenew.repository.RemoteStorageRepository
import com.todokanai.composepracticenew.repository.SortPreferenceRepository
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import com.todokanai.composepracticenew.tools.OperationMessages
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.GetStorageListUseCase
import com.todokanai.composepracticenew.usecase.LocalFileOperationUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionResilienceUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionStateUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import com.todokanai.composepracticenew.usecase.RemoteStorageUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import com.todokanai.composepracticenew.usecase.StorageVolumeUseCase
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
    fun provideRemoteStorageUseCase(repo: RemoteStorageRepository, lastConnectionRepo: LastConnectionRepository) =
        RemoteStorageUseCase(repo, lastConnectionRepo)

    @Provides @Singleton
    fun provideStorageVolumeUseCase(repo: StorageVolumeRepository) =
        StorageVolumeUseCase(repo)

    @Provides @Singleton
    fun provideSortModeUseCase(repo: SortPreferenceRepository) =
        SortModeUseCase(repo)

    @Provides @Singleton
    fun provideProgressUseCase(repo: ProgressRepository) =
        ProgressUseCase(repo)

    @Provides @Singleton
    fun provideRemoteConnectionStateUseCase(repo: RemoteConnectionRepository) =
        RemoteConnectionStateUseCase(repo)

    @Provides @Singleton
    fun provideRemoteConnectionUseCase(nav: RemoteFileNavigatorRepository) =
        RemoteConnectionUseCase(nav)

    @Provides @Singleton
    fun provideRemoteConnectionResilienceUseCase(
        lastConnectionRepo: LastConnectionRepository,
        remoteStorageRepo: RemoteStorageRepository,
        remoteConnectionUseCase: RemoteConnectionUseCase
    ) = RemoteConnectionResilienceUseCase(lastConnectionRepo, remoteStorageRepo, remoteConnectionUseCase)

    @Provides @Singleton
    fun provideConflictCheckUseCase(orchestrator: FileOperationOrchestrator, messages: OperationMessages) =
        ConflictCheckUseCase(orchestrator) { messages.destinationUnverified }

    @Provides @Singleton
    fun provideFileNameUseCase() = FileNameUseCase()

    @Provides @Singleton
    fun provideFileOperationOrchestrator(
        notifier: FileOperationNotifier,
        progressRepo: ProgressRepository,
        @ApplicationScope appScope: CoroutineScope
    ) = FileOperationOrchestrator(notifier, progressRepo, appScope)

    @Provides @Singleton
    fun provideLocalFileOperationUseCase(
        orchestrator: FileOperationOrchestrator,
        fileActionRepo: FileActionRepository,
        fileNameUseCase: FileNameUseCase
    ) = LocalFileOperationUseCase(orchestrator, fileActionRepo, fileNameUseCase)

    @Provides @Singleton
    fun provideRemoteFileOperationUseCase(
        orchestrator: FileOperationOrchestrator,
        transferRepo: RemoteFileTransferRepository,
        mutationRepo: RemoteFileMutationRepository,
        fileNameUseCase: FileNameUseCase
    ) = RemoteFileOperationUseCase(orchestrator, transferRepo, mutationRepo, fileNameUseCase)
}
