package com.todokanai.composepracticenew.tools

import com.todokanai.composepracticenew.service.FtpServiceController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val _exitEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** FTP 서비스를 중단하고 앱을 종료하라는 이벤트. NavController에서 수집해 finishAndRemoveTask()를 호출한다. */
    val exitEvent: SharedFlow<Unit> = _exitEvent.asSharedFlow()

    /**
     * FTP 서비스를 중단하고 앱 종료 이벤트를 발행한다.
     * @return Unit
     */
    fun execute() {
        ftpServiceController.stop()
        _exitEvent.tryEmit(Unit)
    }
}
