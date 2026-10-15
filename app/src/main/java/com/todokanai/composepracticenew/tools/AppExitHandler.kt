package com.todokanai.composepracticenew.tools

import com.todokanai.composepracticenew.service.FtpServiceController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FTP 서비스를 중단하고 앱 종료 이벤트를 발행하는 핸들러.
 * @param ftpServiceController FTP Foreground Service 시작·종료를 담당하는 컨트롤러
 */
@Singleton
class AppExitHandler @Inject constructor(
    private val ftpServiceController: FtpServiceController
) {
    private val _exitChannel = Channel<Unit>(Channel.CONFLATED)

    /** FTP 서비스를 중단하고 앱을 종료하라는 이벤트. NavController에서 수집해 finishAndRemoveTask()를 호출한다. */
    val exitEvent: Flow<Unit> = _exitChannel.receiveAsFlow()

    /**
     * FTP 서비스를 중단하고 앱 종료 이벤트를 발행한다.
     * @return Unit
     */
    fun execute() {
        ftpServiceController.stop()
        _exitChannel.trySend(Unit)
    }
}
