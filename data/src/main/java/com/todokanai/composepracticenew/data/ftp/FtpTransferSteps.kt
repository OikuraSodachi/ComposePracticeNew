package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import java.io.InputStream
import java.io.OutputStream

// 다운로드([FtpDownloader])와 업로드([FtpUploader])가 함께 쓰는 전송 단계.

/** 전송 스트림을 복사할 때 쓰는 버퍼 크기(바이트). */
internal const val TRANSFER_BUFFER_SIZE = 128 * 1024

/**
 * 전송 명령의 결과([DataCommandRunner.run])를 이 항목의 결과로 만든다. 서버가 전송 완료를 확인해야 성공이다.
 * 전송(데이터 스트림·로컬 파일) 중 실패는 제어 채널과 무관하고 완료 응답까지 읽었으므로 이 항목만의 실패다.
 * @param client 응답을 받은 FTP 클라이언트
 * @param outcome 전송 명령의 결과
 * @param onAcked 서버가 전송 완료를 확인한 뒤 호출되는 콜백
 * @return 전송 결과
 */
internal suspend fun transferResult(
    client: FtpCommandClient,
    outcome: DataCommandOutcome,
    onAcked: suspend () -> Unit
): IoResult<Unit> = when (outcome) {
    is DataCommandOutcome.Refused -> FtpReplyRules.dataCommandRefusal(outcome.step, client.getReplyString())
    is DataCommandOutcome.DataFailed -> IoResult.ItemFailed(FailureReason.TransferFailed(outcome.cause.message))
    is DataCommandOutcome.NotConnected -> IoResult.ItemFailed(dataConnectionFailure(outcome.cause))
    is DataCommandOutcome.Finished ->
        if (!outcome.acknowledged) FtpReplyRules.unacknowledgedTransfer(client.getReplyString())
        else {
            onAcked()
            IoResult.Done
        }
}

/** [input]을 버퍼 단위로 [output]에 복사하며 청크마다 [onChunk]에 읽은 바이트 수를 전달한다. */
internal inline fun pump(input: InputStream, output: OutputStream, onChunk: (Int) -> Unit) {
    val buffer = ByteArray(TRANSFER_BUFFER_SIZE)
    var bytesRead: Int
    while (input.read(buffer).also { bytesRead = it } != -1) {
        output.write(buffer, 0, bytesRead)
        onChunk(bytesRead)
    }
}
