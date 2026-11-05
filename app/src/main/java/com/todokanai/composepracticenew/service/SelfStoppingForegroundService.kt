package com.todokanai.composepracticenew.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 시작은 밖에서 받고 멈춤은 스스로 하는 ForegroundService의 공통 수명 규칙.
 * 멈춤은 언제나 이 서비스가 startForeground 뒤에 한다 — 밖에서 멈추면 startForeground 전에 멈춰 startForegroundService 계약 위반으로 앱이 종료될 수 있다.
 * 시스템이 정한 실행 시간 한도(Android 15의 dataSync 6시간)에 이르면 바로 멈춘다 — 몇 초 안에 멈추지 않으면 앱이 종료된다.
 * 시스템이 startForeground를 거부해도 바로 멈춘다 — 거부를 잡지 않으면 메인 스레드에서 나 앱이 종료된다.
 */
abstract class SelfStoppingForegroundService : Service() {

    /** 멈춤 관찰을 실행하는 스코프. 메인 스레드에서 실행해 멈춤이 언제나 같은 스레드의 startForeground 뒤에 오게 한다. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 가장 최근 시작 요청에 대한 멈춤 관찰. 시작 요청마다 새로 시작한다. */
    private var stopWatch: Job? = null

    /**
     * START_NOT_STICKY 반환 — 프로세스 종료 시 시스템이 서비스를 재시작하지 않는다.
     * 포그라운드 알림을 시작한 뒤, 멈춤 관찰을 이 시작 요청 기준으로 다시 시작한다.
     * 관찰은 시작 요청의 id로 멈추므로, 그 뒤에 온 시작 요청이 있으면 멈추지 않고 그 요청의 관찰이 이어받는다.
     * 시스템이 startForeground를 거부하면(백그라운드 시작 제한, 실행 시간 한도 소진) 관찰 없이 이 요청의 id로 바로 멈추고, 그 뒤의 처리는 [onStartRefused]에 맡긴다.
     * 거부된 startForeground도 startForegroundService 계약을 채우므로 이때 멈춰도 계약 위반이 아니다.
     */
    final override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopWatch?.cancel()
        if (!enterForegroundUnlessRefused(intent)) {
            stopWatch = null
            // 이론상: 이미 포그라운드인 서비스에서 뒤 요청의 startForeground가 거부되면, 이 startId가 가장 최근 것이라
            // 앞 요청으로 보호를 받아 진행 중인 작업이 있어도 서비스가 멈춰 그 작업은 알림 없이 보호를 잃는다.
            // 재호출이 실제로 거부되는지는 플랫폼 동작에 달려 있어 확인하지 못했으며, 대응하지 않기로 했다(PR #298 리뷰, won't fix)
            stopSelf(startId)
            onStartRefused(intent)
            return START_NOT_STICKY
        }
        // startForeground 뒤에 관찰한다 — 그 전에 멈추면 startForegroundService 계약 위반으로 앱이 종료된다
        stopWatch = serviceScope.launch {
            awaitStop()
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    /**
     * [enterForeground]를 호출한다. 시스템이 startForeground를 거부하면 false를 돌려준다(거부가 아닌 예외는 그대로 던진다).
     * @param intent 시작 요청의 Intent
     * @return 포그라운드가 됐으면 true, 시스템이 거부했으면 false
     */
    private fun enterForegroundUnlessRefused(intent: Intent?): Boolean =
        try {
            enterForeground(intent)
            true
        } catch (e: IllegalStateException) {
            if (!e.isForegroundStartRefusal()) throw e
            false
        }

    /**
     * 시스템이 정한 실행 시간 한도에 이르렀다. 이 서비스는 더 이상 포그라운드가 아니므로 바로 멈추고, 그 뒤의 처리는 [onTimeLimitReached]에 맡긴다.
     * 멈춤은 [startId]로 한다 — 아직 전달되지 않은 시작 요청이 있으면 멈추지 않고 그 요청이 startForeground를 부른 뒤 수명을 이어받는다.
     * 무조건 멈추면(`stopSelf()`) 그 요청이 startForeground를 부르기 전에 멈춰 startForegroundService 계약 위반으로 앱이 종료된다.
     * @param startId 가장 최근에 전달된 시작 요청의 id
     * @param fgsType 한도에 이른 포그라운드 서비스 유형
     */
    final override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf(startId)
        onTimeLimitReached()
    }

    /**
     * 시작 요청에 맞는 알림으로 startForeground를 호출한다. 정상으로 돌아오면 이 요청으로 포그라운드가 된 것이다.
     * @param intent 시작 요청의 Intent. 시스템이 다시 전달한 경우 null일 수 있다
     */
    protected abstract fun enterForeground(intent: Intent?)

    /**
     * 서비스를 멈춰야 할 때까지 기다린다. 이미 멈춰야 하면 바로 돌아온다.
     * @return Unit
     */
    protected abstract suspend fun awaitStop()

    /** 실행 시간 한도로 멈춘 뒤의 처리. 기본은 아무것도 하지 않는다. */
    protected open fun onTimeLimitReached() {}

    /**
     * 시스템이 startForeground를 거부해 멈춘 뒤의 처리.
     * @param intent 거부된 시작 요청의 Intent
     */
    protected abstract fun onStartRefused(intent: Intent?)

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
