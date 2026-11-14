package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.data.ftp.DataCommandRunner
import com.todokanai.composepracticenew.data.ftp.FakeFtpClient
import com.todokanai.composepracticenew.data.ftp.FtpCommandExecutor
import com.todokanai.composepracticenew.data.ftp.FtpConnector
import com.todokanai.composepracticenew.data.ftp.FtpKeepAlive
import com.todokanai.composepracticenew.data.ftp.FtpSession
import com.todokanai.composepracticenew.data.ftp.FtpTransferrer
import com.todokanai.composepracticenew.datasource.LocalFileDataSource
import com.todokanai.composepracticenew.di.RepositoryModule
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.model.RemoteProtocolSettings
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.model.SortMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 실제 FTP 연결 저장소가 돌려주는 루트 경로([FtpRepositoryImpl.rootPathOf])를 DI가 만드는 원격 탐색 저장소가 거부하지 않는지 검증하는 단위 테스트(#229).
 * RemoteConnectionUseCase는 이 이동이 거부되면 배선 오류로 실패하므로, 루트 경로 형식이나 FTP 경로 판정이 바뀌어 둘이 어긋나면 여기서 드러난다.
 */
class RemoteRootNavigationWiringTest {

    /** 테스트 세션. 루트 경로 계산과 원격 이동은 서버에 명령을 보내지 않는다. */
    private val session = FtpSession(FakeFtpClient(), ConnectionLossNotices())
    /** 원격 파일 시스템과 FTP 저장소가 함께 쓰는 명령 실행기. */
    private val commands = FtpCommandExecutor(session, DataCommandRunner(session))
    /** 루트 경로를 정하는 실제 FTP 연결 저장소. */
    private val connection = FtpRepositoryImpl(session, FtpConnector(session, FtpKeepAlive(session, session, TestScope())), commands, FtpTransferrer(session, commands, DataCommandRunner(session), OperationNotices()))
    /** DI 공급 함수로 만든 원격 탐색 저장소. 파일 시스템 집합은 DI에 등록된 것과 같다. */
    private val nav = RepositoryModule.provideRemoteFileNavigatorRepository(
        DataConverter(),
        sortPreference = object : SortPreferenceRepository {
            override val sortBy = MutableStateFlow(SortMode.BY_DEFAULT)
            override fun saveSortBy(value: SortMode) { sortBy.value = value }
        },
        appScope = TestScope(),
        fileSystems = setOf(FtpPathAwareFileSystem(commands), LocalPathAwareFileSystem(LocalFileDataSource(), FakeStorageVolumes())),
        notices = OperationNotices()
    )

    @Test
    fun `스킴 없는 주소의 루트 경로로 이동한다`() = assertNavigatesToRoot(serverAt("192.168.0.1"))

    @Test
    fun `스킴을 붙여 저장한 주소의 루트 경로로 이동한다`() = assertNavigatesToRoot(serverAt("ftp://nas.local"))

    /**
     * [item]의 루트 경로로 이동이 받아들여지고 현재 경로가 그 경로가 되는지 단언한다.
     * @param item 원격 스토리지 접속 정보
     */
    private fun assertNavigatesToRoot(item: RemoteStorageItem) {
        val root = connection.rootPathOf(item)

        assertEquals(NavigateResult.Navigated, runBlocking { nav.navigate(root) })
        assertEquals(root, nav.currentPath.value)
    }

    /**
     * [address]만 다른 접속 정보를 만든다.
     * @param address 서버 주소
     * @return 접속 정보
     */
    private fun serverAt(address: String) = RemoteStorageItem(name = "nas", address = address, port = 21, userId = "", password = "", settings = RemoteProtocolSettings.Ftp("UTF-8"))
}
