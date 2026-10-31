package com.todokanai.composepracticenew.di

import android.os.Environment
import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.composepracticenew.datasource.ArchiveExtractor
import com.todokanai.composepracticenew.datasource.LocalFileDataSource
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
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.FtpPathAwareFileSystem
import com.todokanai.composepracticenew.repository.FtpRepositoryImpl
import com.todokanai.composepracticenew.repository.LastConnectionRepository
import com.todokanai.composepracticenew.repository.LocalFileExplorerRepositoryImpl
import com.todokanai.composepracticenew.repository.LocalPathAwareFileSystem
import com.todokanai.composepracticenew.repository.PathAwareFileSystem
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.repository.ProgressTracker
import com.todokanai.composepracticenew.repository.RemoteConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteFileMutationRepository
import com.todokanai.composepracticenew.repository.RemoteFileNavigatorRepository
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

    @Binds @Singleton @RemoteNavigator
    abstract fun bindRemoteNavigatorAsFileNavigator(impl: RemoteFileNavigatorRepository): FileNavigatorRepository

    companion object {
        @Provides @Singleton
        fun provideLocalFileNavigatorRepository(
            converter: DataConverter,
            sortPreference: SortPreferenceRepository,
            @ApplicationScope appScope: CoroutineScope,
            localFiles: LocalFileDataSource
        ): FileNavigatorRepository = LocalFileExplorerRepositoryImpl(
            converter, sortPreference, appScope, localFiles,
            initialPath = Environment.getExternalStorageDirectory().absolutePath
        )

        @Provides @Singleton
        fun provideRemoteFileNavigatorRepository(
            converter: DataConverter,
            sortPreference: SortPreferenceRepository,
            @ApplicationScope appScope: CoroutineScope,
            fileSystems: Set<@JvmSuppressWildcards PathAwareFileSystem>,
            connection: RemoteConnectionRepository
        ): RemoteFileNavigatorRepository = FileExplorerRepositoryImpl(converter, sortPreference, appScope, fileSystems, connection)
    }
}
