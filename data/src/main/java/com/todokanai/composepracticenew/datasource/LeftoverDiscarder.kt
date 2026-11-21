package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.LeftoverCause
import com.todokanai.composepracticenew.model.NoticeMessage
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import javax.inject.Inject

/**
 * 멈춘 쓰기가 남긴 로컬 파일을 지우고, 지우지 못하면 그 사실을 알린다(#431) — 쓰다 만 파일이 말없이 온전한 파일처럼 남지 않도록.
 * 남은 로컬 파일의 정리는 이 클래스로만 한다. 결과를 돌려주는 경로는 실패 원인에 담고, 결과로 돌려줄 수 없는 경로(취소·구현 오류)는 작업 메시지 알림함에 넣는다.
 * @param notices 결과로 돌려줄 수 없는 정리 실패를 넣는 작업 메시지 알림함
 * @param delete 파일을 지우는 함수. 이미 없으면 아무것도 하지 않고, 지우지 못하면 IOException을 던진다.
 * 테스트에서는 지우지 못하는 경우를 재현하도록 넘긴다
 */
class LeftoverDiscarder internal constructor(
    private val notices: OperationNoticeReporter,
    private val delete: (File) -> Unit
) {

    /** 실제 파일 시스템에서 지우는 정리 부품을 만든다. Hilt가 사용하는 생성자다. */
    @Inject constructor(notices: OperationNoticeReporter) : this(notices, { Files.deleteIfExists(it.toPath()) })

    /**
     * 결과로 돌려줄 수 없게 멈춘(취소·구현 오류) 쓰기의 [file]을 지운다. 지우지 못하면 알림함에 넣는다. 이미 없으면 아무것도 하지 않는다.
     * @param file 쓰다 만 파일
     */
    fun discardReporting(file: File) {
        val error = removalErrorOf(file) ?: return
        notices.report(NoticeMessage.Leftover(file.path, LeftoverCause.LocalRemovalFailed(ioFailureReasonOf(error))))
    }

    /**
     * 실패로 끝난 쓰기의 [file]을 지운다. 지우지 못하면 [failure]의 원인에 남은 파일을 담는다 — 실패의 종류(연결 끊김 등)는 그대로 둔다.
     * @param file 쓰다 만 파일
     * @param failure 쓰기가 실패한 결과
     * @return 지웠으면 [failure] 그대로, 지우지 못했으면 남은 파일을 담은 같은 종류의 실패
     */
    fun discardInto(file: File, failure: IoResult.Failure): IoResult.Failure {
        val removal = ioFailureReasonOf(removalErrorOf(file) ?: return failure)
        return when (failure) {
            is IoResult.ItemFailed -> IoResult.ItemFailed(FailureReason.LeftoverNotRemoved(failure.reason, removal))
            is IoResult.ConnectionLost -> IoResult.ConnectionLost(FailureReason.LeftoverNotRemoved(failure.reason, removal))
            is IoResult.PartiallyFailed -> IoResult.PartiallyFailed(failure.failures + FailedItem("", FailureReason.LeftoverNotRemoved(null, removal)))
        }
    }

    /**
     * [file]을 쓰는 [block]을 실행하고, 취소로 멈추면 [file]을 지운 뒤 취소를 전파한다 — 쓰다 만 파일이 온전한 파일처럼 남지 않도록.
     * 입출력 실패로 멈춘 파일은 지우지 않는다(실패는 결과로 알린다). [block]은 [file]의 출력을 닫고 끝나야 한다.
     * @param file [block]이 쓰는 파일
     * @param block [file]을 쓰는 작업
     * @return [block]의 결과
     */
    internal inline fun <T> deletingOnCancel(file: File, block: () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            discardReporting(file)
            throw e
        }

    /**
     * [file]을 쓰는 [block]을 실행하고, 끝까지 성공하지 못하면(취소·입출력 실패·구현 오류) [file]을 지운다.
     * 닫을 때 목차를 써서 멈춘 곳까지만 담고도 온전하게 열리는 형식(ZIP — 압축, #427)에 쓴다.
     * 입출력 실패는 결과로 돌려주고(지우지 못했으면 그 원인에 담는다), 취소·구현 오류는 지운 뒤 그대로 전파한다.
     * [file]은 이 작업이 새로 만든 파일이어야 한다 — 기존 파일을 지우지 않도록.
     * [block]은 예외로 끝날 때도 [file]의 출력을 닫아야 한다 — 열린 채로는 Windows에서 지우지 못하고, Android에서는 지운 뒤에도 열린 fd가 남는다.
     * @param file [block]이 쓰는 파일
     * @param block [file]을 쓰는 작업
     * @return [block]의 결과, 또는 입출력 실패
     */
    internal inline fun <T> deletingUnlessCompleted(file: File, block: () -> T): IoResult<T> =
        try {
            IoResult.Success(block())
        } catch (e: IOException) {
            discardInto(file, IoResult.ItemFailed(ioFailureReasonOf(e)))
        } catch (e: Throwable) {
            discardReporting(file)
            throw e
        }

    /**
     * [file]을 지운다.
     * @param file 지울 파일
     * @return 지우지 못했으면 그 입출력 실패. 지웠거나 이미 없으면 null
     */
    private fun removalErrorOf(file: File): IOException? =
        try {
            delete(file)
            null
        } catch (e: IOException) {
            e
        }
}
