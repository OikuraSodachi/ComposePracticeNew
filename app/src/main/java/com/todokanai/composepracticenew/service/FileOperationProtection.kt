package com.todokanai.composepracticenew.service

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.operation.OperationProtection
import com.todokanai.composepracticenew.tools.OperationMessages
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 파일 작업 보호 서비스([FileOperationForegroundService])로 [OperationProtection]을 구현한다.
 * 작업마다 시작 요청을 따로 보내고, 그 요청으로 서비스가 포그라운드가 됐는지 거부됐는지를 요청 id로 받는다.
 * 이미 실행 중인 서비스를 믿지 않고 매번 요청하는 것은, 그 서비스가 이전 요청 기준으로 멈추는 중일 수 있기 때문이다 — 새 요청이 전달되면 이전 요청 기준의 멈춤은 일어나지 않는다.
 * 받아들여진 시작 요청은 시스템이 몇 초 안에 서비스에 전달하므로(전달하지 못하면 시스템이 앱을 종료한다) 결과를 끝없이 기다리지 않는다.
 * @param controller 시작 요청을 보내는 컨트롤러
 * @param messages 보호가 거부됐을 때의 실패 메시지를 제공하는 OperationMessages
 */
@Singleton
class FileOperationProtection @Inject constructor(
    private val controller: FileOperationServiceController,
    private val messages: OperationMessages
) : OperationProtection {

    /** 요청 id 발급과 대기 목록을 지키는 잠금. 요청은 작업 스레드에서, 결과는 메인 스레드에서 온다. */
    private val lock = Any()

    /** 마지막으로 발급한 요청 id. [lock] 안에서만 읽고 쓴다. */
    private var lastRequestId = 0

    /** 결과를 기다리는 요청. [lock] 안에서만 읽고 쓴다. */
    private val pending = mutableMapOf<Int, CompletableDeferred<Boolean>>()

    override suspend fun acquire(): IoResult<Unit> =
        if (requestForeground()) IoResult.Done else IoResult.ItemFailed(messages.protectionRefused)

    /**
     * 서비스가 시작 요청의 결과를 알린다. 이미 기다리지 않는 요청(작업이 취소됨)이면 무시한다.
     * @param requestId 시작 요청의 id
     * @param granted 그 요청으로 포그라운드가 됐으면 true, 시스템이 거부했으면 false
     */
    fun report(requestId: Int, granted: Boolean) {
        synchronized(lock) { pending[requestId] }?.complete(granted)
    }

    /**
     * 새 시작 요청을 보내고 그 결과를 기다린다.
     * @return 포그라운드가 됐으면 true, 시스템이 거부했으면 false
     */
    private suspend fun requestForeground(): Boolean {
        val result = CompletableDeferred<Boolean>()
        val requestId = synchronized(lock) { (++lastRequestId).also { pending[it] = result } }
        try {
            if (!controller.start(requestId)) return false
            return result.await()
        } finally {
            synchronized(lock) { pending.remove(requestId) }
        }
    }
}
