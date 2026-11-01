package com.todokanai.composepracticenew.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.todokanai.composepracticenew.data.datastore.CorruptionReset
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
        corruptionReset: CorruptionReset
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = corruptionReset.handler,
            produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) }
        )
}
