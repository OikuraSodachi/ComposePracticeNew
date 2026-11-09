package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.ftp.DataCommandRunner
import com.todokanai.composepracticenew.data.ftp.FakeFtpClient
import com.todokanai.composepracticenew.data.ftp.FtpCommandExecutor
import com.todokanai.composepracticenew.data.ftp.FtpSession
import com.todokanai.composepracticenew.datasource.LocalFileDataSource
import org.junit.Assert.assertEquals
import org.junit.Test

/** 경로 종류별 표시 이름 규칙([PathAwareFileSystem.displayName])이 경로 표시줄에 보여 줄 이름을 정하는지 검증하는 단위 테스트(#228). */
class DisplayNameTest {

    private val ftp = FtpPathAwareFileSystem(FtpSession(FakeFtpClient()).let { session -> FtpCommandExecutor(session, DataCommandRunner(session)) })
    private val local = LocalPathAwareFileSystem(LocalFileDataSource())

    @Test
    fun `FTP 경로는 마지막 구간을, 서버 루트는 호스트를 이름으로 쓴다`() {
        assertEquals("b", ftp.displayName("ftp://host/a/b"))
        assertEquals("host", ftp.displayName("ftp://host"))
    }

    @Test
    fun `로컬 경로는 마지막 구간을 이름으로 쓴다`() {
        assertEquals("0", local.displayName("/storage/emulated/0"))
    }

    @Test
    fun `이름 구간이 비어 있으면 경로 전체를 이름으로 쓴다`() {
        assertEquals("C:/", local.displayName("C:/"))
    }
}
