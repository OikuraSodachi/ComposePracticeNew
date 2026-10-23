package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.ConnectFailureReason
import com.todokanai.composepracticenew.model.ConnectResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
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
     * 실패하면 소켓을 닫아 연결을 남기지 않는다.
     * @param address 서버 호스트명 또는 IP (ftp:// 접두사 포함 가능)
     * @param port FTP 포트 (기본 21)
     * @param userId FTP 로그인 아이디
     * @param password FTP 로그인 비밀번호
     * @param encoding 컨트롤 채널 문자 인코딩 (예: UTF-8, EUC-KR)
     * @return 연결 결과
     */
    suspend fun connect(address: String, port: Int, userId: String, password: String, encoding: String): ConnectResult {
        session.setConnecting(true)
        return session.withClient { client ->
            withContext(Dispatchers.IO) {
                try {
                    val server = address.removePrefix(FTP_SCHEME)
                    if (client.isConnected) {
                        runCatching { client.logout() }
                        runCatching { client.disconnect() }
                        session.markLoggedOut()
                    }
                    client.connectTimeout = CONNECT_TIMEOUT_MS
                    client.setControlEncoding(encoding)
                    client.connect(server, port)
                    client.soTimeout = SO_TIMEOUT_MS
                    val result = loginAndCheck(client, userId, password)
                    if (result == ConnectResult.Connected) {
                        session.markLoggedIn()
                        keepAlive.start()
                    } else {
                        closeQuietly(client)
                    }
                    result
                } catch (e: Exception) {
                    closeQuietly(client)
                    ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED)
                } finally {
                    session.setConnecting(false)
                }
            }
        }
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
