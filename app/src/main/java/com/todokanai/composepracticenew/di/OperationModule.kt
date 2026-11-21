package com.todokanai.composepracticenew.di

import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.operation.OperationProtection
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import com.todokanai.composepracticenew.repository.OperationQuestionAsker
import com.todokanai.composepracticenew.repository.ProgressReporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/** 작업 인스턴스 ID 발급과 실행 중인 작업 표를 앱 전체가 함께 쓰도록 FileOperationOrchestrator를 싱글톤으로 제공한다. */
@Module
@InstallIn(SingletonComponent::class)
object OperationModule {

    @Provides
    @Singleton
    fun provideFileOperationOrchestrator(
        notifier: FileOperationNotifier,
        progressReporter: ProgressReporter,
        notices: OperationNoticeReporter,
        protection: OperationProtection,
        questions: OperationQuestionAsker,
        @ApplicationScope appScope: CoroutineScope
    ) = FileOperationOrchestrator(notifier, progressReporter, notices, protection, questions, appScope)
}
