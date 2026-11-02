package com.todokanai.composepracticenew.data.ftp

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 쓰기 한 번의 제한 시간. 쓰기가 끝나는 것과 제한 시간이 지나는 것 중 먼저 일어난 쪽 하나만 결과를 정한다.
 * 제한 시간이 먼저 지나면 [onExpire]를 실행하고, 그 뒤에 끝난 쓰기는 제때 끝나지 않은 것([expired])으로 본다 —
 * 예약 취소(`Future.cancel`)의 반환값은 이미 실행 중인 예약에도 true일 수 있어, 그것으로는 어느 쪽이 먼저인지 알 수 없다.
 * @param scheduler 제한 시간을 재는 실행기
 * @param timeoutMillis 쓰기가 끝나기를 기다리는 상한(ms)
 * @param onExpire 쓰기가 끝나기 전에 제한 시간이 지나면 [scheduler]의 스레드에서 한 번 실행할 동작
 */
internal class WriteDeadline(
    scheduler: ScheduledExecutorService,
    timeoutMillis: Long,
    private val onExpire: () -> Unit
) {

    /** 결과를 정한 쪽. 쓰기 스레드와 감시 스레드가 함께 바꾸므로 원자 값으로 두고, [State.PENDING]에서 먼저 바꾼 쪽만 이긴다. */
    private val state = AtomicReference(State.PENDING)
    /** 제한 시간이 지나면 [expire]를 실행하는 예약. */
    private val expiry: ScheduledFuture<*> = scheduler.schedule(Runnable { expire() }, timeoutMillis, TimeUnit.MILLISECONDS)

    /** 쓰기가 끝나기 전에 제한 시간이 지나 [onExpire]를 실행했는지. */
    val expired: Boolean
        get() = state.get() == State.EXPIRED

    /** 쓰기가 끝났음을 알린다. 아직 제한 시간이 지나지 않았으면 이후 [onExpire]는 실행되지 않는다. 여러 번 불러도 된다. */
    fun finish() {
        state.compareAndSet(State.PENDING, State.FINISHED)
        expiry.cancel(false)
    }

    /** 제한 시간이 지났을 때 감시 스레드에서 실행된다. 쓰기가 아직 끝나지 않았을 때만 [onExpire]를 실행한다. */
    private fun expire() {
        if (state.compareAndSet(State.PENDING, State.EXPIRED)) onExpire()
    }

    /** 쓰기 한 번의 결과를 정한 쪽. */
    private enum class State {
        /** 아직 정해지지 않았다. */
        PENDING,
        /** 쓰기가 제한 시간 안에 끝났다. */
        FINISHED,
        /** 제한 시간이 먼저 지났다. */
        EXPIRED
    }
}
