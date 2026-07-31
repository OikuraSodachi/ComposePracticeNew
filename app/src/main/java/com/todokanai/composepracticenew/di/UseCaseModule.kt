package com.todokanai.composepracticenew.di

import android.content.Context
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.SortModeRepository
import com.todokanai.composepracticenew.repository.StorageRepository
import com.todokanai.composepracticenew.usecase.GetStorageListUseCase
import com.todokanai.composepracticenew.tools.fileaction.OpenAction
import com.todokanai.composepracticenew.usecase.CopyFilesUseCase
import com.todokanai.composepracticenew.usecase.DeleteFilesUseCase
import com.todokanai.composepracticenew.usecase.MoveFilesUseCase
import com.todokanai.composepracticenew.usecase.NavigateBackUseCase
import com.todokanai.composepracticenew.usecase.NavigateToDirectoryUseCase
import com.todokanai.composepracticenew.usecase.NewFolderUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import com.todokanai.composepracticenew.usecase.RenameFileUseCase
import com.todokanai.composepracticenew.usecase.UnzipFilesUseCase
import com.todokanai.composepracticenew.usecase.UpdateSortModeUseCase
import com.todokanai.composepracticenew.usecase.ZipFilesUseCase
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
    fun provideNavigateToDirectoryUseCase(nav: FileNavigatorRepository) =
        NavigateToDirectoryUseCase(nav)

    @Provides @Singleton
    fun provideNavigateBackUseCase(nav: FileNavigatorRepository) =
        NavigateBackUseCase(nav)

    @Provides @Singleton
    fun provideCopyFilesUseCase(fileAction: FileActionRepository) =
        CopyFilesUseCase(fileAction)

    @Provides @Singleton
    fun provideMoveFilesUseCase(fileAction: FileActionRepository) =
        MoveFilesUseCase(fileAction)

    @Provides @Singleton
    fun provideDeleteFilesUseCase(fileAction: FileActionRepository) =
        DeleteFilesUseCase(fileAction)

    @Provides @Singleton
    fun provideRenameFileUseCase(fileAction: FileActionRepository) =
        RenameFileUseCase(fileAction)

    @Provides @Singleton
    fun provideNewFolderUseCase(fileAction: FileActionRepository) =
        NewFolderUseCase(fileAction)

    @Provides @Singleton
    fun provideZipFilesUseCase(fileAction: FileActionRepository) =
        ZipFilesUseCase(fileAction)

    @Provides @Singleton
    fun provideUnzipFilesUseCase(fileAction: FileActionRepository) =
        UnzipFilesUseCase(fileAction)

    @Provides @Singleton
    fun provideOpenFileUseCase(
        nav: FileNavigatorRepository,
        @ApplicationContext context: Context
    ) = OpenFileUseCase(nav) { file -> OpenAction().openFile(context, file) }

    @Provides @Singleton
    fun provideUpdateSortModeUseCase(
        sortModeRepo: SortModeRepository,
        nav: FileNavigatorRepository
    ) = UpdateSortModeUseCase(sortModeRepo, nav)

    @Provides @Singleton
    fun provideGetStorageListUseCase(storageRepo: StorageRepository) =
        GetStorageListUseCase(storageRepo)
}
