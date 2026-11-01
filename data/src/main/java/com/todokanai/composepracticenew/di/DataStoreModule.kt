package com.todokanai.composepracticenew.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.todokanai.composepracticenew.data.datastore.PreferenceFailureChannel
import com.todokanai.composepracticenew.model.PreferenceFailure
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 앱 전역 Preferences DataStore를 단일 인스턴스로 제공하는 Hilt 모듈. */
@InstallIn(SingletonComponent::class)
@Module
object DataStoreModule {

    /** 기존 `preferencesDataStore(name = "mydatastore")`와 같은 파일을 사용해 저장된 설정을 그대로 읽는다. */
    private const val DATASTORE_NAME = "mydatastore"

    @Singleton
    @Provides
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        failures: PreferenceFailureChannel
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = resettingCorruptionHandler(failures),
            produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) }
        )

    /**
     * 손상된 설정 파일을 빈 Preferences로 교체해 읽기·쓰기를 복구하는 핸들러를 만든다 (기본 핸들러는 예외를 재전파만 해 영구 실패).
     * 손상된 내용은 이미 읽을 수 없어 교체 말고는 복구할 수 없으므로, 설정이 기본값으로 돌아갔음을 [PreferenceFailure.RESET]으로 알린다.
     * @param failures 초기화를 알릴 대기열
     * @return DataStore에 넘길 손상 핸들러
     */
    internal fun resettingCorruptionHandler(failures: PreferenceFailureChannel): ReplaceFileCorruptionHandler<Preferences> =
        ReplaceFileCorruptionHandler {
            failures.report(PreferenceFailure.RESET)
            emptyPreferences()
        }
}
