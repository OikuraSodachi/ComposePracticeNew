package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import com.todokanai.composepracticenew.ui.model.AppNotice
import com.todokanai.composepracticenew.ui.model.OperationNotice
import com.todokanai.composepracticenew.usecase.ConnectFailureAlertUseCase
import com.todokanai.composepracticenew.usecase.ConnectionLossNoticeUseCase
import com.todokanai.composepracticenew.usecase.OperationNoticeUseCase
import com.todokanai.composepracticenew.usecase.PreferenceFailureUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity 스코프에서 앱 알림함 모두의 알림을 화면에 넘기는 ViewModel(#448).
 * @param operationNoticeUseCase 파일 작업의 실패·거부·완료 메시지를 알려주는 UseCase
 * @param connectionLossNoticeUseCase 원격 세션의 예기치 않은 끊김을 알려주는 UseCase
 * @param preferenceFailureUseCase 설정 저장소의 읽기·저장 실패와 손상 초기화를 알려주는 UseCase
 * @param connectFailureAlertUseCase 다시 시도하면 될 수 있는 원격 연결 실패를 알려주는 UseCase
 */
@HiltViewModel
class AppNoticeViewModel @Inject constructor(
    private val operationNoticeUseCase: OperationNoticeUseCase,
    private val connectionLossNoticeUseCase: ConnectionLossNoticeUseCase,
    private val preferenceFailureUseCase: PreferenceFailureUseCase,
    private val connectFailureAlertUseCase: ConnectFailureAlertUseCase
) : ViewModel() {

    /**
     * 앱 알림함 모두(설정 저장소 실패, 파일 작업의 실패·거부·완료, 원격 세션 끊김, 원격 연결 실패)의 알림을 [show]로 알린다. 어느 화면에 있든 알리기 위해 화면 호스트가 화면이 보이는 동안 부른다. 취소될 때까지 돌아오지 않는다.
     * 알림함을 이 함수 한곳에서 모두 꺼내므로, 알림함을 더할 때 화면에서 꺼내는 곳을 따로 만들지 않는다(#449). 알림함마다 함께 꺼내며, 한 알림함 안에서는 앞 알림을 다 보여 준 뒤에 다음을 꺼낸다.
     * - 보이는 화면이 없을 때 생긴 알림과 보여 주는 도중 화면이 사라진 알림도 부르면 받는다.
     * - 설정 읽기 실패는 그 사이 회복했으면 받지 않는다.
     * - 끊김·연결 실패는 그 사이 새 연결(시도)을 시작했으면 받지 않고, 보여 주는 도중 새 연결(시도)을 시작하면 [show]를 취소한다.
     * - 끊김에 따른 정리(서비스 중지·전송 상태 정리·원격 화면 이탈)는 각자 연결 상태를 보고 하므로, 끊김 알림은 알리는 데만 쓴다.
     * @param show 알림을 사용자에게 보여 주고, 다 보여 줄 때까지 돌아오지 않는 함수. 여러 알림함에서 함께 불리며, 취소되면 보여 주던 것을 거둬야 한다
     */
    suspend fun showNotices(show: suspend (AppNotice) -> Unit): Nothing = coroutineScope {
        launch { preferenceFailureUseCase.deliver { show(AppNotice.PreferenceFailed(it)) } }
        launch { operationNoticeUseCase.deliver { show(AppNotice.Operation(OperationNotice.from(it))) } }
        launch { connectionLossNoticeUseCase.deliver { show(AppNotice.ConnectionLost) } }
        connectFailureAlertUseCase.deliver { show(AppNotice.ConnectFailed(it)) }
    }
}
