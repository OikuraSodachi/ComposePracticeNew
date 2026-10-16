package com.todokanai.composepracticenew.tools

import com.todokanai.composepracticenew.service.ConnectionServiceController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 원격 연결 서비스를 중단하고 앱 종료 이벤트를 발행하는 계약. */
interface AppExitHandler {
    /** 앱을 종료하라는 이벤트. 화면 호스트가 수집해 Activity를 종료한다. */
    val exitEvent: Flow<Unit>

    /**
     * 원격 연결 서비스를 중단하고 앱 종료 이벤트를 발행한다.
     * @return Unit
     */
    fun execute()
}

/**
 * [AppExitHandler]의 기본 구현.
 * @param connectionServiceController 원격 연결 ForegroundService 시작·종료를 담당하는 컨트롤러
 */
@Singleton
class AppExitHandlerImpl @Inject constructor(
    private val connectionServiceController: ConnectionServiceController
) : AppExitHandler {
    private val _exitChannel = Channel<Unit>(Channel.CONFLATED)

    override val exitEvent: Flow<Unit> = _exitChannel.receiveAsFlow()

    override fun execute() {
        connectionServiceController.stop()
        _exitChannel.trySend(Unit)
    }
}
