package com.todokanai.composepracticenew.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.todokanai.composepracticenew.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences DataStore 접근의 실행 위치·재시도·실패 정책을 한 곳에서 정의하는 래퍼.
 * 파일 손상은 DataStore 생성 시의 corruptionHandler가 복구하고, 이 클래스는 일시적 I/O 실패를 처리한다.
 * @param dataStore 앱 전역에서 단일 인스턴스로 제공되는 Preferences DataStore
 * @param appScope 저장 코루틴을 실행할 애플리케이션 수명 CoroutineScope
 */
@Singleton
class PreferencesStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val appScope: CoroutineScope
) {
    companion object {
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
        private const val MAX_BACKOFF_SHIFT = 5
    }

    /**
     * 읽기 IOException 시 첫 실패에만 빈 Preferences(= 기본값)를 방출하고, 지수 백오프로 재수집해 회복 후 실제 값을 계속 방출하는 Flow.
     * IOException이 아닌 예외는 그대로 전파한다.
     */
    val data: Flow<Preferences> = dataStore.data.retryWhen { cause, attempt ->
        if (cause !is IOException) return@retryWhen false
        if (attempt == 0L) emit(emptyPreferences())
        delay(retryDelayMillis(attempt))
        true
    }

    /**
     * [transform]을 appScope에서 비동기로 DataStore에 반영한다(fire-and-forget).
     * 저장 실패(IOException) 시 기존 값이 유지되므로 앱을 중단시키지 않고 무시한다.
     * @param transform 저장할 값을 MutablePreferences에 기록하는 편집 람다
     */
    fun launchEdit(transform: (MutablePreferences) -> Unit) {
        appScope.launch {
            try {
                dataStore.edit(transform)
            } catch (_: IOException) {
                // 저장 실패 시 기존 값이 유지되므로 무시한다.
            }
        }
    }

    /** [attempt]번째 재시도 전 대기 시간. 1초부터 두 배씩 늘리되 최대 30초로 제한한다. */
    private fun retryDelayMillis(attempt: Long): Long =
        minOf(INITIAL_RETRY_DELAY_MS shl attempt.coerceAtMost(MAX_BACKOFF_SHIFT.toLong()).toInt(), MAX_RETRY_DELAY_MS)
}
