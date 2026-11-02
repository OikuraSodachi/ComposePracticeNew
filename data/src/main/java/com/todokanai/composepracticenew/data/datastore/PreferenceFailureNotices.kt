package com.todokanai.composepracticenew.data.datastore

import com.todokanai.composepracticenew.model.PreferenceFailure
import com.todokanai.composepracticenew.model.PreferenceFailureNotice
import com.todokanai.composepracticenew.repository.PreferenceFailureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 설정 저장소([PreferencesStore], [CorruptionReset])가 실패를 넣고, 화면이 꺼내 알리는 알림 목록.
 * 사건을 흘려보내는 대기열이 아니라 상태로 둔다 — 아직 유효한지(읽기 실패라면 지금도 기본값으로 보이는지)를 알릴 때까지 반영하고,
 * 화면이 여럿이어도 각 알림을 꺼낸 한 곳에서만 알리도록.
 */
@Singleton
class PreferenceFailureNotices @Inject constructor() : PreferenceFailureRepository {

    /** 다음 알림에 붙일 id. 알림마다 다르게 해 꺼낼 때 어느 알림인지 가린다. */
    private val nextId = AtomicLong()

    /** 알리기를 기다리는 실패. 실패는 설정 읽기 실패 구간·저장 호출마다 하나라 사용자 동작 수를 넘지 않는다. */
    private val _pending = MutableStateFlow<List<PreferenceFailureNotice>>(emptyList())

    override val pending: StateFlow<List<PreferenceFailureNotice>> = _pending.asStateFlow()

    /**
     * 실패 [failure]를 알림 목록에 넣는다. 호출 스레드에서 바로 반환한다.
     * @param failure 일어난 실패
     */
    fun report(failure: PreferenceFailure) {
        val notice = PreferenceFailureNotice(nextId.getAndIncrement(), failure)
        _pending.update { it + notice }
    }

    /** 설정을 다시 읽었을 때 부른다. 아직 알리지 않은 [PreferenceFailure.READ]를 뺀다 — 이제 저장된 값이 보이므로 "기본값으로 보인다"는 알림은 사실이 아니다. */
    fun readRecovered() {
        _pending.update { notices -> notices.filterNot { it.failure == PreferenceFailure.READ } }
    }

    override fun claim(id: Long): Boolean {
        var claimed = false
        _pending.update { notices ->
            notices.filterNot { it.id == id }.also { claimed = it.size != notices.size }
        }
        return claimed
    }
}
