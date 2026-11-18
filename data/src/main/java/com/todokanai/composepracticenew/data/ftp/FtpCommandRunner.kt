package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Singleton

/**
 * 성공 여부를 반환하는 FTP 명령(이름 변경·삭제·생성)을 FtpSession을 통해 실행하고, 지우거나 옮길 대상 밖으로 먼저 나가는 디렉터리 이동을 만든다.
 * 실패는 결과로 알리며, 연결이 없거나 끊기면 [IoResult.ConnectionLost]를 돌려준다. 서버 응답의 해석은 [FtpReplyRules]에 맡긴다.
 * @param session 명령을 실행할 FTPClient와 잠금을 소유한 FTP 세션
 */
@Singleton
class FtpCommandRunner internal constructor(
    private val session: FtpCommandSession
) {

    /**
     * 세션 잠금 확보 후 IO 스레드에서 성공 여부를 반환하는 FTP 명령을 실행하고, 실패하면 서버 응답을 담은 실패를 돌려준다.
     * 성공 판정은 명령마다 성공 응답을 아는 commons-net의 반환값을 따른다.
     * @param command 실행할 명령 — 실패 메시지에 쓴다
     * @param leave 명령 전에 같은 잠금 안에서 대상 밖으로 나가는 디렉터리 이동([leavingTarget]). null이면 이동하지 않는다
     * @param block 실행할 FTP 명령. 성공하면 true를 반환한다
     * @return 명령 결과
     */
    internal suspend fun run(
        command: FtpCommand,
        leave: ((FtpCommandClient) -> Unit)? = null,
        block: (FtpCommandClient) -> Boolean
    ): IoResult<Unit> =
        session.withConnectedClient { client ->
            withContext(Dispatchers.IO) {
                leave?.invoke(client)
                if (block(client)) IoResult.Done else FtpReplyRules.refusal(command, client.getReplyString())
            }
        }

    /**
     * 세션의 현재 디렉터리가 [targetFtpPath]이거나 그 안이면 대상 밖으로 옮기는 디렉터리 이동을 만든다.
     * LIST 서버의 목록 조회는 세션을 조회한 폴더 안에 남기므로, 그 폴더를 지우거나 옮기는 명령은 대상 밖에서 보낸다 —
     * 세션이 들어가 있는 디렉터리의 삭제를 거부하는 서버가 있다(Apache FTP Server, Windows 서버). 명령과 같은 잠금 안에서 실행해야 그 사이 다른 조회가 다시 들어가지 않는다.
     * 나가기는 그런 서버를 위한 사전 조치이므로, 어디로도 나가지 못해도 명령은 보내고 서버의 응답을 결과로 삼는다 —
     * 세션이 들어가 있는 디렉터리도 지울 수 있는 서버(리눅스 계열)에서 명령을 막지 않는다.
     * @param targetFtpPath 지우거나 옮길 항목의 FTP 경로
     * @param outside 먼저 옮겨 갈 디렉터리의 FTP 경로. [targetFtpPath] 밖이어야 한다
     * @return 잠금을 확보한 FTP 클라이언트로 실행할 디렉터리 이동
     */
    internal fun leavingTarget(targetFtpPath: String, outside: String): (FtpCommandClient) -> Unit = { client ->
        val current = client.workingDirectory
        if (current != null && isSameOrInside(current, targetFtpPath)) {
            exitCandidates(client, targetFtpPath, outside).firstOrNull { client.changeDirectory(it) }
        }
    }

    /**
     * [targetFtpPath] 밖으로 나갈 때 차례로 시도할 디렉터리. 대상의 상위 디렉터리를 먼저, 거부되면 로그인 홈([FtpCommandClient.homeDirectory])을 시도한다.
     * 가상 경로 권한을 쓰는 서버는 상위 디렉터리로의 이동을 거부할 수 있지만, 홈은 로그인한 사용자가 들어가 있던 곳이다. 홈이 대상 안이면 시도하지 않는다.
     * @param client 잠금을 확보한 FTP 클라이언트
     * @param targetFtpPath 지우거나 옮길 항목의 FTP 경로
     * @param outside 먼저 옮겨 갈 디렉터리의 FTP 경로
     * @return 시도할 디렉터리의 FTP 경로 목록
     */
    private fun exitCandidates(client: FtpCommandClient, targetFtpPath: String, outside: String): List<String> {
        val home = client.homeDirectory?.takeUnless { isSameOrInside(it, targetFtpPath) }
        return listOfNotNull(outside, home).distinct()
    }

    /**
     * [path]가 [directoryPath] 자신이거나 그 아래인지 판별한다.
     * @param path 판별할 FTP 경로
     * @param directoryPath 기준 디렉터리의 FTP 경로
     * @return 같거나 그 아래면 true
     */
    private fun isSameOrInside(path: String, directoryPath: String): Boolean =
        path.trimEnd('/') == directoryPath.trimEnd('/') || isRemoteAncestorOf(directoryPath, path)
}
