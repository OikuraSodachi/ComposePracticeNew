package com.todokanai.composepracticenew.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.io.IOException

/**
 * 테스트용 Preferences DataStore. 앞선 [failingReads]회의 수집은 [readError]를 던지고, 이후에는 [state]를 방출한다. [failActiveCollections]로 열린 수집도 실패시킬 수 있다.
 * @param initial 초기 저장값
 * @param failingReads 실패시킬 수집 횟수
 * @param readError 읽기 실패 시 던질 예외
 * @param writeError null이 아니면 updateData가 이 예외를 던진다
 * @param failingWrites 앞선 이 횟수만큼의 updateData는 IOException을 던진다
 * @param writeDelays 앞에서부터 하나씩 꺼내, 해당 updateData 호출이 값을 반영하기 전에 기다릴 시간(ms). 비면 기다리지 않는다 —
 *   편집이 DataStore에 도착하는 시점이 호출마다 달라지는 상황을 재현한다
 */
internal class FakePreferencesDataStore(
    initial: Preferences,
    private var failingReads: Int = 0,
    private val readError: Throwable = IOException("read"),
    private val writeError: Throwable? = null,
    private var failingWrites: Int = 0,
    private val writeDelays: MutableList<Long> = mutableListOf()
) : DataStore<Preferences> {
    /** 현재 저장값. */
    val state = MutableStateFlow(initial)
    /** updateData가 호출된 횟수(실패한 호출 포함). */
    var writeCount = 0
        private set

    /**
     * 다음 [count]회의 수집을 다시 [readError]로 실패시킨다. 읽기가 회복된 뒤 다시 실패하는 상황을 재현한다.
     * @param count 실패시킬 수집 횟수
     */
    fun failNextReads(count: Int) {
        failingReads = count
    }

    /** [failActiveCollections]가 불린 횟수. 수집은 시작할 때의 값과 달라지면 [readError]로 끝난다. */
    private val activeCollectionFailures = MutableStateFlow(0)

    /**
     * 이미 값을 방출하고 있는 수집을 모두 [readError]로 끝내고, 다음 [nextReads]회의 수집도 실패시킨다.
     * DataStore에서 저장 실패 뒤의 다시 읽기가 실패하면, 그 실패가 캐시를 거쳐 열려 있던 수집까지 끝내는 상황을 재현한다.
     * @param nextReads 이어서 실패시킬 새 수집 횟수
     */
    fun failActiveCollections(nextReads: Int) {
        failingReads = nextReads
        activeCollectionFailures.value++
    }

    override val data: Flow<Preferences> = flow {
        if (failingReads > 0) {
            failingReads--
            throw readError
        }
        val failuresAtStart = activeCollectionFailures.value
        state.combine(activeCollectionFailures) { value, failures ->
            if (failures != failuresAtStart) throw readError
            value
        }.collect { emit(it) }
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        writeCount++
        writeDelays.removeFirstOrNull()?.let { delay(it) }
        writeError?.let { throw it }
        if (failingWrites > 0) {
            failingWrites--
            throw IOException("write")
        }
        return transform(state.value).also { state.value = it }
    }
}
