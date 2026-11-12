package com.todokanai.composepracticenew.di

import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.operation.OperationProtection
import com.todokanai.composepracticenew.repository.ConflictQuestionAsker
import com.todokanai.composepracticenew.repository.ConflictQuestionRepository
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.FileInfoRepository
import com.todokanai.composepracticenew.repository.FileLauncher
import com.todokanai.composepracticenew.repository.FileSearchRepository
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.LastConnectionRepository
import com.todokanai.composepracticenew.repository.PreferenceFailureRepository
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import com.todokanai.composepracticenew.repository.OperationNoticeRepository
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.repository.RemoteConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteFileMutationRepository
import com.todokanai.composepracticenew.repository.RemoteFileTransferRepository
import com.todokanai.composepracticenew.repository.RemoteStorageRepository
import com.todokanai.composepracticenew.repository.SortPreferenceRepository
import com.todokanai.composepracticenew.repository.StorageNameMatching
import com.todokanai.composepracticenew.repository.StorageNameRules
import com.todokanai.composepracticenew.repository.StoragePathRules
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import com.todokanai.composepracticenew.tools.OperationMessages
import com.todokanai.composepracticenew.usecase.CancelOperationUseCase
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.ConflictQuestionUseCase
import com.todokanai.composepracticenew.usecase.FileInfoUseCase
import com.todokanai.composepracticenew.usecase.FileSearchUseCase
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.GetStorageListUseCase
import com.todokanai.composepracticenew.usecase.LocalFileOperationUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import com.todokanai.composepracticenew.usecase.OperationNoticeUseCase
import com.todokanai.composepracticenew.usecase.PreferenceFailureUseCase
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
    fun provideOpenFileUseCase(nav: FileNavigatorRepository, launcher: FileLauncher, notices: OperationNoticeReporter) =
        OpenFileUseCase(nav, launcher, notices)

    @Provides @Singleton
    fun provideFileInfoUseCase(repo: FileInfoRepository) =
        FileInfoUseCase(repo)

    @Provides @Singleton
    fun provideFileSearchUseCase(repo: FileSearchRepository, storage: StorageVolumeRepository, nav: FileNavigatorRepository, paths: StoragePathRules) =
        FileSearchUseCase(repo, storage, nav, paths)

    @Provides @Singleton
    fun provideGetStorageListUseCase(storageRepo: StorageVolumeRepository) =
        GetStorageListUseCase(storageRepo)

    @Provides @Singleton
    fun provideFileNavigatorUseCase(nav: FileNavigatorRepository, paths: StoragePathRules) =
        FileNavigatorUseCase(nav, paths)

    @Provides @Singleton @RemoteNavigator
    fun provideRemoteFileNavigatorUseCase(@RemoteNavigator nav: FileNavigatorRepository, @RemoteNavigator paths: StoragePathRules) =
        FileNavigatorUseCase(nav, paths)

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
    fun providePreferenceFailureUseCase(repo: PreferenceFailureRepository) =
        PreferenceFailureUseCase(repo)

    @Provides @Singleton
    fun provideProgressUseCase(repo: ProgressRepository) =
        ProgressUseCase(repo)

    @Provides @Singleton
    fun provideOperationNoticeUseCase(repo: OperationNoticeRepository) =
        OperationNoticeUseCase(repo)

    @Provides @Singleton
    fun provideRemoteConnectionStateUseCase(repo: RemoteConnectionRepository) =
        RemoteConnectionStateUseCase(repo)

    @Provides @Singleton
    fun provideRemoteConnectionUseCase(
        connection: RemoteConnectionRepository,
        @RemoteNavigator nav: FileNavigatorRepository,
        lastConnectionRepo: LastConnectionRepository
    ) = RemoteConnectionUseCase(connection, nav, lastConnectionRepo)

    @Provides @Singleton
    fun provideRemoteConnectionResilienceUseCase(
        lastConnectionRepo: LastConnectionRepository,
        remoteStorageRepo: RemoteStorageRepository,
        remoteConnectionUseCase: RemoteConnectionUseCase
    ) = RemoteConnectionResilienceUseCase(lastConnectionRepo, remoteStorageRepo, remoteConnectionUseCase)

    @Provides @Singleton
    fun provideConflictCheckUseCase(notices: OperationNoticeReporter, nameMatching: StorageNameMatching, messages: OperationMessages) =
        ConflictCheckUseCase(notices, nameMatching) { messages.destinationUnverified }

    @Provides @Singleton
    fun provideFileOperationOrchestrator(
        notifier: FileOperationNotifier,
        progressRepo: ProgressRepository,
        notices: OperationNoticeReporter,
        protection: OperationProtection,
        questions: ConflictQuestionAsker,
        @ApplicationScope appScope: CoroutineScope
    ) = FileOperationOrchestrator(notifier, progressRepo, notices, protection, questions, appScope)

    @Provides @Singleton
    fun provideConflictQuestionUseCase(questions: ConflictQuestionRepository) =
        ConflictQuestionUseCase(questions)

    @Provides @Singleton
    fun provideCancelOperationUseCase(orchestrator: FileOperationOrchestrator) =
        CancelOperationUseCase(orchestrator)

    /** 이름 규칙은 로컬 화면이 다루는 저장소의 제약으로 판정한다. */
    @Provides @Singleton
    fun provideLocalFileOperationUseCase(
        orchestrator: FileOperationOrchestrator,
        notices: OperationNoticeReporter,
        fileActionRepo: FileActionRepository,
        nameRules: StorageNameRules,
        paths: StoragePathRules
    ) = LocalFileOperationUseCase(orchestrator, notices, fileActionRepo, FileNameUseCase(nameRules), paths)

    /** 이름 규칙은 원격 화면이 다루는 저장소의 제약으로 판정한다. */
    @Provides @Singleton
    fun provideRemoteFileOperationUseCase(
        orchestrator: FileOperationOrchestrator,
        notices: OperationNoticeReporter,
        transferRepo: RemoteFileTransferRepository,
        mutationRepo: RemoteFileMutationRepository,
        @RemoteNavigator nameRules: StorageNameRules
    ) = RemoteFileOperationUseCase(orchestrator, notices, transferRepo, mutationRepo, FileNameUseCase(nameRules))
}
