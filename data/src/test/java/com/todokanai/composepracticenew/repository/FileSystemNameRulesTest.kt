package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.ftp.DataCommandRunner
import com.todokanai.composepracticenew.data.ftp.FakeFtpClient
import com.todokanai.composepracticenew.data.ftp.FtpCommandExecutor
import com.todokanai.composepracticenew.data.ftp.FtpSession
import com.todokanai.composepracticenew.datasource.LocalFileDataSource
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.IoResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FileSystemNameRules]가 이름이 놓일 위치를 처리하는 파일 시스템의 제약으로 판정하는지,
 * 로컬·FTP 파일 시스템의 이름 길이 제약(UTF-8 255바이트)을 검증하는 단위 테스트.
 */
class FileSystemNameRulesTest {

    /** 로컬 경로를 처리하는 파일 시스템. */
    private val local = LocalPathAwareFileSystem(LocalFileDataSource())
    /** ftp:// 경로를 처리하는 파일 시스템. */
    private val ftp = FtpPathAwareFileSystem(FtpSession(FakeFtpClient()).let { session -> FtpCommandExecutor(session, DataCommandRunner(session)) }, OperationNotices())

    @Test
    fun `로컬·FTP 모두 UTF-8 255바이트까지 허용하고 초과하면 거부한다`() {
        listOf(local to "/dir", ftp to "ftp://host/dir").forEach { (fileSystem, location) ->
            assertTrue(fileSystem.nameViolationsOf("a".repeat(255), location).isEmpty())
            assertEquals(setOf(FileNameError.TOO_LONG), fileSystem.nameViolationsOf("a".repeat(256), location))
            // 한글은 UTF-8 3바이트 — 85자(255바이트)는 허용, 86자(258바이트)는 거부
            assertTrue(fileSystem.nameViolationsOf("가".repeat(85), location).isEmpty())
            assertEquals(setOf(FileNameError.TOO_LONG), fileSystem.nameViolationsOf("가".repeat(86), location))
        }
    }

    @Test
    fun `위치를 처리하는 파일 시스템의 제약으로 판정한다`() {
        val rules = FileSystemNameRules(setOf(RejectingFileSystem("ftp://"), local))

        assertEquals(setOf(FileNameError.RESERVED_NAME), rules.violationsOf("a", "ftp://host/dir"))
        assertTrue(rules.violationsOf("a", "/dir").isEmpty())
    }

    @Test
    fun `위치를 처리하는 파일 시스템이 없으면 확인할 수 없음으로 거부한다`() {
        val rules = FileSystemNameRules(setOf(local))

        assertEquals(setOf(FileNameError.UNVERIFIABLE_LOCATION), rules.violationsOf("a", "ftp://host/dir"))
    }

    /**
     * [prefix]로 시작하는 경로만 처리하고 모든 이름을 거부하는 테스트용 파일 시스템. 라우팅을 확인하는 데만 쓴다.
     * @param prefix 처리할 경로의 앞부분
     */
    private class RejectingFileSystem(private val prefix: String) : PathAwareFileSystem {
        override fun handles(path: String) = path.startsWith(prefix)
        override suspend fun listFiles(path: String): IoResult<ListedDirectory> = IoResult.ItemFailed(null)
        override fun getParent(path: String): String? = null
        override fun displayName(path: String) = path
        override fun nameViolationsOf(fullName: String, location: String) = setOf(FileNameError.RESERVED_NAME)
        override fun nameMatchKeyOf(name: String, location: String) = name
        override suspend fun resolveNavigationPath(path: String): String? = null
    }
}
