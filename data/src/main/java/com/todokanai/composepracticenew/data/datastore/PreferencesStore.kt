package com.todokanai.composepracticenew.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.todokanai.composepracticenew.di.ApplicationScope
import com.todokanai.composepracticenew.model.PreferenceFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences DataStore 접근의 실행 위치·재시도·실패 정책을 한 곳에서 정의하는 래퍼.
 * 파일 손상은 [corruptionReset]의 핸들러가 빈 설정으로 바꾸며, 교체가 쓰였음은 이 클래스의 읽기·편집 성공으로 확인해 알린다. 이 클래스는 I/O 실패를 처리한다 —
 * [data]의 읽기 실패는 기본값으로 대신하며 다시 읽고, 저장 실패는 이전 값을 남긴다. 어느 쪽이든 결과를 받는 호출부가 없으므로 [failures]로 알려, 실패가 기본값이나 "바뀌지 않음"으로만 보이지 않게 한다.
 * [readOnce]의 읽기 실패는 null로 호출부에 돌려준다.
 * @param dataStore 앱 전역에서 단일 인스턴스로 제공되는 Preferences DataStore
 * @param appScope 설정을 읽는 공유 수집과 저장 코루틴을 실행할 애플리케이션 수명 CoroutineScope
 * @param failures 읽기·저장 실패를 화면에 알리기 위해 넣는 대기열
 * @param corruptionReset 손상 파일 교체를 읽기·편집 성공 뒤에 알리는 곳
 */
@Singleton
class PreferencesStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val appScope: CoroutineScope,
    private val failures: PreferenceFailureChannel,
    private val corruptionReset: CorruptionReset
) {
    companion object {
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
        private const val MAX_BACKOFF_SHIFT = 5
    }

    /**
     * [readOnce]·편집이 성공한 횟수. 읽기 실패 뒤 재시도를 기다리는 [data]의 공유 수집은 이 값이 늘면 타이머를 기다리지 않고 다시 읽는다 —
     * 성공했다면 DataStore가 이미 회복해 있어, 타이머가 끝날 때까지 이전 값을 보여 줄 이유가 없다.
     * [data]보다 먼저 선언한다 — 공유 수집이 생성자에서 시작해 이 값을 읽는다.
     */
    private val accessSuccesses = MutableStateFlow(0L)

    /**
     * 설정 값의 Flow. 앱 수명 동안 DataStore를 한 곳에서 수집해 모든 소비자가 나눠 받으며, 새 소비자는 마지막 값을 바로 받는다.
     * DataStore의 읽기 실패는 소비자마다가 아니라 저장소 하나의 상태(캐시)이므로, 실패 처리도 이 수집 하나에서 한다 — 소비자 수와 관계없이 실패를 실패 구간마다 한 번 알린다.
     * 읽기 IOException 시 실패 구간의 첫 실패에만 빈 Preferences(= 기본값)를 방출하고 [PreferenceFailure.READ]를 알린 뒤 다시 수집해, 회복하면 실제 값을 계속 방출한다 —
     * 다시 수집하지 않으면 실패한 수집이 끝나 이후 저장이 반영되지 않는다. 다시 수집은 지수 백오프 타이머가 끝나거나, 그 전에 [readOnce]·편집이 성공하면 바로 한다.
     * 실패 구간은 마지막 읽기 성공부터 센다. 회복한 뒤 다시 실패하면(저장 실패 뒤의 다시 읽기가 실패하면 DataStore는 열린 수집도 끝낸다) 첫 실패와 같이 처리하고 대기도 처음부터 한다.
     * IOException이 아닌 예외는 구현 오류로 보고 공유 수집을 끝내 [appScope]로 전파한다(kotlinx `shareIn`: 상류 예외는 구독자가 아니라 공유를 실행한 스코프가 처리한다).
     */
    val data: Flow<Preferences> = flow {
        /** 이 수집에서 마지막 읽기 성공 이후 이어진 실패 횟수. retryWhen의 attempt는 수집 전체에서 세어 회복해도 0으로 돌아가지 않으므로 따로 센다. */
        var failuresSinceSuccess = 0L
        /** 이 수집이 마지막으로 확인한 [accessSuccesses]. 이보다 늘었으면 그 사이 저장소가 회복한 것이다. 기다림에서 깰 때마다 갱신해 같은 성공으로 거듭 깨지 않는다. */
        var seenSuccesses = accessSuccesses.value
        emitAll(
            dataStore.data
                .onEach {
                    failuresSinceSuccess = 0L
                    seenSuccesses = accessSuccesses.value
                    corruptionReset.confirmIfReplaced()
                }
                .retryWhen { cause, _ ->
                    if (cause !is IOException) return@retryWhen false
                    if (failuresSinceSuccess == 0L) {
                        emit(emptyPreferences())
                        failures.report(PreferenceFailure.READ)
                    }
                    awaitRetry(retryDelayMillis(failuresSinceSuccess++), seenSuccesses)
                    seenSuccesses = accessSuccesses.value
                    true
                }
        )
    }.shareIn(appScope, SharingStarted.Eagerly, replay = 1)

    /**
     * 지금의 설정 값을 한 번 읽는다. [data]와 달리 읽지 못하면 기본값으로 대신하지 않는다 — 저장된 값이 없는 것과 읽지 못한 것을 구별해야 하는 호출부를 위해.
     * 공유된 [data]의 마지막 값이 아니라 DataStore를 직접 읽는다 — [data]가 재시도를 기다리는 중이어도 지금 읽을 수 있는지를 돌려주기 위해.
     * 읽기 실패는 결과로 받는 호출부가 사용자에게 알리므로 [failures]로 알리지 않는다 — [PreferenceFailure.READ]는 기본값으로 대신해 결과로 드러나지 않는 [data]의 실패만 뜻한다.
     * @return 설정 값. 읽기 IOException이면 null
     */
    suspend fun readOnce(): Preferences? = try {
        dataStore.data.first().also { onAccessSucceeded() }
    } catch (_: IOException) {
        null
    }

    /**
     * 반영을 기다리는 편집 대기열. [launchEdit]가 호출 스레드에서 바로 넣고, [appScope]의 소비자 코루틴 하나가 넣은 순서대로 꺼내 반영한다.
     * 용량 제한이 없어 넣기가 일시 중단되거나 실패하지 않는다.
     */
    private val pendingEdits = Channel<(MutablePreferences) -> Unit>(Channel.UNLIMITED)

    init {
        // 소비자는 하나뿐이고 앞선 edit이 끝나야 다음을 꺼내므로, 반영 순서가 대기열에 넣은 순서(= launchEdit 호출 순서)와 같다
        appScope.launch {
            for (transform in pendingEdits) applyEdit(transform)
        }
    }

    /**
     * [transform]을 DataStore에 비동기로 반영한다(fire-and-forget). 반영 순서는 호출 순서와 같다.
     * 호출 스레드에서 즉시 대기열에 넣으므로, 먼저 호출한 편집이 나중 편집을 덮어쓰지 않는다.
     * 조건에 따라 값을 바꾸는 편집은 [transform] 안에서 현재 값을 확인해야 한다 — 그래야 직전 편집이 반영된 값을 기준으로 판단한다.
     * 저장 실패(IOException) 시 이 편집만 반영되지 않고 이전 값이 남으며, [PreferenceFailure.SAVE]를 알린다.
     * @param transform 저장할 값을 MutablePreferences에 기록하는 편집 람다
     */
    fun launchEdit(transform: (MutablePreferences) -> Unit) {
        pendingEdits.trySend(transform)
    }

    /**
     * [transform]을 한 번의 DataStore edit으로 반영한다. IOException이면 실패를 알리고 돌아와, 소비자 코루틴이 다음 편집을 계속 처리하게 한다.
     * @param transform 반영할 편집 람다
     */
    private suspend fun applyEdit(transform: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit { transform(it) }
            onAccessSucceeded()
        } catch (_: IOException) {
            failures.report(PreferenceFailure.SAVE)
        }
    }

    /** [readOnce]·편집이 성공했을 때의 처리. 재시도를 기다리는 [data]의 수집을 깨우고, 손상 파일을 교체한 뒤의 첫 성공이면 초기화를 알린다. */
    private fun onAccessSucceeded() {
        accessSuccesses.update { it + 1 }
        corruptionReset.confirmIfReplaced()
    }

    /**
     * [delayMillis]가 지나거나, [seen] 이후 [readOnce]·편집이 성공할 때까지 기다린다.
     * @param delayMillis 성공이 없을 때 기다릴 최대 시간
     * @param seen 기다리기 전에 확인한 [accessSuccesses]
     */
    private suspend fun awaitRetry(delayMillis: Long, seen: Long) {
        withTimeoutOrNull(delayMillis) { accessSuccesses.first { it != seen } }
    }

    /** [attempt]번째 재시도 전 대기 시간. 1초부터 두 배씩 늘리되 최대 30초로 제한한다. */
    private fun retryDelayMillis(attempt: Long): Long =
        minOf(INITIAL_RETRY_DELAY_MS shl attempt.coerceAtMost(MAX_BACKOFF_SHIFT.toLong()).toInt(), MAX_RETRY_DELAY_MS)
}
