package com.todokanai.composepracticenew.di

import com.todokanai.composepracticenew.data.ftp.FtpFileSystem
import com.todokanai.composepracticenew.repository.FtpClientRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.apache.commons.net.ftp.FTPClient
import javax.inject.Singleton

/** FTPClient, FtpFileSystem, FtpClientRepository 인스턴스를 싱글톤으로 제공한다. */
@Module
@InstallIn(SingletonComponent::class)
object FtpModule {

    @Provides
    @Singleton
    fun provideFtpClient(): FTPClient = FTPClient()

    @Provides
    @Singleton
    fun provideFtpFileSystem(client: FTPClient): FtpFileSystem = FtpFileSystem(client)

    @Provides
    @Singleton
    fun provideFtpClientRepository(impl: FtpFileSystem): FtpClientRepository = impl
}
