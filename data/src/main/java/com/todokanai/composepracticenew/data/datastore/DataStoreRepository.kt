package com.todokanai.composepracticenew.data.datastore

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.composepracticenew.repository.LastConnectionRepository
import com.todokanai.composepracticenew.repository.SortPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences DataStore 기반으로 정렬 기준과 마지막 접속 서버 id를 저장하는 구현체. 키·값 매핑만 담당하고 실행 위치·실패 정책은 [PreferencesStore]에 위임한다.
 * 소비자는 [SortPreferenceRepository] / [LastConnectionRepository] 인터페이스로만 의존한다.
 * @param store 재시도·실패 정책이 적용된 Preferences DataStore 접근 래퍼
 */
@Singleton
class DataStoreRepository @Inject constructor(
    private val store: PreferencesStore
) : SortPreferenceRepository, LastConnectionRepository {
    companion object {
        val DATASTORE_SORT_BY = stringPreferencesKey("datastore_sort_by")
        val DATASTORE_LAST_REMOTE_ID = longPreferencesKey("datastore_last_remote_id")
    }

    override fun saveSortBy(value: SortMode) = store.launchEdit {
        it[DATASTORE_SORT_BY] = value.id
    }

    override val sortBy: Flow<SortMode> = store.data.map {
        SortMode.fromId(it[DATASTORE_SORT_BY])
    }.distinctUntilChanged() // lastRemoteId 등 다른 키 저장 시에도 data가 재방출되므로, 정렬 모드가 같으면 하류(목록 재조회)로 전달하지 않음

    override fun setLastRemoteId(id: Long?) = store.launchEdit {
        if (id != null) it[DATASTORE_LAST_REMOTE_ID] = id
        else it.remove(DATASTORE_LAST_REMOTE_ID)
    }

    override suspend fun lastRemoteId(): Long? =
        store.data.first()[DATASTORE_LAST_REMOTE_ID]
}
