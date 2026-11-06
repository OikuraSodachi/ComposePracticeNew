package com.todokanai.composepracticenew.di

import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.OperationProtection
import com.todokanai.composepracticenew.service.ConnectionServiceController
import com.todokanai.composepracticenew.service.FileOperationProtection
import com.todokanai.composepracticenew.service.FileOperationServiceController
import com.todokanai.composepracticenew.service.FileOperationServiceControllerImpl
import com.todokanai.composepracticenew.service.FtpServiceController
import com.todokanai.composepracticenew.tools.AppExitHandler
import com.todokanai.composepracticenew.tools.AppExitHandlerImpl
import com.todokanai.composepracticenew.tools.CompletionMessageProvider
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.tools.OperationMessages
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** app 레이어 인프라 인터페이스(서비스 제어·작업 보호·메시지·작업 알림·앱 종료)를 Android 구현체에 바인딩한다. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingModule {
    @Binds
    abstract fun bindConnectionServiceController(impl: FtpServiceController): ConnectionServiceController

    @Binds
    abstract fun bindFileOperationServiceController(impl: FileOperationServiceControllerImpl): FileOperationServiceController

    @Binds
    abstract fun bindOperationProtection(impl: FileOperationProtection): OperationProtection

    @Binds
    abstract fun bindOperationMessages(impl: CompletionMessageProvider): OperationMessages

    @Binds
    abstract fun bindFileOperationNotifier(impl: MyNotification): FileOperationNotifier

    @Binds
    abstract fun bindAppExitHandler(impl: AppExitHandlerImpl): AppExitHandler
}
