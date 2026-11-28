package com.todokanai.composepracticenew.navigation

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 저장소 화면에서 파일 탐색 그래프로 들어가는 [enterFileBrowser]가 저장된 목록 화면 상태를 지우는지 검증한다.
 * 그래프는 앱과 같은 모양(저장소 화면 + 두 목록 화면을 담은 중첩 그래프)이며, 화면 내용은 비운다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EnterFileBrowserTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController

    @Before
    fun setUp() {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = NavDestinations.STORAGE) {
                composable(NavDestinations.STORAGE) {}
                navigation(startDestination = NavDestinations.FILE_LIST, route = NavDestinations.FILE_BROWSER_GRAPH) {
                    composable(NavDestinations.FILE_LIST) {}
                    composable(NavDestinations.REMOTE_FILE_LIST) {}
                }
            }
        }
    }

    @Test
    fun entersLocalListWithoutSavedState() {
        navigate { enterFileBrowser(NavDestinations.FILE_LIST) }

        assertEquals(NavDestinations.FILE_LIST, currentRoute())
    }

    @Test
    fun entersRemoteListWithoutSavedState() {
        navigate { enterFileBrowser(NavDestinations.REMOTE_FILE_LIST) }

        assertEquals(NavDestinations.REMOTE_FILE_LIST, currentRoute())
    }

    @Test
    fun listSavedByTabSwitchIsNotRestoredOnNextVisit() {
        navigate { enterFileBrowser(NavDestinations.FILE_LIST) }
        val firstVisitLocalEntryId = currentEntryId()
        navigate { switchListTab(NavDestinations.REMOTE_FILE_LIST) }
        navigate { popBackStack(NavDestinations.STORAGE, false) }

        navigate { enterFileBrowser(NavDestinations.REMOTE_FILE_LIST) }
        navigate { switchListTab(NavDestinations.FILE_LIST) }

        assertEquals(NavDestinations.FILE_LIST, currentRoute())
        assertNotEquals(firstVisitLocalEntryId, currentEntryId())
    }

    @Test
    fun remoteListSavedByTabSwitchIsNotRestoredOnNextVisit() {
        navigate { enterFileBrowser(NavDestinations.REMOTE_FILE_LIST) }
        val firstVisitRemoteEntryId = currentEntryId()
        navigate { switchListTab(NavDestinations.FILE_LIST) }
        navigate { popBackStack(NavDestinations.STORAGE, false) }

        navigate { enterFileBrowser(NavDestinations.FILE_LIST) }
        navigate { switchListTab(NavDestinations.REMOTE_FILE_LIST) }

        assertEquals(NavDestinations.REMOTE_FILE_LIST, currentRoute())
        assertNotEquals(firstVisitRemoteEntryId, currentEntryId())
    }

    /** UI 스레드에서 [action]으로 이동하고 컴포지션이 따라올 때까지 기다린다. */
    private fun navigate(action: NavHostController.() -> Unit) {
        composeRule.runOnIdle { navController.action() }
        composeRule.waitForIdle()
    }

    /** 지금 화면의 route. */
    private fun currentRoute(): String? = composeRule.runOnIdle { navController.currentDestination?.route }

    /** 지금 화면의 back stack entry id — 복원된 entry는 저장 전과 같은 id를 갖는다. */
    private fun currentEntryId(): String? = composeRule.runOnIdle { navController.currentBackStackEntry?.id }
}
