package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.ftp.DataCommandRunner
import com.todokanai.composepracticenew.data.ftp.FakeFtpClient
import com.todokanai.composepracticenew.data.ftp.FtpCommandExecutor
import com.todokanai.composepracticenew.data.ftp.FtpSession
import com.todokanai.composepracticenew.datasource.LocalFileDataSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 경로 종류별 부모 경로 규칙([PathAwareFileSystem.getParent])이 한 단계씩 위로 올라가 루트에서 null로 끝나는지 검증하는 단위 테스트(#227).
 * 경로 표시줄은 이 규칙을 따라 루트까지 올라가므로, 이 규칙이 표시줄의 단계와 종료를 정한다.
 */
class ParentPathTest {

    @Test
    fun `FTP 경로는 서버 루트까지 올라가고 그 위는 없다`() {
        val ftp = FtpPathAwareFileSystem(FtpSession(FakeFtpClient(), ConnectionLossNotices()).let { session -> FtpCommandExecutor(session, DataCommandRunner(session)) })

        assertEquals(listOf("ftp://host/a/b", "ftp://host/a", "ftp://host"), ancestorChain(ftp, "ftp://host/a/b"))
    }

    @Test
    fun `저장소 볼륨 밖의 로컬 경로는 최상위 디렉터리까지 올라가고 그 위는 없다`() {
        val local = LocalPathAwareFileSystem(LocalFileDataSource(), FakeStorageVolumes())

        assertEquals(
            listOf("/storage/emulated/0", "/storage/emulated", "/storage"),
            ancestorChain(local, "/storage/emulated/0")
        )
    }

    @Test
    fun `로컬 경로는 저장소 볼륨 루트에서 끝난다`() {
        val local = LocalPathAwareFileSystem(LocalFileDataSource(), FakeStorageVolumes("/storage/emulated/0", "/storage/ABCD-1234"))

        assertEquals(listOf("/storage/emulated/0/a/b", "/storage/emulated/0/a", "/storage/emulated/0"), ancestorChain(local, "/storage/emulated/0/a/b"))
        assertEquals(listOf("/storage/ABCD-1234/x", "/storage/ABCD-1234"), ancestorChain(local, "/storage/ABCD-1234/x"))
    }

    /**
     * [path]에서 [fileSystem]의 부모 규칙을 따라 null이 나올 때까지 올라간 경로들을 반환한다.
     * @param fileSystem 부모 규칙을 가진 파일 시스템
     * @param path 시작 경로
     * @return [path]부터 루트까지의 경로
     */
    private fun ancestorChain(fileSystem: PathAwareFileSystem, path: String): List<String> =
        generateSequence(path) { fileSystem.getParent(it) }.toList()
}
