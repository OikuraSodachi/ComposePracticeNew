package com.todokanai.composepracticenew.data.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.todokanai.composepracticenew.di.ApplicationScope
import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.composepracticenew.repository.LastConnectionRepository
import com.todokanai.composepracticenew.repository.SortPreferenceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences DataStore 기반으로 정렬 기준과 마지막 접속 서버 id를 저장하는 구현체.
 * 소비자는 [SortPreferenceRepository] / [LastConnectionRepository] 인터페이스로만 의존한다.
 * @param context DataStore 파일 접근에 사용하는 애플리케이션 Context
 */
@Singleton
class DataStoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope
) : SortPreferenceRepository, LastConnectionRepository {
    companion object {
        private const val TAG = "DataStoreRepository"
        val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mydatastore")
        val DATASTORE_SORT_BY = stringPreferencesKey("datastore_sort_by")
        val DATASTORE_LAST_REMOTE_ID = longPreferencesKey("datastore_last_remote_id")
    }

    override fun saveSortBy(value: SortMode) {
        appScope.launch {
            try {
                context.dataStore.edit {
                    it[DATASTORE_SORT_BY] = value.id
                }
            } catch (e: IOException) {
                Log.w(TAG, "saveSortBy 실패", e)
            }
        }
    }

    override val sortBy: Flow<SortMode> = context.dataStore.data.map {
        SortMode.fromId(it[DATASTORE_SORT_BY])
    }

    override fun setLastRemoteId(id: Long?) {
        appScope.launch {
            try {
                context.dataStore.edit {
                    if (id != null) it[DATASTORE_LAST_REMOTE_ID] = id
                    else it.remove(DATASTORE_LAST_REMOTE_ID)
                }
            } catch (e: IOException) {
                Log.w(TAG, "setLastRemoteId 실패", e)
            }
        }
    }

    override suspend fun lastRemoteId(): Long? =
        context.dataStore.data.first()[DATASTORE_LAST_REMOTE_ID]
}
