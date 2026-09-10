package com.todokanai.composepracticenew.di

import android.os.Environment
import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.composepracticenew.repository.FtpRepository
import com.todokanai.composepracticenew.repository.LocalDataRepository
import com.todokanai.composepracticenew.repository.LocalDataRepositoryImpl
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.FileActionRepositoryImpl
import com.todokanai.composepracticenew.repository.FileExplorerRepositoryImpl
import com.todokanai.composepracticenew.repository.FtpRepositoryImpl
import com.todokanai.composepracticenew.repository.LocalFileExplorerRepositoryImpl
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.repository.ProgressTracker
import com.todokanai.composepracticenew.repository.StorageVolumeRepositoryImpl
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.todokanai.composepracticenew.repository.FileOperationNotifier
import com.todokanai.composepracticenew.tools.MyNotification

/** Binds domain repository interfaces to their data-layer implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindLocalDataRepository(impl: LocalDataRepositoryImpl): LocalDataRepository

    @Binds
    abstract fun bindStorageRepository(impl: StorageVolumeRepositoryImpl): StorageVolumeRepository

    @Binds
    abstract fun bindFileActionRepository(impl: FileActionRepositoryImpl): FileActionRepository

    @Binds
    abstract fun bindProgressRepository(impl: ProgressTracker): ProgressRepository

    @Binds
    abstract fun bindFtpRepository(impl: FtpRepositoryImpl): FtpRepository

    @Binds
    abstract fun bindFileOperationNotifier(impl: MyNotification): FileOperationNotifier

    companion object {
        @Provides @Singleton
        fun provideLocalFileNavigatorRepository(
            converter: DataConverter,
            dsRepo: DataStoreRepository
        ): FileNavigatorRepository = LocalFileExplorerRepositoryImpl(
            converter, dsRepo,
            initialPath = Environment.getExternalStorageDirectory().absolutePath
        )

        @Provides @Singleton @RemoteNavigator
        fun provideRemoteFileNavigatorRepository(
            converter: DataConverter,
            dsRepo: DataStoreRepository,
            ftpFileSystem: FtpRepository
        ): FileNavigatorRepository = FileExplorerRepositoryImpl(converter, dsRepo, ftpFileSystem)
    }
}
