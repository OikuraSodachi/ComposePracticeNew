package com.todokanai.composepracticenew.data.ftp

import kotlinx.coroutines.CancellationException
import java.io.Closeable
import java.io.IOException
import javax.inject.Inject

/**
 * 데이터 연결을 쓰는 명령 하나를 [DataCommandRunner]로 실행한 결과. 서버 응답을 무엇으로 볼지는 명령마다 받는 쪽이 정한다.
 * 어느 결과든 제어 채널의 응답은 끝까지 읽은 것이다 — 응답을 읽지 못한 실패는 결과가 아니라 예외로 전파된다.
 */
internal sealed interface DataCommandOutcome {

    /** 서버가 데이터 연결을 열기 전에 명령을 거부했다(PASV·REST 실패, 명령의 1xx 아닌 응답). 거부 응답은 FTPClient에 남아 있다. */
    data object Refused : DataCommandOutcome

    /**
     * 데이터를 끝까지 주고받고 데이터 연결을 정상으로 닫은 뒤 완료 응답을 읽었다.
     * @param acknowledged 완료 응답이 성공(2xx)이면 true
     */
    data class Finished(val acknowledged: Boolean) : DataCommandOutcome

    /**
     * 데이터 연결(또는 그 끝의 로컬 스트림)이 실패해 데이터 연결을 중단으로 닫은 뒤 완료 응답을 읽었다. 이 명령만의 실패다.
     * @param cause 데이터를 주고받다 난 실패
     */
    data class DataFailed(val cause: IOException) : DataCommandOutcome
}

/** 취소로 멈춘 명령 뒤의 정리 명령([DataCommandRunner.cleanUpAfterCancel])의 결과. */
internal sealed interface CancelCleanup {

    /** 서버가 정리 명령을 받아들였다. */
    data object Done : CancelCleanup

    /**
     * 서버가 정리 명령을 거부했다. 세션은 그대로다.
     * @param reply 서버가 보낸 거부 응답 문자열
     */
    data class Refused(val reply: String) : CancelCleanup

    /** 세션이 이미 끝났거나 정리 명령의 응답을 읽지 못해 세션을 끝내, 정리 명령이 처리되지 않았다. */
    data object SessionEnded : CancelCleanup
}

/**
 * 데이터 연결을 쓰는 명령(RETR·STOR·LIST·MLSD)을 한 규칙으로 실행한다. 데이터 연결의 실패는 그 명령만의 실패이고, 완료 응답을 읽지 못한 실패는 세션 종료다.
 * 데이터 연결은 제어 채널과 따로 오가므로, 그 실패 뒤에도 완료 응답까지 읽으면 응답 순서가 유지된다.
 * 데이터 연결은 데이터를 끝까지 주고받았을 때만 정상으로 닫고([DataConnection.finish]), 실패·취소·구현 오류로 멈췄으면 중단으로 닫는다([DataConnection.abort]) —
 * 중단을 정상 닫기로 알리면 서버는 잘린 업로드를 다 받은 파일로 저장한다(RFC 959 §3.4.1). 정상으로 닫지 못한 것(남은 데이터를 보내지 못함)도 데이터 연결의 실패다.
 * @param session 데이터 연결을 열고, 응답 순서를 알 수 없게 된 세션을 끝내는 FTP 세션
 */
internal class DataCommandRunner @Inject constructor(private val session: FtpDataCommandSession) {

    /**
     * [open]으로 데이터 연결을 열어 [exchange]로 데이터를 주고받고, 연결을 닫은 뒤 완료 응답을 읽는다. 세션 잠금을 쥔 블록 안에서 호출한다.
     * 완료 응답을 읽다 실패하면 제어 채널을 더 쓸 수 없으므로 그 예외를 전파해 [FtpSession.withConnectedClient]가 세션을 끝내게 한다(데이터 연결의 실패는 suppressed로 붙인다).
     * 취소와 구현 오류는 결과로 바꾸지 않고 전파한다. 구현 오류는 [FtpSession.withConnectedClient]가 세션을 끝내므로 데이터 연결만 닫는다.
     * 취소는 세션을 끝내지 않고 전파되므로, [onCancel]을 실행하고 완료 응답을 기다려 응답 순서를 맞춘다. 완료 응답을 읽지 못했으면 여기서 세션을 끝낸다.
     * @param client 잠금을 확보한 FTP 클라이언트
     * @param open 명령을 보내 데이터 스트림을 여는 동작. 서버가 거부하면 null을 돌려준다
     * @param onCancel 취소됐을 때 실행할 정리 작업
     * @param exchange 열린 데이터 스트림으로 데이터를 주고받는 동작. 스트림은 이 함수가 닫는다
     * @return 명령의 결과
     * @throws IOException 명령이나 완료 응답을 읽지 못한 경우
     */
    suspend fun <S : Closeable> run(
        client: FtpCommandClient,
        open: () -> S?,
        onCancel: () -> Unit = {},
        exchange: suspend (S) -> Unit
    ): DataCommandOutcome {
        // 스트림을 열지 못했으면 응답을 끝까지 읽은 것이다 — 응답 코드와 관계없이 이 명령만 실패다.
        // 기다릴 응답이 없으므로 completePendingCommand는 부르지 않는다. 부르면 오지 않을 응답을 상한까지 기다리며 세션 잠금을 쥔다
        val connection = session.openDataConnection(open) ?: return DataCommandOutcome.Refused
        val dataFailure = try {
            exchange(connection.stream)
            connection.finish()
            null
        } catch (e: IOException) {
            connection.abort()
            e
        } catch (e: CancellationException) {
            onCancel()
            connection.abort()
            try {
                client.completePendingCommand()
            } catch (_: IOException) {
                session.endUnusableSession()
            }
            throw e
        } catch (e: Exception) {
            connection.abort()
            throw e
        }
        val acknowledged = try {
            client.completePendingCommand()
        } catch (controlFailure: IOException) {
            dataFailure?.let(controlFailure::addSuppressed)
            throw controlFailure
        }
        return if (dataFailure != null) DataCommandOutcome.DataFailed(dataFailure) else DataCommandOutcome.Finished(acknowledged)
    }

    /**
     * 취소로 멈춘 명령([run]이 취소를 전파함)이 서버에 남긴 것을 [cleanup] 명령 하나로 정리한다. 같은 잠금 안에서 [run] 바로 뒤에 호출한다.
     * 취소는 [FtpSession.withConnectedClient]를 그대로 지나가므로, 취소 뒤의 정리 명령이 실패한 의미는 그 경계 대신 여기서 같은 규칙으로 정한다.
     * [run]의 취소 정리에서 세션을 끝냈으면 명령을 보내지 않는다. 정리 명령의 응답을 읽지 못하면 응답 순서를 알 수 없으므로 세션을 끝낸다.
     * @param client 잠금을 확보한 FTP 클라이언트
     * @param cleanup 정리 명령을 보내고 서버가 받아들였는지(2xx)를 돌려주는 동작
     * @return 정리 명령의 결과
     */
    suspend fun cleanUpAfterCancel(client: FtpCommandClient, cleanup: () -> Boolean): CancelCleanup {
        if (!session.isReady()) return CancelCleanup.SessionEnded
        return try {
            if (cleanup()) CancelCleanup.Done else CancelCleanup.Refused(client.getReplyString())
        } catch (_: IOException) {
            session.endUnusableSession()
            CancelCleanup.SessionEnded
        }
    }
}
