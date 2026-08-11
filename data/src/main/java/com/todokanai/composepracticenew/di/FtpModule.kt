package com.todokanai.composepracticenew.di

import com.todokanai.composepracticenew.data.ftp.FtpFileSystem
import com.todokanai.composepracticenew.repository.FtpClientRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** FtpFileSystem을 FtpClientRepository로 바인딩한다. FTPClient는 FtpConnectionState 내부에서 생성된다. */
@Module
@InstallIn(SingletonComponent::class)
abstract class FtpModule {

    @Binds
    @Singleton
    abstract fun bindFtpClientRepository(impl: FtpFileSystem): FtpClientRepository
}
