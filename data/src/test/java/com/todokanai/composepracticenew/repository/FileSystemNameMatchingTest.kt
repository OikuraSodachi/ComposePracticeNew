package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.ftp.DataCommandRunner
import com.todokanai.composepracticenew.data.ftp.FakeFtpClient
import com.todokanai.composepracticenew.data.ftp.FtpCommandExecutor
import com.todokanai.composepracticenew.data.ftp.FtpSession
import com.todokanai.composepracticenew.datasource.LocalFileDataSource
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.IoResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [FileSystemNameMatching]이 이름이 놓인 위치를 처리하는 파일 시스템의 비교 규칙을 쓰는지,
 * 로컬·FTP 파일 시스템이 대소문자와 Unicode 정규형만 다른 이름을 같은 항목으로 묶는지 검증하는 단위 테스트.
 */
class FileSystemNameMatchingTest {

    /** 로컬 경로를 처리하는 파일 시스템. */
    private val local = LocalPathAwareFileSystem(LocalFileDataSource(), FakeStorageVolumes())
    /** ftp:// 경로를 처리하는 파일 시스템. */
    private val ftp = FtpPathAwareFileSystem(FtpSession(FakeFtpClient(), ConnectionLossNotices()).let { session -> FtpCommandExecutor(session, DataCommandRunner(session)) }, OperationNotices())

    /** 로컬·FTP 파일 시스템과 각자 처리하는 위치. */
    private val fileSystems = listOf(local to "/dir", ftp to "ftp://host/dir")

    @Test
    fun `대소문자만 다른 이름은 같은 항목으로 묶는다`() {
        fileSystems.forEach { (fileSystem, location) ->
            assertSameEntry(fileSystem, location, "readme.txt", "README.TXT")
            // 한 문자가 여러 문자로 바뀌는 대소문자 매핑("ß" → "SS")도 같은 항목으로 본다
            assertSameEntry(fileSystem, location, "straße", "STRASSE")
        }
    }

    @Test
    fun `조합형과 분해형처럼 Unicode 정규형만 다른 이름은 같은 항목으로 묶는다`() {
        fileSystems.forEach { (fileSystem, location) ->
            // 조합형 "한"(U+D55C)과 macOS 등이 쓰는 분해형 "ㅎ+ㅏ+ㄴ"(U+1112 U+1161 U+11AB)
            assertSameEntry(fileSystem, location, "한.txt", "한.txt")
            // 조합형 "é"(U+00E9)와 "e" + 결합 악센트(U+0301), 대소문자도 함께 다른 경우
            assertSameEntry(fileSystem, location, "café", "CAFÉ")
        }
    }

    @Test
    fun `글자가 다른 이름은 다른 항목으로 본다`() {
        fileSystems.forEach { (fileSystem, location) ->
            assertNotEquals(fileSystem.nameMatchKeyOf("a.txt", location), fileSystem.nameMatchKeyOf("b.txt", location))
            assertNotEquals(fileSystem.nameMatchKeyOf("cafe", location), fileSystem.nameMatchKeyOf("café", location))
        }
    }

    @Test
    fun `위치를 처리하는 파일 시스템의 비교 규칙으로 비교 값을 구한다`() {
        val matching = FileSystemNameMatching(setOf(PrefixedKeyFileSystem("ftp://"), local))

        assertEquals("ftp://:A.txt", matching.matchKeyOf("A.txt", "ftp://host/dir"))
        assertEquals(local.nameMatchKeyOf("A.txt", "/dir"), matching.matchKeyOf("A.txt", "/dir"))
    }

    /** [fileSystem]이 [location]에서 [a]와 [b]를 같은 항목으로 묶는지 확인한다. */
    private fun assertSameEntry(fileSystem: PathAwareFileSystem, location: String, a: String, b: String) {
        assertEquals("$a / $b", fileSystem.nameMatchKeyOf(a, location), fileSystem.nameMatchKeyOf(b, location))
    }

    /**
     * [prefix]로 시작하는 경로만 처리하고 이름 앞에 [prefix]를 붙인 값을 비교 값으로 돌려주는 테스트용 파일 시스템. 라우팅을 확인하는 데만 쓴다.
     * @param prefix 처리할 경로의 앞부분
     */
    private class PrefixedKeyFileSystem(private val prefix: String) : PathAwareFileSystem {
        override fun handles(path: String) = path.startsWith(prefix)
        override suspend fun listFiles(path: String): IoResult<ListedDirectory> = IoResult.ItemFailed(null)
        override fun getParent(path: String): String? = null
        override fun displayName(path: String) = path
        override fun nameViolationsOf(fullName: String, location: String) = emptySet<FileNameError>()
        override fun nameMatchKeyOf(name: String, location: String) = "$prefix:$name"
        override suspend fun resolveNavigationPath(path: String): String? = null
    }
}
