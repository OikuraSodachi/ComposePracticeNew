package com.todokanai.composepracticenew.data.ftp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FTP 구현이 디렉터리·이름으로 받은 목적지를 ftp:// 경로로 만드는 규칙([childFtpUri], [siblingFtpUri])이
 * 목록 항목 경로와 같은 규칙을 따르는지(#228), 서버 주소와 ftp:// 경로를 바꾸는 규칙([ftpUriOf], [ftpServerOf])을 검증하는 단위 테스트(#234).
 */
class FtpPathCompositionTest {

    @Test
    fun `디렉터리 아래 항목 경로를 만든다`() {
        assertEquals("ftp://host/dir/a.txt", childFtpUri("ftp://host/dir", "a.txt"))
    }

    @Test
    fun `서버 루트 아래 항목 경로를 만든다`() {
        assertEquals("ftp://host/a.txt", childFtpUri("ftp://host", "a.txt"))
    }

    @Test
    fun `디렉터리 경로가 구분자로 끝나도 목록 항목과 같은 경로를 만든다`() {
        assertEquals("ftp://host/a.txt", childFtpUri("ftp://host/", "a.txt"))
    }

    @Test
    fun `같은 디렉터리 안의 다른 이름 경로를 만든다`() {
        assertEquals("ftp://host/dir/b.txt", siblingFtpUri("ftp://host/dir/a.txt", "b.txt"))
        assertEquals("ftp://host/b.txt", siblingFtpUri("ftp://host/a.txt", "b.txt"))
    }

    @Test
    fun `서버 주소는 접두사가 있든 없든 같은 ftp 경로가 된다`() {
        assertEquals("ftp://host", ftpUriOf("host"))
        assertEquals("ftp://host", ftpUriOf("ftp://host"))
    }

    @Test
    fun `서버 주소에서 접속할 호스트만 꺼낸다`() {
        assertEquals("host", ftpServerOf("host"))
        assertEquals("host", ftpServerOf("ftp://host"))
    }

    @Test
    fun `ftp 형식 경로만 ftp 경로로 판별한다`() {
        assertTrue(isFtpUri("ftp://host/a.txt"))
        assertFalse(isFtpUri("/storage/a.txt"))
    }
}
