package com.todokanai.composepracticenew.di

import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.OperationQuestionRepository
import com.todokanai.composepracticenew.repository.ConnectFailureAlertReporter
import com.todokanai.composepracticenew.repository.ConnectFailureAlertRepository
import com.todokanai.composepracticenew.repository.ConnectionLossNoticeRepository
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.FileInfoRepository
import com.todokanai.composepracticenew.repository.FileLauncher
import com.todokanai.composepracticenew.repository.FileSearchRepository
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.FolderEntryNamesRepository
import com.todokanai.composepracticenew.repository.PreferenceFailureRepository
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import com.todokanai.composepracticenew.repository.OperationNoticeRepository
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.repository.RemoteConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteFileMutationRepository
import com.todokanai.composepracticenew.repository.RemoteFileTransferRepository
import com.todokanai.composepracticenew.repository.RemoteRootNavigator
import com.todokanai.composepracticenew.repository.RemoteSessionProvider
import com.todokanai.composepracticenew.repository.RemoteStorageRepository
import com.todokanai.composepracticenew.repository.SortPreferenceRepository
import com.todokanai.composepracticenew.repository.StorageNameMatching
import com.todokanai.composepracticenew.repository.StorageNameRules
import com.todokanai.composepracticenew.repository.StoragePathRules
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import com.todokanai.composepracticenew.usecase.CancelOperationUseCase
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.OperationQuestionUseCase
import com.todokanai.composepracticenew.usecase.ConnectFailureAlertUseCase
import com.todokanai.composepracticenew.usecase.ConnectFailureReportUseCase
import com.todokanai.composepracticenew.usecase.ConnectionLossNoticeUseCase
import com.todokanai.composepracticenew.usecase.FileInfoUseCase
import com.todokanai.composepracticenew.usecase.FileSearchUseCase
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.LoadStorageListUseCase
import com.todokanai.composepracticenew.usecase.LocalFileOperationUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import com.todokanai.composepracticenew.usecase.ArchiveViewUseCase
import com.todokanai.composepracticenew.repository.ArchiveContentRepository
import com.todokanai.composepracticenew.usecase.OperationNoticeUseCase
import com.todokanai.composepracticenew.usecase.PreferenceFailureUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionDropUseCase
import com.todokanai.composepracticenew.usecase.RemoteSessionCheckUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionStateUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionUseCase
import com.todokanai.composepracticenew.usecase.RemoteDisconnectUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import com.todokanai.composepracticenew.usecase.RemoteStorageUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import com.todokanai.composepracticenew.usecase.StorageVolumeUseCase
import com.todokanai.composepracticenew.usecase.UploadConflictCheckUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Provides domain-layer UseCase instances, unscoped so each one lives as long as the class that receives it. */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    fun provideOpenFileUseCase(nav: FileNavigatorRepository, launcher: FileLauncher, notices: OperationNoticeReporter) =
        OpenFileUseCase(nav, launcher, notices)

    @Provides
    fun provideArchiveViewUseCase(repo: ArchiveContentRepository) = ArchiveViewUseCase(repo)

    @Provides
    fun provideFileInfoUseCase(repo: FileInfoRepository, paths: StoragePathRules) =
        FileInfoUseCase(repo, paths)

    @Provides
    fun provideFileSearchUseCase(repo: FileSearchRepository, storage: StorageVolumeRepository, nav: FileNavigatorRepository, paths: StoragePathRules) =
        FileSearchUseCase(repo, storage, nav, paths)

    @Provides
    fun provideLoadStorageListUseCase(storageRepo: StorageVolumeRepository) =
        LoadStorageListUseCase(storageRepo)

    @Provides
    fun provideFileNavigatorUseCase(nav: FileNavigatorRepository, paths: StoragePathRules) =
        FileNavigatorUseCase(nav, paths)

    @Provides @RemoteNavigator
    fun provideRemoteFileNavigatorUseCase(@RemoteNavigator nav: FileNavigatorRepository, @RemoteNavigator paths: StoragePathRules) =
        FileNavigatorUseCase(nav, paths)

    @Provides
    fun provideRemoteStorageUseCase(repo: RemoteStorageRepository) = RemoteStorageUseCase(repo)

    @Provides
    fun provideStorageVolumeUseCase(repo: StorageVolumeRepository) =
        StorageVolumeUseCase(repo)

    @Provides
    fun provideSortModeUseCase(repo: SortPreferenceRepository) =
        SortModeUseCase(repo)

    @Provides
    fun providePreferenceFailureUseCase(repo: PreferenceFailureRepository) =
        PreferenceFailureUseCase(repo)

    @Provides
    fun provideConnectionLossNoticeUseCase(repo: ConnectionLossNoticeRepository) =
        ConnectionLossNoticeUseCase(repo)

    @Provides
    fun provideConnectFailureAlertUseCase(repo: ConnectFailureAlertRepository) =
        ConnectFailureAlertUseCase(repo)

    @Provides
    fun provideConnectFailureReportUseCase(reporter: ConnectFailureAlertReporter) =
        ConnectFailureReportUseCase(reporter)

    @Provides
    fun provideProgressUseCase(repo: ProgressRepository) =
        ProgressUseCase(repo)

    @Provides
    fun provideOperationNoticeUseCase(repo: OperationNoticeRepository) =
        OperationNoticeUseCase(repo)

    @Provides
    fun provideRemoteConnectionStateUseCase(repo: RemoteConnectionRepository) =
        RemoteConnectionStateUseCase(repo)

    @Provides
    fun provideRemoteDisconnectUseCase(repo: RemoteConnectionRepository) =
        RemoteDisconnectUseCase(repo)

    @Provides
    fun provideRemoteConnectionDropUseCase(repo: RemoteConnectionRepository) =
        RemoteConnectionDropUseCase(repo)

    @Provides
    fun provideRemoteConnectionUseCase(
        connection: RemoteConnectionRepository,
        rootNavigator: RemoteRootNavigator,
        failureAlerts: ConnectFailureAlertReporter
    ) = RemoteConnectionUseCase(connection, rootNavigator, failureAlerts)

    @Provides
    fun provideRemoteSessionCheckUseCase(connection: RemoteConnectionRepository, notices: OperationNoticeReporter) =
        RemoteSessionCheckUseCase(connection, notices)

    @Provides
    fun provideConflictCheckUseCase(notices: OperationNoticeReporter, nameMatching: StorageNameMatching) =
        ConflictCheckUseCase(notices, nameMatching)

    @Provides
    fun provideUploadConflictCheckUseCase(conflictCheck: ConflictCheckUseCase, sourceInfo: FolderEntryNamesRepository, nameMatching: StorageNameMatching) =
        UploadConflictCheckUseCase(conflictCheck, sourceInfo, nameMatching)

    @Provides
    fun provideOperationQuestionUseCase(questions: OperationQuestionRepository) =
        OperationQuestionUseCase(questions)

    @Provides
    fun provideCancelOperationUseCase(orchestrator: FileOperationOrchestrator) =
        CancelOperationUseCase(orchestrator)

    /** 이름 규칙은 로컬 화면이 다루는 저장소의 제약으로 판정한다. */
    @Provides
    fun provideLocalFileOperationUseCase(
        orchestrator: FileOperationOrchestrator,
        notices: OperationNoticeReporter,
        fileActionRepo: FileActionRepository,
        nameRules: StorageNameRules,
        paths: StoragePathRules
    ) = LocalFileOperationUseCase(orchestrator, notices, fileActionRepo, FileNameUseCase(nameRules), paths)

    /** 이름 규칙은 원격 화면이 다루는 저장소의 제약으로 판정한다. */
    @Provides
    fun provideRemoteFileOperationUseCase(
        orchestrator: FileOperationOrchestrator,
        notices: OperationNoticeReporter,
        transferRepo: RemoteFileTransferRepository,
        mutationRepo: RemoteFileMutationRepository,
        sessions: RemoteSessionProvider,
        @RemoteNavigator nameRules: StorageNameRules
    ) = RemoteFileOperationUseCase(orchestrator, notices, transferRepo, mutationRepo, sessions, FileNameUseCase(nameRules))
}
