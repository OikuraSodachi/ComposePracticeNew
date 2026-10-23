package com.todokanai.composepracticenew.data.ftp

import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.net.SocketFactory

/**
 * 만든 소켓을 기억해 두었다가 다른 스레드에서 한꺼번에 닫을 수 있게 하는 소켓 팩토리.
 * FTPClient는 제어 연결과 수동 모드 데이터 연결을 모두 설정된 소켓 팩토리로 만든다(commons-net `SocketClient._connect`, `FTPClient._openDataConnection_`).
 * 소켓을 닫으면 그 소켓에서 I/O를 기다리던 스레드는 SocketException을 받는다([Socket.close]).
 * FTPClient의 `disconnect()`는 내부 스트림 필드를 null로 바꿔 다른 스레드의 명령이 NullPointerException으로 끝나게 하므로, 다른 스레드에서는 이 팩토리로 소켓만 닫는다.
 */
internal class AbortableSocketFactory : SocketFactory() {

    /** 만든 소켓 중 아직 닫지 않은 소켓. 명령 스레드가 더하고 종료 요청 스레드가 닫으므로 동시성 집합으로 둔다. */
    private val sockets: MutableSet<Socket> = ConcurrentHashMap.newKeySet()

    override fun createSocket(): Socket = track(Socket())

    override fun createSocket(host: String, port: Int): Socket = track(Socket(host, port))

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        track(Socket(host, port, localHost, localPort))

    override fun createSocket(host: InetAddress, port: Int): Socket = track(Socket(host, port))

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        track(Socket(address, port, localAddress, localPort))

    /** 지금까지 만든 소켓을 모두 닫는다. 그 소켓에서 I/O를 기다리던 스레드는 SocketException을 받는다. */
    fun closeAll() {
        sockets.removeIf { socket ->
            runCatching { socket.close() }
            true
        }
    }

    /**
     * [socket]을 닫을 대상으로 기억한다. 이미 닫힌 소켓은 함께 잊어 목록 조회·전송마다 데이터 소켓이 쌓이지 않게 한다.
     * @param socket 새로 만든 소켓
     * @return [socket]
     */
    private fun track(socket: Socket): Socket {
        sockets.removeIf { it.isClosed }
        sockets += socket
        return socket
    }
}
