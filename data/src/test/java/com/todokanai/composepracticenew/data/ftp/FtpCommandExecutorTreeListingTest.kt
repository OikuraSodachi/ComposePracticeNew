package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 작업용 트리 수집([FtpCommandExecutor.collectRemoteTree])이 MLSD 지원 서버에서는 MLSD로 빈 폴더와 조회 실패를 구분하고,
 * 자신·상위 디렉터리 항목(type=cdir/pdir)을 걸러내며, 미지원 서버에서는 LIST를 쓰고 오류 응답을 실패 결과로 보는지,
 * 목록이 상위 폴더를 하위로 보여줘도 수집이 끝나는지(항목·자신 항목의 식별값으로 순환 감지, 경로 한도) 검증하는 단위 테스트.
 */
class FtpCommandExecutorTreeListingTest {

    private val client = FakeFtpClient()
    private val session = FtpSession(client)
    private val commands = FtpCommandExecutor(session)

    @Test
    fun `MLSD 서버에서 빈 하위 폴더는 실패가 아닌 빈 폴더로 수집하고 자신·상위 항목은 걸러낸다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf(
            // 서버에 따라 cdir·pdir 이름이 "."이 아닌 전체 경로로 온다
            "/r" to listOf("type=cdir; /r", "type=pdir; /", "type=dir; empty", "type=file;size=3; a.txt"),
            "/r/empty" to listOf("type=cdir; /r/empty", "type=pdir; /r")
        )

        val tree = (runBlocking { commands.collectRemoteTree("ftp://host/r") } as IoResult.Success).value

        assertEquals(listOf("ftp://host/r/empty" to true, "ftp://host/r/a.txt" to false), tree.map { it.path to it.isDirectory })
        assertEquals(listOf("MLSD /r", "MLSD /r/empty"), client.listRequests)
    }

    @Test
    fun `MLSD 서버에서 하위 폴더 조회가 550이면 일부만 수집하지 않고 실패를 알린다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf("/r" to listOf("type=dir; locked"))

        val result = runBlocking { commands.collectRemoteTree("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("MLSD 실패: 550 No such directory"), result)
        assertTrue("서버 오류 응답이므로 세션은 유지돼야 함", runBlocking { session.isReady() })
    }

    @Test
    fun `MLSD 서버에서 빈 하위 폴더가 있는 폴더를 삭제할 수 있다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf(
            "/r" to listOf("type=dir; empty", "type=file;size=3; a.txt"),
            "/r/empty" to emptyList()
        )

        assertEquals(IoResult.Done, runBlocking { commands.removeDirectoryRecursive("ftp://host/r") })

        assertEquals(listOf("/r/a.txt"), client.deleteRequests)
    }

    @Test
    fun `MLST를 알리지 않는 서버는 LIST를 쓰고 오류 응답을 실패로 본다`() {
        loggedIn()
        client.serverBehaviors = mapOf("LIST" to ServerBehavior.Refuse(550, "550 No such directory"))

        val result = runBlocking { commands.collectRemoteTree("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("LIST 실패: 550 No such directory"), result)
        assertEquals(listOf("CWD /r", "LIST"), client.listRequests)
    }

    @Test
    fun `LIST 서버에서 디렉터리 이동이 거부되면 LIST를 보내지 않고 실패로 본다`() {
        loggedIn()
        client.serverBehaviors = mapOf("CWD" to ServerBehavior.Refuse(550, "550 No such directory"))

        val result = runBlocking { commands.collectRemoteTree("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("CWD 실패: 550 No such directory"), result)
        assertEquals(listOf("CWD /r"), client.listRequests)
    }

    @Test
    fun `MLSD 서버가 상위 폴더와 같은 식별값의 하위 폴더를 보여주면 순환으로 보고 더 내려가지 않는다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf(
            "/r" to listOf("type=dir;unique=801U10; a", "type=file;size=3; a.txt"),
            // 디렉터리로 보고된 링크 — 대상(/r/a)의 식별값을 가진다
            "/r/a" to listOf("type=dir;unique=801U10; loop")
        )

        val result = runBlocking { commands.collectRemoteTree("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("폴더 순환 감지: /r/a/loop 은(는) 상위 폴더와 같은 폴더입니다"), result)
        assertEquals(listOf("MLSD /r", "MLSD /r/a"), client.listRequests)
    }

    @Test
    fun `MLSD 서버에서 상위가 아닌 폴더와 식별값이 같은 폴더는 순환이 아니므로 수집한다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf(
            // b는 형제 폴더 a를 가리키는 링크 — 같은 폴더를 두 번 거쳐도 순환은 아니다
            "/r" to listOf("type=dir;unique=801U10; a", "type=dir;unique=801U10; b"),
            "/r/a" to listOf("type=file;size=3;unique=801U11; x"),
            "/r/b" to listOf("type=file;size=3;unique=801U11; x")
        )

        val tree = (runBlocking { commands.collectRemoteTree("ftp://host/r") } as IoResult.Success).value

        assertEquals(listOf("ftp://host/r/a", "ftp://host/r/a/x", "ftp://host/r/b", "ftp://host/r/b/x"), tree.map { it.path })
    }

    @Test
    fun `식별값은 대소문자를 구분한다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf(
            "/r" to listOf("type=dir;unique=AbC; a"),
            "/r/a" to listOf("type=dir;UNIQUE=abc; b"),
            "/r/a/b" to emptyList()
        )

        val tree = (runBlocking { commands.collectRemoteTree("ftp://host/r") } as IoResult.Success).value

        assertEquals(listOf("ftp://host/r/a", "ftp://host/r/a/b"), tree.map { it.path })
    }

    @Test
    fun `시작 폴더로 돌아오는 순환은 시작 폴더의 자신 항목 식별값으로 바로 감지한다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf("/r" to listOf("type=cdir;unique=801U10; /r", "type=dir;unique=801U10; self"))

        val result = runBlocking { commands.collectRemoteTree("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("폴더 순환 감지: /r/self 은(는) 상위 폴더와 같은 폴더입니다"), result)
        assertEquals(listOf("MLSD /r"), client.listRequests)
    }

    @Test
    fun `시작 폴더의 자신 항목이 없으면 시작 폴더로 돌아오는 순환을 한 단계 더 내려간 뒤 감지한다`() {
        loggedIn()
        client.features = setOf("MLST")
        // /r/self는 /r을 가리키는 링크 — 같은 목록이 돌아온다
        client.mlsdListingOf = { path -> if (path.startsWith("/r")) listOf("type=dir;unique=801U10; self") else null }

        val result = runBlocking { commands.collectRemoteTree("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("폴더 순환 감지: /r/self/self 은(는) 상위 폴더와 같은 폴더입니다"), result)
        assertEquals(listOf("MLSD /r", "MLSD /r/self"), client.listRequests)
    }

    @Test
    fun `LIST 서버가 하위 경로마다 같은 하위 폴더를 보여주면 경로 한도에서 멈추고 판별할 수 없음을 알린다`() {
        loggedIn()
        // 서버 오류로 어느 하위 경로를 조회해도 같은 목록을 돌려준다 — 이름이 한 글자라 한도까지 가장 깊이 내려간다
        client.remoteDirectories = listOf("a")

        val result = runBlocking { commands.collectRemoteTree("ftp://host/r") }

        // "/r" + "/a" × 2047 = 4096바이트 — 처음으로 한도에 이르는 경로
        val tooLong = "/r" + "/a".repeat(2047)
        assertEquals(IoResult.ItemFailed("경로가 너무 깁니다(4096바이트 이상): $tooLong$UNIDENTIFIABLE"), result)
        assertEquals(2047, client.listRequests.count { it == "LIST" })
    }

    @Test
    fun `식별값을 보내지 않는 MLSD 서버도 경로 한도에서 멈추고 판별할 수 없음을 알린다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListingOf = { listOf("type=dir; a") }

        val result = runBlocking { commands.collectRemoteTree("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("경로가 너무 깁니다(4096바이트 이상): /r${"/a".repeat(2047)}$UNIDENTIFIABLE"), result)
    }

    @Test
    fun `식별값이 모두 달라도 경로 한도에서 멈추며 판별할 수 없다는 설명은 붙이지 않는다`() {
        loggedIn()
        client.features = setOf("MLST")
        // 실제로 아주 깊은 트리 — 순환이 아니므로 식별값만으로는 멈추지 않는다
        client.mlsdListingOf = { path -> listOf("type=dir;unique=$path/a; a") }

        val result = runBlocking { commands.collectRemoteTree("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("경로가 너무 깁니다(4096바이트 이상): /r${"/a".repeat(2047)}"), result)
    }

    @Test
    fun `순환한 폴더는 다운로드하지 않고 로컬 폴더도 만들지 않는다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.streamContent = "data".toByteArray()
        client.mlsdListings = mapOf(
            "/r" to listOf("type=dir;unique=801U10; a", "type=file;size=3; a.txt"),
            "/r/a" to listOf("type=dir;unique=801U10; loop")
        )
        val dir = Files.createTempDirectory("ftp-tree-listing-test").toFile()
        try {
            val target = File(dir, "r")

            val result = runBlocking { FtpTransferrer(session, commands).download("ftp://host/r", target.path, isDirectory = true) }

            assertEquals(IoResult.ItemFailed("폴더 순환 감지: /r/a/loop 은(는) 상위 폴더와 같은 폴더입니다"), result)
            assertTrue("전송을 시작하지 않아야 함", client.retrieveRequests.isEmpty())
            assertFalse("로컬 폴더를 만들지 않아야 함", target.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `항목에는 링크 자신의 식별값을 보내도 들어간 폴더의 자신 항목 식별값으로 순환을 감지한다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf(
            "/r" to listOf("type=cdir;unique=801U10; /r", "type=dir;unique=801U99; loop"),
            // loop는 /r을 가리키는 링크 — 상위 목록의 항목은 링크 자신의 값(801U99), 들어간 목록의 자신 항목은 대상의 값(801U10)
            "/r/loop" to listOf("type=cdir;unique=801U10; /r/loop", "type=dir;unique=801U99; loop")
        )

        val result = runBlocking { commands.collectRemoteTree("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("폴더 순환 감지: /r/loop 은(는) 상위 폴더와 같은 폴더입니다"), result)
        assertEquals(listOf("MLSD /r", "MLSD /r/loop"), client.listRequests)
    }

    @Test
    fun `들어갔다 나온 폴더의 식별값은 형제 폴더를 순환으로 보지 않도록 빠진다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf(
            "/r" to listOf("type=dir;unique=A; a", "type=dir;unique=B; b"),
            "/r/a" to listOf("type=cdir;unique=A2; /r/a"),
            // b 안의 c는 이미 나온 a의 자신 항목 값과 같다 — 상위가 아니므로 순환이 아니다
            "/r/b" to listOf("type=dir;unique=A2; c"),
            "/r/b/c" to emptyList()
        )

        val tree = (runBlocking { commands.collectRemoteTree("ftp://host/r") } as IoResult.Success).value

        assertEquals(listOf("ftp://host/r/a", "ftp://host/r/b", "ftp://host/r/b/c"), tree.map { it.path })
    }

    @Test
    fun `순환한 폴더는 하위 항목을 하나도 지우지 않고 실패를 알린다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf(
            "/r" to listOf("type=dir;unique=801U10; a", "type=file;size=3; a.txt"),
            "/r/a" to listOf("type=dir;unique=801U10; loop")
        )

        val result = runBlocking { commands.removeDirectoryRecursive("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("폴더 순환 감지: /r/a/loop 은(는) 상위 폴더와 같은 폴더입니다"), result)
        assertEquals(emptyList<String>(), client.deleteRequests)
        assertEquals(emptyList<String>(), client.removeDirectoryRequests)
    }

    /** 세션을 로그인 완료 상태로 만든다. */
    private fun loggedIn() = runBlocking { session.markLoggedIn(session.closeRequestMark(), home = null) }

    private companion object {
        /** 식별값 없이 경로 한도에서 멈췄을 때 실패 메시지에 붙는 설명. */
        const val UNIDENTIFIABLE = " — 이 서버의 목록으로는 폴더가 순환하는지 판별할 수 없습니다"
    }
}
