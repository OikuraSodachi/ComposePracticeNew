package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FTP 파일 명령(이름변경·생성·폴더 확인)을 FtpSession을 통해 실행한다. 목록 조회는 [FtpListingReader], 트리 수집은 [FtpTreeCollector], 삭제는 [FtpRemoteDeletion]이 맡는다.
 * 모든 명령은 실패를 결과로 알리며, 연결이 없거나 끊기면 [IoResult.ConnectionLost]를 돌려준다.
 * 서버 응답의 해석은 [FtpReplyRules]에 맡긴다.
 * @param session 폴더 확인([ensureDirectory])을 실행할 FTPClient와 잠금을 소유한 FTP 세션
 * @param commands 이름 변경·생성 명령과 대상 밖으로 나가는 이동을 실행하는 FtpCommandRunner
 */
@Singleton
class FtpCommandExecutor @Inject internal constructor(
    private val session: FtpCommandSession,
    private val commands: FtpCommandRunner
) {

    /**
     * fromPath를 toPath로 이름 변경 또는 이동한다.
     * 알려진 문제(방치하기로 결정): 서버가 목록에서 숨긴 항목이 toPath에 있으면 충돌 검사에 보이지 않고, RNTO가 기존 파일을 바꾸는 서버(vsftpd 등)에서는
     * 묻지 않고 덮어쓴다([FtpListingReader.identifiedListing] 참고).
     * @param fromPath 원본 경로 (ftp://server/path 형식)
     * @param toPath 변경할 경로 (ftp://server/path 형식)
     * @return 명령 결과
     */
    suspend fun rename(fromPath: String, toPath: String): IoResult<Unit> {
        val fromFtpPath = extractFtpPath(fromPath)
        val toFtpPath = extractFtpPath(toPath)
        return commands.run(FtpCommand.RENAME, commands.leavingTarget(fromFtpPath, outside = parentFtpPath(fromFtpPath))) {
            it.rename(fromFtpPath, toFtpPath)
        }
    }

    /**
     * path에 새 원격 디렉터리를 생성한다.
     * @param path 생성할 디렉터리의 절대 경로 (ftp://server/path 형식)
     * @return 명령 결과
     */
    suspend fun makeDirectory(path: String): IoResult<Unit> {
        val ftpPath = extractFtpPath(path)
        return commands.run(FtpCommand.MKD) { it.makeDirectory(ftpPath) }
    }

    /**
     * [ftpPath]에 원격 디렉터리가 있게 한다. 폴더 업로드가 쓴다.
     * 이미 있는 폴더에도 권한 없음과 같은 거부 응답이 와 응답으로는 구분할 수 없으므로, 거부되면 그 폴더로 CWD해 있는지 확인한다.
     * 확인이 성공하면 세션은 그 폴더 안에 남는다.
     * @param ftpPath 있어야 할 디렉터리의 FTP 경로
     * @return 디렉터리가 있으면 성공, 없거나 들어갈 수 없으면 MKD 거부 응답을 담은 실패, 연결이 없거나 끊겼으면 그 실패
     */
    internal suspend fun ensureDirectory(ftpPath: String): IoResult<Unit> =
        session.withConnectedClient { client ->
            withContext(Dispatchers.IO) {
                if (client.makeDirectory(ftpPath)) {
                    IoResult.Done
                } else {
                    // 확인하는 CWD가 마지막 응답을 덮어쓰기 전에 MKD의 거부 응답을 읽어 둔다
                    val refused = FtpReplyRules.refusal(FtpCommand.MKD, client.getReplyString())
                    if (client.changeDirectory(ftpPath)) IoResult.Done else refused
                }
            }
        }
}
