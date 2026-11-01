package com.todokanai.composepracticenew.data.datastore

import com.todokanai.composepracticenew.model.PreferenceFailure
import com.todokanai.composepracticenew.repository.PreferenceFailureRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 설정 저장소([PreferencesStore], [CorruptionReset])가 실패를 넣고, 화면 호스트가 꺼내 알리는 대기열.
 * 앱을 열자마자(화면이 수집하기 전) 일어난 실패도 잃지 않도록 수집할 때까지 보관한다.
 */
@Singleton
class PreferenceFailureChannel @Inject constructor() : PreferenceFailureRepository {

    /** 알리기를 기다리는 실패. 용량 제한이 없어 넣기가 실패하지 않는다 — 실패는 설정 읽기 실패 묶음·저장 호출마다 하나라 사용자 동작 수를 넘지 않는다. */
    private val pending = Channel<PreferenceFailure>(Channel.UNLIMITED)

    override val failures: Flow<PreferenceFailure> = pending.receiveAsFlow()

    /**
     * 실패 [failure]를 알릴 대기열에 넣는다. 호출 스레드에서 바로 반환한다.
     * @param failure 일어난 실패
     */
    fun report(failure: PreferenceFailure) {
        pending.trySend(failure)
    }
}
