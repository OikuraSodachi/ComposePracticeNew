package com.todokanai.composepracticenew.data.ftp

import java.net.InetAddress
import java.net.ServerSocket
import javax.net.ServerSocketFactory

/**
 * 서버 소켓을 만들지 않는 팩토리. FTPClient가 능동(PORT) 모드로 데이터 연결을 열면 구현 오류로 바로 실패하게 한다.
 * FTPClient는 능동 모드 데이터 연결만 이 팩토리의 서버 소켓으로 받는다(commons-net `FTPClient._openDataConnection_`).
 * 그렇게 받은 소켓은 [AbortableSocketFactory]를 거치지 않아 쓰기 상한도, 잠금 밖 종료도 받지 않는다.
 * 그래서 지원 범위는 수동 모드다 — 능동 모드는 휴대폰이 대개 NAT 뒤에 있어 서버가 접속해 오지 못하고, 앱도 쓰지 않는다.
 */
internal class PassiveOnlyServerSocketFactory : ServerSocketFactory() {

    override fun createServerSocket(): ServerSocket = refuse()

    override fun createServerSocket(port: Int): ServerSocket = refuse()

    override fun createServerSocket(port: Int, backlog: Int): ServerSocket = refuse()

    override fun createServerSocket(port: Int, backlog: Int, ifAddress: InetAddress?): ServerSocket = refuse()

    /**
     * 능동 모드 데이터 연결을 거부한다.
     * @throws IllegalStateException 항상. 수동 모드로 바꾸지 않은 채 데이터 연결을 연 구현 오류다
     */
    private fun refuse(): Nothing =
        throw IllegalStateException("Active-mode data connections are not supported; call enterLocalPassiveMode() first")
}
