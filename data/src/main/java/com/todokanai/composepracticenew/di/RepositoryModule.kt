package com.todokanai.composepracticenew.di

import android.os.Environment
import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.composepracticenew.data.datastore.PreferenceFailureNotices
import com.todokanai.composepracticenew.datasource.ArchiveExtractor
import com.todokanai.composepracticenew.datasource.LocalFileOperationDataSource
import com.todokanai.composepracticenew.datasource.LocalFileOperationDataSourceImpl
import com.todokanai.composepracticenew.datasource.RarArchiveExtractor
import com.todokanai.composepracticenew.datasource.ZipArchiveExtractor
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.FileActionRepositoryImpl
import com.todokanai.composepracticenew.repository.FileExplorerRepositoryImpl
import com.todokanai.composepracticenew.repository.FileInfoRepository
import com.todokanai.composepracticenew.repository.LocalFileInfoRepositoryImpl
import com.todokanai.composepracticenew.repository.FileSearchRepository
import com.todokanai.composepracticenew.repository.LocalFileSearchRepositoryImpl
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.FtpPathAwareFileSystem
import com.todokanai.composepracticenew.repository.FtpRepositoryImpl
import com.todokanai.composepracticenew.repository.LastConnectionRepository
import com.todokanai.composepracticenew.repository.LocalPathAwareFileSystem
import com.todokanai.composepracticenew.repository.PathAwareFileSystem
import com.todokanai.composepracticenew.repository.PreferenceFailureRepository
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.repository.ProgressTracker
import com.todokanai.composepracticenew.repository.RemoteConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteFileMutationRepository
import com.todokanai.composepracticenew.repository.RemoteFileTransferRepository
import com.todokanai.composepracticenew.repository.RemoteStorageRepository
import com.todokanai.composepracticenew.repository.RemoteStorageRepositoryImpl
import com.todokanai.composepracticenew.repository.SortPreferenceRepository
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import com.todokanai.composepracticenew.repository.StorageVolumeRepositoryImpl
import com.todokanai.composepracticenew.tools.MyNotification
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Singleton

/** Binds domain repository interfaces to their data-layer implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindSortPreferenceRepository(impl: DataStoreRepository): SortPreferenceRepository

    @Binds
    abstract fun bindLastConnectionRepository(impl: DataStoreRepository): LastConnectionRepository

    @Binds
    abstract fun bindPreferenceFailureRepository(impl: PreferenceFailureNotices): PreferenceFailureRepository

    @Binds
    abstract fun bindRemoteStorageRepository(impl: RemoteStorageRepositoryImpl): RemoteStorageRepository

    @Binds
    abstract fun bindStorageRepository(impl: StorageVolumeRepositoryImpl): StorageVolumeRepository

    @Binds
    abstract fun bindLocalFileOperationDataSource(impl: LocalFileOperationDataSourceImpl): LocalFileOperationDataSource

    @Binds
    abstract fun bindFileActionRepository(impl: FileActionRepositoryImpl): FileActionRepository

    /** 정보 다이얼로그는 로컬 목록 화면에만 있으므로 로컬 구현만 바인딩한다. 원격 화면에 붙이려면 원격 구현을 따로 둔다. */
    @Binds
    abstract fun bindFileInfoRepository(impl: LocalFileInfoRepositoryImpl): FileInfoRepository

    /** 파일 검색은 로컬 목록 화면에만 있으므로 로컬 구현만 바인딩한다. 원격(FTP)은 폴더마다 LIST를 보내 하나뿐인 연결을 나눠 써야 해 범위 밖이다. */
    @Binds
    abstract fun bindFileSearchRepository(impl: LocalFileSearchRepositoryImpl): FileSearchRepository

    @Binds
    abstract fun bindProgressRepository(impl: ProgressTracker): ProgressRepository

    /** 원격 스토리지 구현을 바꾸려면 아래 세 바인딩의 구현체만 교체한다. */
    @Binds
    abstract fun bindRemoteConnectionRepository(impl: FtpRepositoryImpl): RemoteConnectionRepository

    @Binds
    abstract fun bindRemoteFileTransferRepository(impl: FtpRepositoryImpl): RemoteFileTransferRepository

    @Binds
    abstract fun bindRemoteFileMutationRepository(impl: FtpRepositoryImpl): RemoteFileMutationRepository

    @Binds
    abstract fun bindFileOperationNotifier(impl: MyNotification): FileOperationNotifier

    @Binds @IntoSet
    abstract fun bindFtpPathAwareFileSystem(impl: FtpPathAwareFileSystem): PathAwareFileSystem

    @Binds @IntoSet
    abstract fun bindLocalPathAwareFileSystem(impl: LocalPathAwareFileSystem): PathAwareFileSystem

    @Binds @IntoSet
    abstract fun bindZipArchiveExtractor(impl: ZipArchiveExtractor): ArchiveExtractor

    @Binds @IntoSet
    abstract fun bindRarArchiveExtractor(impl: RarArchiveExtractor): ArchiveExtractor

    companion object {
        /** 로컬 화면은 로컬 경로만 다룬다. */
        @Provides @Singleton
        fun provideLocalFileNavigatorRepository(
            converter: DataConverter,
            sortPreference: SortPreferenceRepository,
            @ApplicationScope appScope: CoroutineScope,
            localFileSystem: LocalPathAwareFileSystem
        ): FileNavigatorRepository = FileExplorerRepositoryImpl(
            converter, sortPreference, appScope, SharingStarted.WhileSubscribed(5_000),
            fileSystems = setOf(localFileSystem),
            initialPath = Environment.getExternalStorageDirectory().absolutePath
        )

        /** 원격 화면은 등록된 모든 경로 종류를 다룬다(원격 경로와 로컬 경로). */
        @Provides @Singleton @RemoteNavigator
        fun provideRemoteFileNavigatorRepository(
            converter: DataConverter,
            sortPreference: SortPreferenceRepository,
            @ApplicationScope appScope: CoroutineScope,
            fileSystems: Set<@JvmSuppressWildcards PathAwareFileSystem>
        ): FileNavigatorRepository = FileExplorerRepositoryImpl(converter, sortPreference, appScope, SharingStarted.Lazily, fileSystems)
    }
}
