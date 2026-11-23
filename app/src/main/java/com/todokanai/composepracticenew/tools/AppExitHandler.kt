package com.todokanai.composepracticenew.tools

import com.todokanai.composepracticenew.di.ApplicationScope
import com.todokanai.composepracticenew.usecase.RemoteDisconnectUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** 원격 연결을 종료하고 앱 종료를 요청하는 계약. */
interface AppExitHandler {
    /** 앱을 종료하라는 요청이 있으면 Unit. 없으면 null. 화면 호스트가 받아 Activity를 종료한다. */
    val exitRequest: StateFlow<Unit?>

    /**
     * 원격 연결을 종료하고 앱 종료를 요청한다.
     * @return Unit
     */
    fun execute()

    /**
     * 앱 종료 요청을 처리했다고 알린다 — 프로세스가 남아 Activity가 다시 열려도 같은 요청으로 다시 종료하지 않도록.
     * @return Unit
     */
    fun onExitHandled()
}

/**
 * [AppExitHandler]의 기본 구현. 연결 유지 서비스는 연결이 종료된 상태를 보고 멈추므로 여기서 직접 멈추지 않는다.
 * @param disconnectUseCase 원격 연결을 종료하는 UseCase
 * @param appScope 화면이 닫힌 뒤에도 연결 종료를 끝까지 실행하는 앱 수명 스코프
 */
@Singleton
class AppExitHandlerImpl @Inject constructor(
    private val disconnectUseCase: RemoteDisconnectUseCase,
    @ApplicationScope private val appScope: CoroutineScope
) : AppExitHandler {
    /** [exitRequest]의 원본. 화면 호스트가 처리했다고 알릴 때까지 보관하며(#534), 처리 전에 여러 번 요청해도 한 번의 종료로 합친다. */
    private val pendingExit = PendingRequest<Unit>()

    override val exitRequest: StateFlow<Unit?> = pendingExit.request

    override fun execute() {
        appScope.launch { disconnectUseCase.disconnect() }
        pendingExit.post(Unit)
    }

    override fun onExitHandled() = pendingExit.consume(Unit)
}
