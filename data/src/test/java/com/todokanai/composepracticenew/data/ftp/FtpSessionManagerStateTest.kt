package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.RemoteConnectionState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * [FtpSessionManager.connectionState]가 탐색 세션의 예기치 않은 끊김만 [RemoteConnectionState.Lost]로 남기고 끊김 알림을 한 번 넣으며,
 * 앱이 요청한 종료·서버 전환·새 연결 시도와 구분하는지 검증하는 통합 테스트.
 * 끊김이 상태로 남아야 끊긴 뒤에 관찰을 시작한 쪽(다시 만들어진 화면 등)도 끊김을 안다.
 */
class FtpSessionManagerStateTest {

    /** 세션 관리자로 테스트 서버에 접속시키는 도구. */
    private val fixture = SessionManagerFixture()
    /** 검증 대상. */
    private val manager = fixture.manager
    /** 목록 조회(LIST)를 받으면 응답하지 않고 연결을 닫는 서버. */
    private val hangingUpOnList = fixture.server(mapOf("LIST" to { it.hangUp() }))
    /** 목록 조회를 거부하는 다른 서버. */
    private val other = fixture.server(mapOf("LIST" to { it.send("550 No such directory") }))

    @After
    fun tearDown() = fixture.close()

    @Test
    fun `탐색 세션의 명령이 응답을 읽지 못하면 끊김으로 남고 끊김을 한 번 알린다`() {
        fixture.connect(hangingUpOnList)

        val listing = list()

        assertTrue(listing.toString(), listing is IoResult.ConnectionLost)
        assertEquals(RemoteConnectionState.Lost, manager.connectionState.value)
        assertNull(manager.current())
        assertEquals(1, fixture.notices.pending.value.size)
    }

    @Test
    fun `앱이 종료를 요청한 세션은 끊김이 아니다`() {
        fixture.connect(hangingUpOnList)

        runBlocking { manager.disconnect() }

        assertEquals(RemoteConnectionState.Disconnected, manager.connectionState.value)
        assertEquals(0, fixture.notices.pending.value.size)
    }

    @Test
    fun `앱이 연결을 더 유지할 수 없어 끝낸 세션은 끊김으로 남고 끊김을 알린다`() {
        fixture.connect(hangingUpOnList)

        assertTrue(runBlocking { manager.drop() })

        assertEquals(RemoteConnectionState.Lost, manager.connectionState.value)
        assertNull(manager.current())
        assertEquals(1, fixture.notices.pending.value.size)
    }

    @Test
    fun `이미 종료한 세션은 끊김으로 끝내지 않는다`() {
        fixture.connect(hangingUpOnList)
        runBlocking { manager.disconnect() }

        assertFalse(runBlocking { manager.drop() })

        assertEquals(RemoteConnectionState.Disconnected, manager.connectionState.value)
        assertEquals(0, fixture.notices.pending.value.size)
    }

    @Test
    fun `이미 끊긴 세션의 끊김을 다시 알리거나 종료로 덮어쓰지 않는다`() {
        fixture.connect(hangingUpOnList)
        list()

        assertFalse(runBlocking { manager.drop() })

        assertEquals(RemoteConnectionState.Lost, manager.connectionState.value)
        assertEquals(1, fixture.notices.pending.value.size)
    }

    @Test
    fun `끊긴 뒤 새 연결을 시도하면 끊김 기록과 아직 알리지 않은 끊김 알림을 지운다`() {
        fixture.connect(hangingUpOnList)
        list()

        val result = fixture.connect(other, name = "other")

        assertEquals(ConnectResult.Connected, result)
        assertEquals(RemoteConnectionState.Connected("other"), manager.connectionState.value)
        assertEquals(0, fixture.notices.pending.value.size)
    }

    @Test
    fun `새 연결을 시작하면 보여 주는 중이던 끊김도 돌려놓지 않는다`() {
        fixture.connect(hangingUpOnList)
        list()
        val shown = fixture.notices.pending.value.single().id
        fixture.notices.claim(shown)

        fixture.connect(other, name = "other")
        fixture.notices.release(shown)

        assertEquals(0, fixture.notices.pending.value.size)
    }

    @Test
    fun `다른 서버로 전환하면 연결 상태가 새로 연결한 서버를 가리킨다`() {
        // 연결 유지 서비스 알림이 연결 상태의 서버 이름을 보여 주므로, 전환 뒤에 이전 서버 이름이 남으면 안 된다
        fixture.connect(hangingUpOnList)
        val before = manager.current()

        fixture.connect(other, name = "other")

        assertEquals(RemoteConnectionState.Connected("other"), manager.connectionState.value)
        assertTrue("전환하면 새 세션 표지를 발급한다", before!!.id != manager.current()!!.id)
    }

    @Test
    fun `다른 서버로 전환하는 동안 이전 세션의 명령이 실패해도 끊김이 아니고 전환은 그 명령을 기다리지 않는다`() {
        val release = CountDownLatch(1)
        val listingStarted = CountDownLatch(1)
        // 목록 데이터를 보내지 않고 붙잡아, 이전 탐색 세션의 명령이 진행 중인 채로 전환하게 한다
        val holding = fixture.server(mapOf("LIST" to { exchange ->
            exchange.data.accept().use {
                exchange.send("150 Here comes the directory listing")
                listingStarted.countDown()
                release.await(ScriptedFtpServer.TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            }
            exchange.send("226 Transfer complete")
        }))
        fixture.connect(holding)
        val listing = AtomicReference<IoResult<*>?>()
        val lister = thread { listing.set(list()) }
        listingStarted.await(ScriptedFtpServer.TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)

        val switched = AtomicReference<ConnectResult?>()
        val switching = thread { switched.set(fixture.connect(other, name = "other")) }
        switching.join(1_500)
        release.countDown()
        lister.join(ScriptedFtpServer.TIMEOUT_MILLIS.toLong())

        assertEquals(ConnectResult.Connected, switched.get())
        assertTrue(listing.get().toString(), listing.get() is IoResult.ConnectionLost)
        assertEquals(RemoteConnectionState.Connected("other"), manager.connectionState.value)
        assertEquals(0, fixture.notices.pending.value.size)
    }

    @Test
    fun `서버 거부처럼 결과로 돌아온 실패는 연결 상태를 바꾸지 않는다`() {
        fixture.connect(other, name = "other")

        val listing = list()

        assertTrue(listing.toString(), listing is IoResult.ItemFailed)
        assertEquals(RemoteConnectionState.Connected("other"), manager.connectionState.value)
    }

    /**
     * 탐색 세션으로 목록을 조회한다.
     * @return 조회 결과
     */
    private fun list(): IoResult<*> = runBlocking { manager.withBrowsingSession { it.listing.listFiles("ftp://host/r") } }
}
