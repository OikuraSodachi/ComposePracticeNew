package com.todokanai.composepracticenew.tools

import com.todokanai.composepracticenew.di.ApplicationScope
import com.todokanai.composepracticenew.usecase.RemoteConnectionStateUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** 원격 연결을 종료하고 앱 종료 이벤트를 발행하는 계약. */
interface AppExitHandler {
    /** 앱을 종료하라는 이벤트. 화면 호스트가 수집해 Activity를 종료한다. */
    val exitEvent: Flow<Unit>

    /**
     * 원격 연결을 종료하고 앱 종료 이벤트를 발행한다.
     * @return Unit
     */
    fun execute()
}

/**
 * [AppExitHandler]의 기본 구현. 연결 유지 서비스는 연결이 종료된 상태를 보고 멈추므로 여기서 직접 멈추지 않는다.
 * @param connectionStateUseCase 원격 연결을 종료하는 UseCase
 * @param appScope 화면이 닫힌 뒤에도 연결 종료를 끝까지 실행하는 앱 수명 스코프
 */
@Singleton
class AppExitHandlerImpl @Inject constructor(
    private val connectionStateUseCase: RemoteConnectionStateUseCase,
    @ApplicationScope private val appScope: CoroutineScope
) : AppExitHandler {
    private val _exitChannel = Channel<Unit>(Channel.CONFLATED)

    override val exitEvent: Flow<Unit> = _exitChannel.receiveAsFlow()

    override fun execute() {
        appScope.launch { connectionStateUseCase.disconnect() }
        _exitChannel.trySend(Unit)
    }
}
