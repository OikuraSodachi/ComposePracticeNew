package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.data.ftp.FakeFtpClient
import com.todokanai.composepracticenew.data.ftp.FtpSessionManager
import com.todokanai.composepracticenew.datasource.LocalFileDataSource
import com.todokanai.composepracticenew.di.RepositoryModule
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.model.RemoteProtocolSettings
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.model.SortMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [FtpRootNavigator]가 만든 루트 URI를 DI가 만드는 원격 탐색 저장소가 거부하지 않는지, 거부되면 배선 오류로 실패하는지 검증하는 단위 테스트(#229, #445).
 * 루트 URI 형식이나 FTP 경로 판정이 바뀌어 둘이 어긋나면 여기서 드러난다.
 */
class RemoteRootNavigationWiringTest {

    /** 테스트 세션 관리자. 루트 이동은 서버에 접속하지 않는다. */
    private val sessions = FtpSessionManager(ConnectionLossNotices(), TestScope(), { FakeFtpClient() })
    /** DI에 등록된 원격 파일 시스템. 탐색과 경로 규칙이 함께 쓴다. */
    private val ftpFileSystem = FtpFileSystem(sessions)
    /** DI에 등록된 로컬 파일 시스템. 탐색과 경로 규칙이 함께 쓴다. */
    private val localFileSystem = LocalFileSystem(LocalFileDataSource(), FakeStorageVolumes())
    /** DI 공급 함수로 만든 원격 탐색 저장소. */
    private val nav = RepositoryModule.provideRemoteFileNavigatorRepository(
        FileListFlowProducer(DataConverter(), object : SortPreferenceRepository {
            override val sortBy = MutableStateFlow(SortMode.BY_DEFAULT)
            override fun saveSortBy(value: SortMode) { sortBy.value = value }
        }),
        appScope = TestScope(),
        fileSystems = setOf(ftpFileSystem, localFileSystem),
        pathRules = FileSystemPathRules(setOf(ftpFileSystem, localFileSystem)),
        notices = OperationNotices()
    )
    /** DI 공급 함수로 만든 루트 이동. */
    private val rootNavigator = RepositoryModule.provideRemoteRootNavigator(nav)

    @Test
    fun `스킴 없는 주소의 루트로 이동한다`() = assertNavigatesToRoot(serverAt("192.168.0.1"), "ftp://192.168.0.1")

    @Test
    fun `스킴을 붙여 저장한 주소의 루트로 이동한다`() = assertNavigatesToRoot(serverAt("ftp://nas.local"), "ftp://nas.local")

    @Test
    fun `탐색 저장소가 루트 이동을 거부하면 배선 오류로 실패한다`() {
        val rejecting = FtpRootNavigator(RejectingNavigator)

        assertThrows(IllegalStateException::class.java) { runBlocking { rejecting.navigateToRoot(serverAt("192.168.0.1")) } }
    }

    /**
     * [item]의 루트로 이동하면 원격 탐색 저장소의 현재 경로가 [expectedRoot]가 되는지 단언한다.
     * @param item 원격 스토리지 접속 정보
     * @param expectedRoot 기대하는 루트 URI
     */
    private fun assertNavigatesToRoot(item: RemoteStorageItem, expectedRoot: String) {
        runBlocking { rootNavigator.navigateToRoot(item) }

        assertEquals(expectedRoot, nav.currentPath.value)
    }

    /**
     * [address]만 다른 접속 정보를 만든다.
     * @param address 서버 주소
     * @return 접속 정보
     */
    private fun serverAt(address: String) = RemoteStorageItem(name = "nas", address = address, port = 21, userId = "", password = "", settings = RemoteProtocolSettings.Ftp("UTF-8"))

    /** 모든 이동을 거부하는 탐색 저장소. 목록 기능은 쓰지 않는다. */
    private object RejectingNavigator : FileNavigatorRepository {
        override val currentPath: StateFlow<String?> = MutableStateFlow(null)
        override val listingRequest: StateFlow<ListingRequest?> = MutableStateFlow(null)
        override val directoryListing: StateFlow<DirectoryListing> = MutableStateFlow(DirectoryListing.NoPath)
        override suspend fun navigate(path: String) = NavigateResult.Rejected
        override fun refresh() = Unit
    }
}
