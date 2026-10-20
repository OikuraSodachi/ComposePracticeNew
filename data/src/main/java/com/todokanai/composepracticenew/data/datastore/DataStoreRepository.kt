package com.todokanai.composepracticenew.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
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
        val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mydatastore")
        val DATASTORE_SORT_BY = stringPreferencesKey("datastore_sort_by")
        val DATASTORE_LAST_REMOTE_ID = longPreferencesKey("datastore_last_remote_id")
    }

    /** 읽기 중 IOException이 나면 빈 Preferences(= 기본값)를 방출하고, 그 외 예외는 그대로 전파하는 data Flow. */
    private val safeData: Flow<Preferences> = context.dataStore.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }

    /**
     * [transform]을 appScope에서 DataStore에 반영한다. 저장 실행 위치와 실패 정책을 이 한 곳에서만 결정한다.
     * @param transform 저장할 값을 MutablePreferences에 기록하는 편집 람다
     */
    private fun update(transform: (MutablePreferences) -> Unit) {
        appScope.launch {
            try {
                context.dataStore.edit(transform)
            } catch (_: IOException) {
                // 저장 실패 시 기존 값이 유지되므로 앱을 중단시키지 않고 무시한다.
            }
        }
    }

    override fun saveSortBy(value: SortMode) = update {
        it[DATASTORE_SORT_BY] = value.id
    }

    override val sortBy: Flow<SortMode> = safeData.map {
        SortMode.fromId(it[DATASTORE_SORT_BY])
    }.distinctUntilChanged() // lastRemoteId 등 다른 키 저장 시에도 data가 재방출되므로, 정렬 모드가 같으면 하류(목록 재조회)로 전달하지 않음

    override fun setLastRemoteId(id: Long?) = update {
        if (id != null) it[DATASTORE_LAST_REMOTE_ID] = id
        else it.remove(DATASTORE_LAST_REMOTE_ID)
    }

    override suspend fun lastRemoteId(): Long? =
        safeData.first()[DATASTORE_LAST_REMOTE_ID]
}
