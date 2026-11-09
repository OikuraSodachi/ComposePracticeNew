package com.todokanai.composepracticenew.data.ftp

import org.junit.Assert.assertEquals
import org.junit.Test

/** 디렉터리 이동과 현재 디렉터리 기록이 [FtpCommandClient.changeDirectory] 하나로 묶여 어긋나지 않는지 검증하는 단위 테스트(#233). */
class ListingStreamFtpClientWorkingDirectoryTest {

    private val client = FakeFtpClient()

    @Test
    fun `로그인을 기록하면 현재 디렉터리는 홈이다`() {
        client.recordLogin("/home/u")

        assertEquals("/home/u", client.homeDirectory)
        assertEquals("/home/u", client.workingDirectory)
    }

    @Test
    fun `이동이 받아들여지면 현재 디렉터리를 기록한다`() {
        client.recordLogin("/home/u")

        assertEquals(true, client.changeDirectory("/r"))

        assertEquals("/r", client.workingDirectory)
        assertEquals("/home/u", client.homeDirectory)
    }

    @Test
    fun `이동이 거부되면 현재 디렉터리는 그대로다`() {
        client.recordLogin("/home/u")
        client.onChangeWorkingDirectory = { false }

        assertEquals(false, client.changeDirectory("/r"))

        assertEquals("/home/u", client.workingDirectory)
    }
}
