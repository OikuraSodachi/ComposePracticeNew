package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.apache.commons.net.ftp.FTPConnectionClosedException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.net.SocketTimeoutException
import java.nio.file.Files

/**
 * 서버 동작 × FTP 명령 계약 테스트 행렬. 모든 명령이 같은 서버 동작에 같은 의미의 결과를 내는지 검증한다.
 * 새 명령은 [commands]에 한 줄을, 새 서버 동작은 [behaviors]에 한 줄과 [expectedOutcome]의 한 갈래를 더하면 모든 조합이 검증된다.
 * @param command 실행할 명령 행
 * @param behavior 그 명령에 서버가 보일 동작
 */
@RunWith(Parameterized::class)
class FtpCommandContractTest internal constructor(private val command: CommandRow, private val behavior: BehaviorColumn) {

    /** 전송 명령이 쓸 로컬 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("ftp-contract-test").toFile()
    /** 서버 동작을 재현하는 가짜 클라이언트. */
    private val client = FakeFtpClient()
    /** 검증 대상 세션. */
    private val session = FtpSession(client)
    /** 검증 대상 파일 명령 실행기. */
    private val executor = FtpCommandExecutor(session)
    /** 검증 대상 전송기. */
    private val transferrer = FtpTransferrer(session, executor)

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `서버 동작에 맞는 결과를 내고 세션 유지 여부를 지킨다`() {
        command.prepare(client)
        behavior.serverBehavior?.let { client.serverBehaviors = mapOf(command.ftpName to it) }
        if (behavior != BehaviorColumn.NOT_CONNECTED) runBlocking { session.markLoggedIn(session.closeRequestMark(), home = null) }
        val run = suspend { command.run(Fixture(executor, transferrer, dir)) }

        when (expectedOutcome(command, behavior)) {
            Outcome.ITEM_FAILED -> {
                val result = runBlocking { run() }
                assertTrue("$result", result is IoResult.ItemFailed)
                val text = (behavior.serverBehavior as ServerBehavior.Refuse).text
                assertEquals("${FtpCommand.valueOf(command.ftpName).label} 실패: $text${command.refusalNote}", (result as IoResult.ItemFailed).message)
                assertEquals("거부 응답 뒤에는 기다릴 응답이 없다", 0, client.completePendingCalls)
                assertSessionKept()
            }
            Outcome.CONNECTION_LOST -> {
                assertTrue(runBlocking { run() } is IoResult.ConnectionLost)
                assertSessionEnded()
            }
            Outcome.NOT_SENT -> {
                assertTrue(runBlocking { run() } is IoResult.ConnectionLost)
                assertEquals(emptyList<String>(), client.sentCommands)
            }
            Outcome.THROWS -> {
                assertThrows(IllegalStateException::class.java) { runBlocking { run() } }
                assertSessionEnded()
            }
        }
    }

    /** 세션이 유지되고 소켓을 닫지 않았는지 확인한다. */
    private fun assertSessionKept() {
        assertTrue(runBlocking { session.isReady() })
        assertEquals(0, client.disconnectCalls)
    }

    /** 세션이 끊김 상태가 되고 소켓이 닫혔는지 확인한다. */
    private fun assertSessionEnded() {
        assertFalse(runBlocking { session.isReady() })
        assertEquals(1, client.disconnectCalls)
    }

    /**
     * 명령 행이 실행에 쓰는 대상.
     * @param executor FTP 파일 명령 실행기
     * @param transferrer FTP 전송기
     * @param dir 전송에 쓸 로컬 임시 디렉터리
     */
    internal class Fixture(val executor: FtpCommandExecutor, val transferrer: FtpTransferrer, val dir: File)

    /**
     * 행렬의 명령 행.
     * @param ftpName [FakeFtpClient.serverBehaviors]의 명령 이름
     * @param name 테스트 이름에 표시할 행 이름. 같은 명령을 다른 용도로 쓰는 행을 구분한다
     * @param refusalNote 거부 메시지에서 서버 응답 뒤에 붙는 설명. 원인을 응답만으로 알 수 없는 거부에만 붙는다([FtpReplyRules.refusal])
     * @param prepare 명령이 그 단계까지 정상 진행되도록 가짜 클라이언트를 준비하는 함수
     * @param run 공개 API로 명령을 실행하는 함수
     */
    internal class CommandRow(
        val ftpName: String,
        val name: String = ftpName,
        val refusalNote: String = "",
        val prepare: (FakeFtpClient) -> Unit = {},
        val run: suspend (Fixture) -> IoResult<*>
    ) {
        override fun toString(): String = name
    }

    /**
     * 행렬의 서버 동작 열.
     * @param serverBehavior 명령에 걸 서버 동작. null이면 걸지 않는다
     */
    internal enum class BehaviorColumn(val serverBehavior: ServerBehavior?) {
        REFUSED_450(ServerBehavior.Refuse(450, "450 Requested file action not taken")),
        REFUSED_550(ServerBehavior.Refuse(550, "550 Requested action not taken")),
        CONNECTION_CLOSED(ServerBehavior.Fail(FTPConnectionClosedException("Connection closed without indication."))),
        REPLY_TIMEOUT(ServerBehavior.Fail(SocketTimeoutException("Read timed out"))),
        UNEXPECTED_EXCEPTION(ServerBehavior.Fail(IllegalStateException("구현 오류"))),
        NOT_CONNECTED(null),
    }

    /** 기대하는 결과 종류. */
    internal enum class Outcome { ITEM_FAILED, CONNECTION_LOST, NOT_SENT, THROWS }

    companion object {
        /** 모든 명령 행. */
        private val commands = listOf(
            CommandRow("LIST") { it.executor.listFiles("ftp://host/r") },
            CommandRow("LIST", name = "LIST(트리 수집)") { it.executor.collectRemoteTree("ftp://host/r") },
            // LIST 서버는 디렉터리로 이동한 뒤 LIST를 보낸다
            CommandRow("CWD", name = "CWD(화면 목록)") { it.executor.listFiles("ftp://host/r") },
            CommandRow("CWD", name = "CWD(트리 수집)") { it.executor.collectRemoteTree("ftp://host/r") },
            CommandRow("MLSD", prepare = { it.features = setOf("MLST"); it.mlsdListings = mapOf("/r" to emptyList()) }) {
                it.executor.listFiles("ftp://host/r")
            },
            CommandRow("RENAME") { it.executor.rename("ftp://host/r/a.txt", "ftp://host/r/b.txt") },
            CommandRow("DELE") { it.executor.deleteFile("ftp://host/r/a.txt") },
            CommandRow("MKD") { it.executor.makeDirectory("ftp://host/r/new") },
            // MLSD 목록은 숨김 항목을 빠뜨릴 수 있어, 그 항목이 남아 거부됐을 가능성을 덧붙인다
            CommandRow(
                "RMD",
                refusalNote = FtpReplyRules.HIDDEN_ENTRIES_NOTE,
                prepare = { it.features = setOf("MLST"); it.mlsdListings = mapOf("/r/d" to emptyList()) }
            ) {
                it.executor.removeDirectoryRecursive("ftp://host/r/d")
            },
            CommandRow("RETR", prepare = { it.streamContent = "data".toByteArray() }) {
                it.transferrer.download("ftp://host/r/a.txt", File(it.dir, "a.txt").path)
            },
            CommandRow("STOR", prepare = { it.streamContent = "data".toByteArray() }) {
                val local = File(it.dir, "a.txt").apply { writeText("A") }
                it.transferrer.upload(local.path, "ftp://host/r/a.txt")
            },
        )

        /** 모든 서버 동작 열. */
        private val behaviors = BehaviorColumn.entries

        /**
         * 명령과 서버 동작의 기대 결과 — 이 행렬이 검증하는 계약이다.
         * @param command 명령 행
         * @param behavior 서버 동작
         * @return 기대 결과
         */
        private fun expectedOutcome(command: CommandRow, behavior: BehaviorColumn): Outcome = when (behavior) {
            BehaviorColumn.REFUSED_450, BehaviorColumn.REFUSED_550 -> Outcome.ITEM_FAILED
            BehaviorColumn.CONNECTION_CLOSED, BehaviorColumn.REPLY_TIMEOUT -> Outcome.CONNECTION_LOST
            BehaviorColumn.UNEXPECTED_EXCEPTION -> Outcome.THROWS
            BehaviorColumn.NOT_CONNECTED -> Outcome.NOT_SENT
        }

        /** 명령 행 × 서버 동작 열의 모든 조합. */
        @JvmStatic
        @Parameterized.Parameters(name = "{0} × {1}")
        fun parameters(): List<Array<Any>> = commands.flatMap { command -> behaviors.map { arrayOf(command, it) } }
    }
}
