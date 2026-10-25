package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.ConnectFailureReason
import com.todokanai.composepracticenew.model.ConnectResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FTP 연결 수명주기(접속·로그인·해제)와 Keep-Alive 기동을 담당한다.
 * @param session 접속 대상 FTPClient와 상태를 소유한 FTP 세션
 * @param keepAlive 로그인 성공 후 idle timeout 방지를 위해 기동하는 Keep-Alive
 */
@Singleton
class FtpConnector @Inject constructor(
    private val session: FtpSession,
    private val keepAlive: FtpKeepAlive
) {

    /**
     * FTP 서버에 TCP 연결 후 로그인한다. 이미 연결된 경우 먼저 해제하고 재연결한다.
     * 로그인한 뒤 목록을 해석할 수 없는 서버([FtpListingFormat.isReadable])면 어느 폴더도 볼 수 없으므로 연결을 종료하고 실패로 알린다.
     * 연결할 때 목록 조회 옵션(LIST 서버의 `-a`, MLSD 서버의 항목 식별값)을 정한다([establishWithListingOptions]).
     * 연결하는 동안 앱이 종료를 요청했으면([FtpSession.closeBypassingIoLock]) 어느 단계에서 끝났든 실패로 알린다.
     * 실패하면 소켓을 닫아 연결을 남기지 않는다.
     * @param address 서버 호스트명 또는 IP (ftp:// 접두사 포함 가능)
     * @param port FTP 포트 (기본 21)
     * @param userId FTP 로그인 아이디
     * @param password FTP 로그인 비밀번호
     * @param encoding 컨트롤 채널 문자 인코딩 (예: UTF-8, EUC-KR)
     * @return 연결 결과
     */
    suspend fun connect(address: String, port: Int, userId: String, password: String, encoding: String): ConnectResult {
        session.beginConnecting()
        val closeMark = session.closeRequestMark()
        return session.withClient { client ->
            withContext(Dispatchers.IO) {
                try {
                    val server = address.removePrefix(FTP_SCHEME)
                    if (client.isConnected) {
                        runCatching { client.logout() }
                        runCatching { client.disconnect() }
                        session.markLoggedOut()
                    }
                    val established = establishWithListingOptions(client, closeMark) { establish(client, server, port, userId, password, encoding) }
                    val result = if (established == ConnectResult.Connected) markLoggedIn(client, closeMark) else established
                    if (result == ConnectResult.Connected) keepAlive.start() else closeQuietly(client)
                    result
                } catch (e: IOException) {
                    // 접속·로그인·홈 확인 단계의 실패만 여기 온다 — 옵션 판정의 실패는 판정이 결과로 돌려준다
                    closeQuietly(client)
                    ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED)
                } finally {
                    session.endConnecting()
                }
            }
        }
    }

    /**
     * [openUsableConnection]으로 쓸 수 있는 연결을 만든 뒤 목록 조회 옵션을 정한다([FtpListingFormat.negotiateListingOptions]) —
     * LIST 서버는 숨김 항목을 담는 `-a` 옵션을 붙일지, MLSD 서버는 항목 식별값을 목록에 담도록 요청한다.
     * 옵션 판정은 연결이 쓸 수 있으려면 성공해야 하는 단계가 아니므로 연결을 실패로 만들지 않는다.
     * 판정이 제어 연결을 쓸 수 없게 만들면(서버가 끊거나 받지 못한 응답이 남음) 그 연결을 닫고 [openUsableConnection]으로 다시 연결해 옵션 없이 쓴다.
     * 같은 서버는 같은 판정에 다시 연결을 끊을 수 있으므로 다시 판정하지 않는다.
     * 그 끊김이 앱의 종료 요청([FtpSession.closeBypassingIoLock]) 때문이면 다시 연결하지 않는다 — 다시 연결하면 종료 요청을 뒤집는다.
     * @param client 연결할 FTPClient
     * @param closeMark 연결 작업을 시작할 때 읽은 종료 요청 표지([FtpSession.closeRequestMark])
     * @param openUsableConnection [client]를 접속·로그인시켜 쓸 수 있는 연결인지 확인하는 단계
     * @return 쓸 수 있는 연결이면 [ConnectResult.Connected], 아니면 실패
     * @throws java.io.IOException [openUsableConnection]이 접속하지 못했거나 응답을 읽지 못한 경우
     */
    private fun establishWithListingOptions(client: FTPClient, closeMark: Int, openUsableConnection: () -> ConnectResult): ConnectResult {
        val established = openUsableConnection()
        if (established != ConnectResult.Connected) return established
        return when (val probe = FtpListingFormat.negotiateListingOptions(client)) {
            is ListingOptionsProbe.Decided -> {
                client.listHiddenFiles = probe.useListOption
                ConnectResult.Connected
            }
            ListingOptionsProbe.ConnectionBroken -> {
                // 쓸 수 없는 제어 연결이므로 QUIT 응답을 기다리지 않고 소켓만 닫는다
                runCatching { client.disconnect() }
                if (session.isCloseRequestedSince(closeMark)) ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED)
                else openUsableConnection().also { client.listHiddenFiles = false }
            }
        }
    }

    /**
     * 쓸 수 있는 연결을 세션의 로그인 상태로 전환하며, 로그인 직후의 현재 디렉터리(홈)를 함께 기록한다.
     * 옵션 판정은 디렉터리를 옮기지 않으므로 지금의 현재 디렉터리가 홈이다. 서버가 PWD를 거부하면 홈을 모르는 채로 전환한다.
     * 종료 요청이 연결 작업과 겹쳤으면 그 연결은 쓸 수 있어 보여도 종료 요청을 따른다.
     * @param client 쓸 수 있는 연결을 연 FTPClient
     * @param closeMark 연결 작업을 시작할 때 읽은 종료 요청 표지([FtpSession.closeRequestMark])
     * @return 전환했으면 [ConnectResult.Connected], 그 사이 종료가 요청됐으면 실패
     * @throws java.io.IOException PWD 응답을 읽지 못한 경우
     */
    private suspend fun markLoggedIn(client: FTPClient, closeMark: Int): ConnectResult {
        val home = client.printWorkingDirectory()
        return if (session.markLoggedIn(closeMark, home)) ConnectResult.Connected else ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED)
    }

    /**
     * [client]를 서버에 접속하고 로그인해 쓸 수 있는 연결인지 확인한다. 하나라도 실패하면 연결을 쓸 수 없는 단계들이다.
     * @param client 연결할 FTPClient
     * @param server 서버 호스트명 또는 IP
     * @param port FTP 포트
     * @param userId FTP 로그인 아이디
     * @param password FTP 로그인 비밀번호
     * @param encoding 컨트롤 채널 문자 인코딩
     * @return 쓸 수 있는 연결이면 [ConnectResult.Connected]. 로그인이 거부됐거나 목록을 해석할 수 없으면 그 이유를 담은 실패
     * @throws java.io.IOException 접속하지 못했거나 응답을 읽지 못한 경우
     */
    private fun establish(client: FTPClient, server: String, port: Int, userId: String, password: String, encoding: String): ConnectResult {
        client.connectTimeout = CONNECT_TIMEOUT_MS
        client.setControlEncoding(encoding)
        client.connect(server, port)
        client.soTimeout = SO_TIMEOUT_MS
        return loginAndCheck(client, userId, password)
    }

    /**
     * 접속한 [client]로 로그인하고 전송 방식을 정한 뒤, 이 서버의 목록을 해석할 수 있는지 확인한다.
     * @param client 서버에 접속한 FTPClient
     * @param userId FTP 로그인 아이디
     * @param password FTP 로그인 비밀번호
     * @return 쓸 수 있는 연결이면 [ConnectResult.Connected]. 로그인이 거부됐거나 목록을 해석할 수 없으면 그 이유를 담은 실패
     * @throws java.io.IOException 응답을 읽지 못한 경우
     */
    private fun loginAndCheck(client: FTPClient, userId: String, password: String): ConnectResult {
        if (!client.login(userId, password)) return ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED)
        client.enterLocalPassiveMode()
        client.setFileType(FTP.BINARY_FILE_TYPE)
        if (!FtpListingFormat.isReadable(client)) return ConnectResult.Failed(ConnectFailureReason.LISTING_FORMAT_UNKNOWN)
        return ConnectResult.Connected
    }

    /**
     * 쓰지 않을 연결을 로그아웃 후 닫는다. 이미 닫혔거나 응답이 없어도 실패를 알리지 않는다 — 연결을 남기지 않는 것이 목적이다.
     * @param client 닫을 FTPClient
     */
    private fun closeQuietly(client: FTPClient) {
        if (client.isConnected) runCatching { client.logout() }
        runCatching { client.disconnect() }
    }

    /**
     * 현재 연결을 로그아웃 후 해제한다. ioMutex를 우회해 소켓을 즉시 닫으므로 진행 중인 명령이 중단된다.
     * 이미 미연결 상태이면 아무 동작도 하지 않는다.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        if (!session.isSocketOpen()) return@withContext
        keepAlive.stop()
        session.closeBypassingIoLock()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val SO_TIMEOUT_MS = 15_000
    }
}
