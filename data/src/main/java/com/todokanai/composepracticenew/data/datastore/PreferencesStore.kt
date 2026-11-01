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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences DataStore 접근의 실행 위치·재시도·실패 정책을 한 곳에서 정의하는 래퍼.
 * 파일 손상은 DataStore 생성 시의 corruptionHandler가 빈 설정으로 바꾸고 알린다. 이 클래스는 I/O 실패를 처리한다 —
 * 읽기 실패는 기본값으로 대신하며 다시 읽고, 저장 실패는 이전 값을 남긴다. 어느 쪽이든 [failures]로 알려, 실패가 기본값이나 "바뀌지 않음"으로만 보이지 않게 한다.
 * @param dataStore 앱 전역에서 단일 인스턴스로 제공되는 Preferences DataStore
 * @param appScope 저장 코루틴을 실행할 애플리케이션 수명 CoroutineScope
 * @param failures 읽기·저장 실패를 화면에 알리기 위해 넣는 대기열
 */
@Singleton
class PreferencesStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val appScope: CoroutineScope,
    private val failures: PreferenceFailureChannel
) {
    companion object {
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
        private const val MAX_BACKOFF_SHIFT = 5
    }

    /** 지금 설정 읽기가 실패해 있는지. 여러 곳이 [data]를 수집해도 읽기 실패를 실패가 이어지는 동안 한 번만 알리기 위해 둔다. 읽기에 성공하면 false로 돌아간다. */
    private val readFailing = AtomicBoolean(false)

    /**
     * 설정 값의 Flow. 읽기 IOException 시 첫 실패에만 빈 Preferences(= 기본값)를 방출하고 [PreferenceFailure.READ]를 알린 뒤,
     * 지수 백오프로 다시 수집해 회복하면 실제 값을 계속 방출한다 — 다시 수집하지 않으면 실패한 수집이 끝나 이후 저장이 반영되지 않는다.
     * IOException이 아닌 예외는 그대로 전파한다.
     */
    val data: Flow<Preferences> = dataStore.data
        .onEach { readFailing.set(false) }
        .retryWhen { cause, attempt ->
            if (cause !is IOException) return@retryWhen false
            if (attempt == 0L) {
                emit(emptyPreferences())
                reportReadFailure()
            }
            delay(retryDelayMillis(attempt))
            true
        }

    /**
     * 지금의 설정 값을 한 번 읽는다. [data]와 달리 읽지 못하면 기본값으로 대신하지 않는다 — 저장된 값이 없는 것과 읽지 못한 것을 구별해야 하는 호출부를 위해.
     * @return 설정 값. 읽기 IOException이면 [PreferenceFailure.READ]를 알리고 null
     */
    suspend fun readOnce(): Preferences? = try {
        dataStore.data.first().also { readFailing.set(false) }
    } catch (_: IOException) {
        reportReadFailure()
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
        } catch (_: IOException) {
            failures.report(PreferenceFailure.SAVE)
        }
    }

    /** 읽기 실패를 알린다. 이미 실패가 이어지는 중이면(다른 수집이 알렸으면) 다시 알리지 않는다. */
    private fun reportReadFailure() {
        if (readFailing.compareAndSet(false, true)) failures.report(PreferenceFailure.READ)
    }

    /** [attempt]번째 재시도 전 대기 시간. 1초부터 두 배씩 늘리되 최대 30초로 제한한다. */
    private fun retryDelayMillis(attempt: Long): Long =
        minOf(INITIAL_RETRY_DELAY_MS shl attempt.coerceAtMost(MAX_BACKOFF_SHIFT.toLong()).toInt(), MAX_RETRY_DELAY_MS)
}
