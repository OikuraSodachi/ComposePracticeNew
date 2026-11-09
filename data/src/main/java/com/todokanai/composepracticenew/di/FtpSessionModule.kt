package com.todokanai.composepracticenew.di

import com.todokanai.composepracticenew.data.ftp.FtpCommandSession
import com.todokanai.composepracticenew.data.ftp.FtpConnectionLifecycle
import com.todokanai.composepracticenew.data.ftp.FtpConnectionStatus
import com.todokanai.composepracticenew.data.ftp.FtpSession
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** FTP 세션의 역할 인터페이스를 하나의 세션 구현에 묶는다. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class FtpSessionModule {
    @Binds
    abstract fun bindFtpCommandSession(impl: FtpSession): FtpCommandSession

    @Binds
    abstract fun bindFtpConnectionLifecycle(impl: FtpSession): FtpConnectionLifecycle

    @Binds
    abstract fun bindFtpConnectionStatus(impl: FtpSession): FtpConnectionStatus
}
