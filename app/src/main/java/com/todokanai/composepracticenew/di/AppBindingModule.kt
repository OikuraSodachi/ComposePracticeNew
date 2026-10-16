package com.todokanai.composepracticenew.di

import com.todokanai.composepracticenew.service.ConnectionServiceController
import com.todokanai.composepracticenew.service.FtpServiceController
import com.todokanai.composepracticenew.tools.AppExitHandler
import com.todokanai.composepracticenew.tools.AppExitHandlerImpl
import com.todokanai.composepracticenew.tools.CompletionMessageProvider
import com.todokanai.composepracticenew.tools.OperationMessages
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** app 레이어 인프라 인터페이스(서비스 제어·메시지·앱 종료)를 Android 구현체에 바인딩한다. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingModule {
    @Binds
    abstract fun bindConnectionServiceController(impl: FtpServiceController): ConnectionServiceController

    @Binds
    abstract fun bindOperationMessages(impl: CompletionMessageProvider): OperationMessages

    @Binds
    abstract fun bindAppExitHandler(impl: AppExitHandlerImpl): AppExitHandler
}
