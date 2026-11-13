package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.datasource.UnexpectedConflictResolver
import com.todokanai.composepracticenew.repository.ConnectionLossNotices
import com.todokanai.composepracticenew.repository.OperationNotices
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 작업용 트리 수집([FtpCommandExecutor.collectRemoteTree])이 MLSD 지원 서버에서는 MLSD로 빈 폴더와 조회 실패를 구분하고,
 * 자신·상위 디렉터리 항목(type=cdir/pdir)을 걸러내며, 미지원 서버에서는 LIST를 쓰고 오류 응답을 실패 결과로 보는지,
 * 목록이 상위 폴더를 하위로 보여줘도 수집이 끝나는지(메모리 상한, 항목의 식별값으로 순환 감지, 경로 한도) 검증하는 단위 테스트.
 */
class FtpCommandExecutorTreeListingTest {

    private val client = FakeFtpClient()
    private val session = FtpSession(client, ConnectionLossNotices())
    private val commands = FtpCommandExecutor(session, DataCommandRunner(session))

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

        assertEquals(listOf("ftp://host/r/empty" to true, "ftp://host/r/a.txt" to false), tree.map { (it as ListedItem.Entry).entry.let { e -> e.path to e.isDirectory } })
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

        assertEquals(listOf("ftp://host/r/a", "ftp://host/r/a/x", "ftp://host/r/b", "ftp://host/r/b/x"), tree.map { (it as ListedItem.Entry).entry.path })
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

        assertEquals(listOf("ftp://host/r/a", "ftp://host/r/a/b"), tree.map { (it as ListedItem.Entry).entry.path })
    }

    @Test
    fun `시작 폴더로 돌아오는 순환은 시작 폴더의 식별값을 모르므로 한 단계 더 내려간 뒤 감지한다`() {
        loggedIn()
        client.features = setOf("MLST")
        // /r/self는 /r을 가리키는 링크 — 같은 목록이 돌아온다
        client.mlsdListingOf = { path -> if (path.startsWith("/r")) listOf("type=dir;unique=801U10; self") else null }

        val result = runBlocking { commands.collectRemoteTree("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("폴더 순환 감지: /r/self/self 은(는) 상위 폴더와 같은 폴더입니다"), result)
        assertEquals(listOf("MLSD /r", "MLSD /r/self"), client.listRequests)
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
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

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun `식별값을 보내지 않는 MLSD 서버도 경로 한도에서 멈추고 판별할 수 없음을 알린다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListingOf = { listOf("type=dir; a") }

        val result = runBlocking { commands.collectRemoteTree("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("경로가 너무 깁니다(4096바이트 이상): /r${"/a".repeat(2047)}$UNIDENTIFIABLE"), result)
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun `식별값이 모두 달라도 경로 한도에서 멈추며 판별할 수 없다는 설명은 붙이지 않는다`() {
        loggedIn()
        client.features = setOf("MLST")
        // 실제로 아주 깊은 트리 — 순환이 아니므로 식별값만으로는 멈추지 않는다
        client.mlsdListingOf = { path -> listOf("type=dir;unique=$path/a; a") }

        val result = runBlocking { commands.collectRemoteTree("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("경로가 너무 깁니다(4096바이트 이상): /r${"/a".repeat(2047)}"), result)
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun `식별값이 있는 폴더에서 경로 한도에 이르러도 경로 위에 식별값 없는 폴더가 있으면 판별할 수 없음을 알린다`() {
        loggedIn()
        client.features = setOf("MLST")
        // 식별값 없는 링크 u와 식별값 있는 폴더 yy가 번갈아 나온다 — u가 순환하는지는 식별값으로 판별할 수 없다
        client.mlsdListingOf = { path -> if (path.endsWith("/u")) listOf("type=dir;unique=$path/yy; yy") else listOf("type=dir; u") }

        val result = runBlocking { commands.collectRemoteTree("ftp://host/r") }

        // "/r" + "/u/yy" × 819 = 4097바이트 — 처음으로 한도에 이르는 경로이며, 마지막 폴더(yy)는 식별값이 있다
        assertEquals(IoResult.ItemFailed("경로가 너무 깁니다(4096바이트 이상): /r${"/u/yy".repeat(819)}$UNIDENTIFIABLE"), result)
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

            val result = runBlocking { FtpTransferrer(session, commands, DataCommandRunner(session), OperationNotices()).download(sources = listOf(DownloadSource("ftp://host/r", target.path, true)), resolveConflict = UnexpectedConflictResolver).singleItemResult() }

            assertEquals(IoResult.ItemFailed("폴더 순환 감지: /r/a/loop 은(는) 상위 폴더와 같은 폴더입니다"), result)
            assertTrue("전송을 시작하지 않아야 함", client.retrieveRequests.isEmpty())
            assertFalse("로컬 폴더를 만들지 않아야 함", target.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `들어갔다 나온 폴더의 식별값은 형제 폴더를 순환으로 보지 않도록 빠진다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf(
            "/r" to listOf("type=dir;unique=A; a", "type=dir;unique=B; b"),
            "/r/a" to emptyList(),
            // b 안의 c는 이미 나온 a를 가리키는 링크 — 상위가 아니므로 순환이 아니다
            "/r/b" to listOf("type=dir;unique=A; c"),
            "/r/b/c" to emptyList()
        )

        val tree = (runBlocking { commands.collectRemoteTree("ftp://host/r") } as IoResult.Success).value

        assertEquals(listOf("ftp://host/r/a", "ftp://host/r/b", "ftp://host/r/b/c"), tree.map { (it as ListedItem.Entry).entry.path })
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun `LIST 서버에서 상위를 가리키는 링크가 여럿이어도 메모리 상한에서 멈추고 판별할 수 없음을 알린다`() {
        loggedIn()
        // 어느 하위 경로를 조회해도 같은 두 폴더를 돌려준다 — 단계마다 방문 수가 배로 늘어난다(#257)
        client.remoteDirectories = listOf("a", "b")
        val limited = FtpCommandExecutor(session, DataCommandRunner(session), treeMemoryLimitBytes = SMALL_LIMIT_BYTES)

        val result = runBlocking { limited.collectRemoteTree("ftp://host/r") }

        val message = (result as IoResult.ItemFailed).message.orEmpty()
        assertTrue(message, message.startsWith("폴더 안의 항목이 너무 많아 처리하지 않았습니다: /r/a"))
        assertTrue(message, message.endsWith(UNIDENTIFIABLE))
        // 항목 하나의 추정치는 170바이트 이상이므로, 상한까지 받을 수 있는 항목 수(곧 목록 조회 수)는 상한 / 170을 넘지 않는다
        assertTrue(client.listRequests.toString(), client.listRequests.count { it == "LIST" } <= SMALL_LIMIT_BYTES / 170)
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun `식별값이 모두 다른 큰 트리도 메모리 상한에서 멈추며 판별할 수 없다는 설명은 붙이지 않는다`() {
        loggedIn()
        client.features = setOf("MLST")
        // 순환이 아닌 넓은 트리 — 폴더마다 하위 폴더 열 개
        client.mlsdListingOf = { path -> (0 until 10).map { "type=dir;unique=$path/d$it; d$it" } }
        val limited = FtpCommandExecutor(session, DataCommandRunner(session), treeMemoryLimitBytes = SMALL_LIMIT_BYTES)

        val result = runBlocking { limited.collectRemoteTree("ftp://host/r") }

        val message = (result as IoResult.ItemFailed).message.orEmpty()
        assertTrue(message, message.startsWith("폴더 안의 항목이 너무 많아 처리하지 않았습니다: /r/d0"))
        assertFalse(message, message.endsWith(UNIDENTIFIABLE))
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun `식별값 없는 폴더를 끝내고 나온 뒤 상한에 이르면 판별할 수 없다는 설명은 붙이지 않는다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListingOf = { path ->
            when {
                // u는 식별값이 없지만 비어 있어 끝난다 — 상한에 이른 경로(d 아래) 위에는 없다
                path == "/r" -> listOf("type=dir; u", "type=dir;unique=D; d")
                path == "/r/u" -> emptyList()
                else -> (0 until 10).map { "type=dir;unique=$path/d$it; d$it" }
            }
        }
        val limited = FtpCommandExecutor(session, DataCommandRunner(session), treeMemoryLimitBytes = SMALL_LIMIT_BYTES)

        val result = runBlocking { limited.collectRemoteTree("ftp://host/r") }

        val message = (result as IoResult.ItemFailed).message.orEmpty()
        assertTrue(message, message.startsWith("폴더 안의 항목이 너무 많아 처리하지 않았습니다: /r/d"))
        assertFalse(message, message.endsWith(UNIDENTIFIABLE))
    }

    @Test
    fun `받는 동안의 메모리 추정치가 상한 안이면 상한에 가까워도 끝까지 수집한다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf("/$DEEP_NAME" to listOf("type=file;size=1; a"))
        // 줄 하나(21바이트, 항목 경로의 앞부분 512자)를 받는 동안의 추정치: 250 + 2 × 512 + 4 × 21 = 1358바이트
        // (받은 뒤: 경로 513자, 이름 1자 → 170 + 2 × 514 = 1198바이트)
        val exact = FtpCommandExecutor(session, DataCommandRunner(session), treeMemoryLimitBytes = 1358)

        val tree = (runBlocking { exact.collectRemoteTree(DEEP_PATH) } as IoResult.Success).value

        assertEquals(listOf("$DEEP_PATH/a"), tree.map { (it as ListedItem.Entry).entry.path })
        assertEquals(
            IoResult.ItemFailed("폴더 안의 항목이 너무 많아 처리하지 않았습니다: /$DEEP_NAME"),
            runBlocking { FtpCommandExecutor(session, DataCommandRunner(session), treeMemoryLimitBytes = 1357).collectRemoteTree(DEEP_PATH) }
        )
    }

    @Test
    fun `깊은 폴더의 큰 목록은 항목 경로의 앞부분까지 세어 받는 동안 멈춘다`() {
        loggedIn()
        client.features = setOf("MLST")
        // 받은 데이터(42,000바이트)만 세면 받는 동안의 추정치(2000 × (250 + 4 × 21) = 668,000바이트)는 상한 안이라 끝까지 받고,
        // 앞부분(512자)까지 세면 줄 773개 즈음에서 상한(1MiB)을 넘는다
        client.mlsdListings = mapOf("/$DEEP_NAME" to List(2000) { "type=file;size=1; a" })
        val limited = FtpCommandExecutor(session, DataCommandRunner(session), treeMemoryLimitBytes = 1024L * 1024)

        val result = runBlocking { limited.collectRemoteTree(DEEP_PATH) }

        assertEquals(IoResult.ItemFailed("폴더 안의 항목이 너무 많아 처리하지 않았습니다: /$DEEP_NAME"), result)
        assertTrue("받는 동안 멈췄다면 목록이 남아 있어야 함", client.lastListingStream!!.available() > 0)
    }

    @Test
    fun `줄을 CR로만 끝내는 목록도 줄마다 세어 받는 동안 멈춘다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.listingLineEnd = "\r"
        // LF만 세면 받는 동안의 추정치는 4 × 40,000바이트뿐이라 끝까지 받고, CR을 줄 끝으로 세면 줄 775개 즈음에서 상한(1MiB)을 넘는다
        client.mlsdListings = mapOf("/$DEEP_NAME" to List(2000) { "type=file;size=1; a" })
        val limited = FtpCommandExecutor(session, DataCommandRunner(session), treeMemoryLimitBytes = 1024L * 1024)

        val result = runBlocking { limited.collectRemoteTree(DEEP_PATH) }

        assertEquals(IoResult.ItemFailed("폴더 안의 항목이 너무 많아 처리하지 않았습니다: /$DEEP_NAME"), result)
        assertTrue("받는 동안 멈췄다면 목록이 남아 있어야 함", client.lastListingStream!!.available() > 0)
    }

    @Test
    fun `목록 하나가 받는 동안 메모리 상한을 넘으면 끝까지 받지 않고 그 폴더를 실패로 알리며 세션은 유지한다`() {
        loggedIn()
        client.features = setOf("MLST")
        // 한 폴더의 항목이 아주 많다 — 받는 동안의 추정치는 줄마다 250 + 2 × 13(항목 경로의 앞부분) + 4 × 24바이트
        client.mlsdListings = mapOf("/r" to (0 until 1000).map { "type=file;size=1; f$it" })
        val limited = FtpCommandExecutor(session, DataCommandRunner(session), treeMemoryLimitBytes = SMALL_LIMIT_BYTES)

        val result = runBlocking { limited.collectRemoteTree("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("폴더 안의 항목이 너무 많아 처리하지 않았습니다: /r"), result)
        assertTrue("데이터 연결만 실패했으므로 세션은 유지돼야 함", runBlocking { session.isReady() })
    }

    @Test
    fun `받는 동안의 메모리 상한은 받기 시작할 때 남은 상한이다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf(
            // 하위 폴더 b의 목록은 혼자서는 상한 안이지만, 앞서 받은 a의 목록과 합치면 넘는다
            "/r" to listOf("type=dir;unique=A; a", "type=dir;unique=B; b"),
            "/r/a" to (0 until 20).map { "type=file;size=1; f$it" },
            "/r/b" to (0 until 20).map { "type=file;size=1; f$it" }
        )
        // a의 목록 20개를 받은 뒤의 추정치는 약 4.6KiB, b를 받는 동안의 추정치는 약 7KiB
        val limited = FtpCommandExecutor(session, DataCommandRunner(session), treeMemoryLimitBytes = 10L * 1024)

        val result = runBlocking { limited.collectRemoteTree("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("폴더 안의 항목이 너무 많아 처리하지 않았습니다: /r/b"), result)
    }

    @Test
    fun `메모리 상한이 0 이하면 명령 실행기를 만들지 않는다`() {
        assertThrows(IllegalArgumentException::class.java) { FtpCommandExecutor(session, DataCommandRunner(session), treeMemoryLimitBytes = 0) }
        assertThrows(IllegalArgumentException::class.java) { FtpCommandExecutor(session, DataCommandRunner(session), treeMemoryLimitBytes = -1) }
    }

    @Test
    fun `화면 목록에는 트리 수집의 메모리 상한을 두지 않는다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf("/r" to (0 until 1000).map { "type=file;size=1; f$it" })
        val limited = FtpCommandExecutor(session, DataCommandRunner(session), treeMemoryLimitBytes = SMALL_LIMIT_BYTES)

        val listing = runBlocking { limited.listFiles("ftp://host/r") }

        assertEquals(1000, (listing as IoResult.Success).value.size)
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun `메모리 상한에 이른 폴더는 하위 항목을 하나도 지우지 않고 실패를 알린다`() {
        loggedIn()
        client.remoteDirectories = listOf("a", "b")
        client.remoteFiles = listOf("x.txt")
        val limited = FtpCommandExecutor(session, DataCommandRunner(session), treeMemoryLimitBytes = SMALL_LIMIT_BYTES)

        val result = runBlocking { limited.removeDirectoryRecursive("ftp://host/r") }

        assertTrue(result.toString(), result is IoResult.ItemFailed)
        assertEquals(emptyList<String>(), client.deleteRequests)
        assertEquals(emptyList<String>(), client.removeDirectoryRequests)
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
    private fun loggedIn() = runBlocking { session.markLoggedIn(session.closeRequestMark(), home = null, serverName = "server") }

    private companion object {
        /** 식별값 없이 경로 한도나 메모리 상한에서 멈췄을 때 실패 메시지에 붙는 설명. */
        const val UNIDENTIFIABLE = " — 이 서버의 목록으로는 폴더가 순환하는지 판별할 수 없습니다"
        /** 메모리 상한 테스트가 쓰는 작은 상한(바이트). 기본 상한(32MiB)까지 조회하면 테스트가 오래 걸린다. */
        const val SMALL_LIMIT_BYTES = 16L * 1024
        /**
         * 끝없는 트리를 수집하는 테스트의 시간 제한(밀리초). 멈추는 장치(경로 한도·메모리 상한)가 회귀하면 테스트가 실패하지 않고
         * 끝나지 않으므로 시간으로 실패시킨다. 장치가 동작하면 순식간에 끝난다.
         */
        const val TEST_TIMEOUT_MILLIS = 10_000L
        /** 경로가 긴 폴더의 이름(500자). 항목 경로의 앞부분이 길어 받는 동안의 메모리 추정치에서 앞부분의 몫이 받은 데이터의 몫보다 크다. */
        val DEEP_NAME = "p".repeat(500)
        /** [DEEP_NAME] 폴더의 경로. */
        val DEEP_PATH = "ftp://host/$DEEP_NAME"
    }
}
