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
     */
    final override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        enterForeground(intent)
        // startForeground 뒤에 관찰한다 — 그 전에 멈추면 startForegroundService 계약 위반으로 앱이 종료된다
        stopWatch?.cancel()
        stopWatch = serviceScope.launch {
            awaitStop()
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    /**
     * 시스템이 정한 실행 시간 한도에 이르렀다. 이 서비스는 더 이상 포그라운드가 아니므로 바로 멈추고, 그 뒤의 처리는 [onTimeLimitReached]에 맡긴다.
     * @param startId 가장 최근 시작 요청의 id
     * @param fgsType 한도에 이른 포그라운드 서비스 유형
     */
    final override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
        onTimeLimitReached()
    }

    /**
     * 시작 요청에 맞는 알림으로 startForeground를 호출한다.
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

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
