package com.todokanai.composepracticenew.data.ftp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
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
     * @param address 서버 호스트명 또는 IP (ftp:// 접두사 포함 가능)
     * @param port FTP 포트 (기본 21)
     * @param userId FTP 로그인 아이디
     * @param password FTP 로그인 비밀번호
     * @param encoding 컨트롤 채널 문자 인코딩 (예: UTF-8, EUC-KR)
     * @return 연결 및 로그인 성공 시 true
     */
    suspend fun connect(address: String, port: Int, userId: String, password: String, encoding: String): Boolean {
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
                    val loggedIn = client.login(userId, password)
                    if (loggedIn) {
                        client.enterLocalPassiveMode()
                        client.setFileType(FTP.BINARY_FILE_TYPE)
                        session.markLoggedIn()
                        keepAlive.start()
                    } else {
                        runCatching { client.disconnect() }
                    }
                    loggedIn
                } catch (e: Exception) {
                    false
                } finally {
                    session.setConnecting(false)
                }
            }
        }
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
