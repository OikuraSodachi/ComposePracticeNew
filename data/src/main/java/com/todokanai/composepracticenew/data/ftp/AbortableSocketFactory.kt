package com.todokanai.composepracticenew.data.ftp

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.net.SocketFactory

/**
 * 만든 소켓을 기억해 두었다가 다른 스레드에서 한꺼번에 닫을 수 있게 하는 소켓 팩토리.
 * FTPClient는 제어 연결과 수동 모드 데이터 연결을 모두 설정된 소켓 팩토리로 만든다(commons-net `SocketClient._connect`, `FTPClient._openDataConnection_`).
 * 소켓을 닫으면 그 소켓에서 I/O를 기다리던 스레드는 SocketException을 받는다([Socket.close]).
 * FTPClient의 `disconnect()`는 내부 스트림 필드를 null로 바꿔 다른 스레드의 명령이 NullPointerException으로 끝나게 하므로, 다른 스레드에서는 이 팩토리로 소켓만 닫는다.
 * 만드는 소켓은 쓰기마다 제한 시간이 걸린 [WriteBoundedSocket]이다 — 읽기 제한 시간은 FTPClient가 소켓마다 SO_TIMEOUT으로 건다.
 * @param writeTimeoutMillis 만드는 소켓의 쓰기 한 번이 끝나기를 기다리는 상한(ms)
 */
internal class AbortableSocketFactory(private val writeTimeoutMillis: Long) : SocketFactory() {

    /** 만든 소켓 중 아직 닫지 않은 소켓. 명령 스레드가 더하고 종료 요청 스레드가 닫으므로 동시성 집합으로 둔다. */
    private val sockets: MutableSet<WriteBoundedSocket> = ConcurrentHashMap.newKeySet()
    /** [capturing] 중인 스레드에서 만든 소켓을 모으는 목록. 스레드마다 따로 두어 다른 스레드가 만든 소켓은 섞이지 않는다. */
    private val captured = ThreadLocal<MutableList<WriteBoundedSocket>>()

    override fun createSocket(): Socket = track(WriteBoundedSocket(writeTimeoutMillis))

    override fun createSocket(host: String, port: Int): Socket = connected(InetSocketAddress(host, port))

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        connected(InetSocketAddress(host, port), InetSocketAddress(localHost, localPort))

    override fun createSocket(host: InetAddress, port: Int): Socket = connected(InetSocketAddress(host, port))

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        connected(InetSocketAddress(address, port), InetSocketAddress(localAddress, localPort))

    /**
     * 지금까지 만든 소켓을 모두 중단으로 닫는다([WriteBoundedSocket.abort]). 그 소켓에서 I/O를 기다리던 스레드는 SocketException을 받는다.
     * 진행 중인 명령을 끊는 닫기이므로, 업로드 중인 데이터 연결이 서버에 파일 끝으로 읽히지 않게 한다.
     */
    fun closeAll() {
        sockets.removeIf { socket ->
            socket.abort()
            true
        }
    }

    /**
     * [open]을 실행하는 동안 이 스레드에서 만든 소켓을 모아 [open]의 결과와 함께 돌려준다.
     * 전송 명령이 데이터 스트림을 열며 만든 데이터 소켓을 집어내는 데 쓴다 — FTPClient는 데이터 소켓을 내주지 않는다.
     * @param open 소켓을 만들 수 있는 동작. 스레드를 바꾸지 않고 끝까지 실행된다
     * @return [open]의 결과와, 그동안 만든 소켓
     */
    fun <T> capturing(open: () -> T): Pair<T, List<WriteBoundedSocket>> {
        val opened = mutableListOf<WriteBoundedSocket>()
        captured.set(opened)
        try {
            return open() to opened
        } finally {
            captured.remove()
        }
    }

    /**
     * 새 소켓을 만들어 [remote]에 연결한다. 연결하는 생성자(`Socket(host, port)` 등)와 같은 일을 [createSocket]으로 만든 소켓으로 한다.
     * 연결하지 못하면 소켓을 닫고 예외를 전파한다.
     * @param remote 연결할 주소
     * @param local 묶을 로컬 주소. null이면 묶지 않는다
     * @return 연결된 소켓
     * @throws java.io.IOException 묶거나 연결하지 못한 경우
     */
    private fun connected(remote: InetSocketAddress, local: InetSocketAddress? = null): Socket {
        val socket = createSocket()
        try {
            if (local != null) socket.bind(local)
            socket.connect(remote)
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
        return socket
    }

    /**
     * [socket]을 닫을 대상으로 기억하고, [capturing] 중이면 그 목록에도 넣는다. 이미 닫힌 소켓은 함께 잊어 목록 조회·전송마다 데이터 소켓이 쌓이지 않게 한다.
     * @param socket 새로 만든 소켓
     * @return [socket]
     */
    private fun track(socket: WriteBoundedSocket): Socket {
        sockets.removeIf { it.isClosed }
        sockets += socket
        captured.get()?.add(socket)
        return socket
    }
}
