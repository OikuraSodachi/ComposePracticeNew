package com.todokanai.composepracticenew.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import java.io.IOException

/**
 * 테스트용 Preferences DataStore. 앞선 [failingReads]회의 수집은 [readError]를 던지고, 이후에는 [state]를 방출한다.
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

    override val data: Flow<Preferences> = flow {
        if (failingReads > 0) {
            failingReads--
            throw readError
        }
        state.collect { emit(it) }
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        writeDelays.removeFirstOrNull()?.let { delay(it) }
        writeError?.let { throw it }
        if (failingWrites > 0) {
            failingWrites--
            throw IOException("write")
        }
        return transform(state.value).also { state.value = it }
    }
}

/**
 * [PreferencesStore]의 소비자 코루틴을 돌릴 스코프. 소비자는 끝나지 않으므로 runTest의 자식이 아닌 별도 스코프로 두되,
 * 같은 스케줄러를 써서 advanceUntilIdle 등으로 함께 진행되게 한다. 테스트 종료 시 cancel해야 한다.
 */
internal fun TestScope.storeScope(): CoroutineScope = CoroutineScope(StandardTestDispatcher(testScheduler))
