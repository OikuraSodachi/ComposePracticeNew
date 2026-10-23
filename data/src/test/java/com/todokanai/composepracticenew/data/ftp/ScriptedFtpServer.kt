package com.todokanai.composepracticenew.data.ftp

import java.io.Closeable
import java.io.Writer
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * 제어 연결 하나를 받아 정해진 대로 응답하는 테스트용 FTP 서버. 실제 commons-net FTPClient의 동작을 검증하는 데 쓴다.
 * 로그인(USER·PASS)·TYPE·PASV·SYST(Unix)에는 기본 응답을 보내고, [script]에 있는 명령은 그 동작으로 응답하며, 나머지는 502로 거부한다.
 * QUIT에는 221로 답하고 연결을 닫는다.
 * @param script 명령 이름(대문자) → 그 명령을 받았을 때의 응답 동작. 기본 응답보다 우선한다
 */
internal class ScriptedFtpServer(script: Map<String, (Exchange) -> Unit> = emptyMap()) : Closeable {

    /**
     * 명령 하나에 응답하는 데 쓰는 서버 쪽 연결.
     * @param data PASV로 알린 데이터 연결을 받을 소켓
     * @param writer 제어 연결에 응답을 쓰는 Writer
     */
    class Exchange(val data: ServerSocket, private val writer: Writer) {
        /** 응답 한 줄을 CRLF로 끝맺어 보낸다. */
        fun send(line: String) {
            writer.write("$line\r\n")
            writer.flush()
        }
    }

    /** 제어 연결을 받을 소켓. */
    private val control = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    /** PASV로 알린 데이터 연결을 받을 소켓. */
    private val data = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    /** 기본 응답에 [script]를 덮어쓴 명령별 응답 동작. */
    private val handlers: Map<String, (Exchange) -> Unit> = defaultHandlers() + script
    private val _received = CopyOnWriteArrayList<String>()
    /** 받은 명령 이름(대문자). 받은 순서대로 쌓는다. */
    val received: List<String> get() = _received
    /** 접속할 호스트 주소. */
    val host: String get() = control.inetAddress.hostAddress
    /** 제어 연결 포트. */
    val port: Int get() = control.localPort
    /** 제어 연결을 처리하는 스레드. */
    private val worker = thread(isDaemon = true) { runCatching { serve() } }

    /** 제어 연결 하나를 받아 명령마다 응답한다. */
    private fun serve() {
        control.accept().use { socket ->
            val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
            val exchange = Exchange(data, socket.getOutputStream().bufferedWriter(Charsets.US_ASCII))
            exchange.send("220 ready")
            while (true) {
                val command = reader.readLine()?.substringBefore(' ')?.uppercase() ?: return
                _received.add(command)
                if (command == "QUIT") return exchange.send("221 Bye")
                handlers[command]?.invoke(exchange) ?: exchange.send("502 Command not implemented")
            }
        }
    }

    /** 로그인·전송 방식·수동 모드·시스템 종류에 대한 기본 응답. */
    private fun defaultHandlers(): Map<String, (Exchange) -> Unit> = mapOf(
        "USER" to { it.send("331 Password required") },
        "PASS" to { it.send("230 Logged in") },
        "TYPE" to { it.send("200 Type set") },
        "SYST" to { it.send("215 UNIX Type: L8") },
        "PASV" to { it.send("227 Entering Passive Mode (127,0,0,1,${data.localPort / 256},${data.localPort % 256})") },
    )

    override fun close() {
        control.close()
        data.close()
        worker.join(TIMEOUT_MILLIS.toLong())
    }

    companion object {
        /** 연결·응답 대기 제한 시간(ms). 서버가 응답하지 않아도 테스트가 멈추지 않게 한다. */
        const val TIMEOUT_MILLIS = 5_000
    }
}
