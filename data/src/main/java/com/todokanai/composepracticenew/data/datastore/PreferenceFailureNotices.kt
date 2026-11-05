package com.todokanai.composepracticenew.data.datastore

import com.todokanai.composepracticenew.model.PendingNotice
import com.todokanai.composepracticenew.model.PreferenceFailure
import com.todokanai.composepracticenew.repository.NoticeBox
import com.todokanai.composepracticenew.repository.PreferenceFailureRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 설정 저장소([PreferencesStore], [CorruptionReset])가 실패를 넣고, 화면이 꺼내 알리는 알림함.
 * 알림이 아직 유효한지(읽기 실패라면 지금도 기본값으로 보이는지)를 알릴 때까지 반영한다.
 */
@Singleton
class PreferenceFailureNotices @Inject constructor() : PreferenceFailureRepository {

    /** 실패를 보관하고 꺼내 주는 알림함. 실패는 설정 읽기 실패 구간·저장 호출마다 하나라 사용자 동작 수를 넘지 않는다. */
    private val box = NoticeBox<PreferenceFailure>()

    override val pending: StateFlow<List<PendingNotice<PreferenceFailure>>> get() = box.pending

    /**
     * 실패 [failure]를 알림 목록에 넣는다. 호출 스레드에서 바로 반환한다.
     * @param failure 일어난 실패
     */
    fun report(failure: PreferenceFailure) = box.report(failure)

    /** 설정을 다시 읽었을 때 부른다. 아직 다 알리지 않은 [PreferenceFailure.READ]를 뺀다 — 이제 저장된 값이 보이므로 "기본값으로 보인다"는 알림은 사실이 아니다. */
    fun readRecovered() = box.discard { it == PreferenceFailure.READ }

    override fun claim(id: Long): Boolean = box.claim(id)

    override fun complete(id: Long) = box.complete(id)

    override fun release(id: Long) = box.release(id)
}
