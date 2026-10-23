package com.todokanai.composepracticenew.data.ftp

import org.apache.commons.net.ProtocolCommandEvent
import org.apache.commons.net.ProtocolCommandListener
import org.apache.commons.net.ftp.FTPClient

/**
 * 명령을 보내고 응답을 받는 흐름을 기록하는 응답 이벤트 수신기. commons-net이 알려주지 않는 진행 상태를 응답 이벤트로 판별한다.
 * 응답 코드의 의미는 [FtpReplyRules]로 판단한다. 응답 이벤트는 ioMutex를 쥔 명령 스레드에서만 오므로([FtpSession.closeBypassingIoLock]) 동기화하지 않는다.
 */
internal class FtpReplyWatch : ProtocolCommandListener {
    /** 예비 응답(1xx)을 한 번이라도 받았으면 true. 목록 조회에서는 데이터 연결을 열어 전송을 시작했다는 뜻이다. */
    var preliminaryReceived = false
        private set

    /** 마지막으로 보낸 명령의 최종 응답(예비 응답이 아닌 응답)을 아직 받지 못했으면 true. 이때 난 실패는 늦게 올 응답을 제어 연결에 남긴다. */
    var awaitingFinalReply = false
        private set

    override fun protocolCommandSent(event: ProtocolCommandEvent) {
        awaitingFinalReply = true
    }

    override fun protocolReplyReceived(event: ProtocolCommandEvent) {
        if (FtpReplyRules.isPreliminaryReply(event.replyCode)) preliminaryReceived = true
        else awaitingFinalReply = false
    }
}

/**
 * [block]을 실행하는 동안 [client]의 응답 흐름을 [FtpReplyWatch]로 기록한다. 끝나면 예외가 나도 수신기를 뗀다.
 * @param client 잠금을 확보한 FTPClient
 * @param block 기록을 받아 실행할 명령
 * @return [block]의 결과
 */
internal inline fun <T> watchingReplies(client: FTPClient, block: (FtpReplyWatch) -> T): T {
    val watch = FtpReplyWatch()
    client.addProtocolCommandListener(watch)
    try {
        return block(watch)
    } finally {
        client.removeProtocolCommandListener(watch)
    }
}
