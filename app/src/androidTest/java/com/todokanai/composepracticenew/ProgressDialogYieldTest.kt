package com.todokanai.composepracticenew

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.todokanai.composepracticenew.compose.presets.dialog.AppAlertDialog
import com.todokanai.composepracticenew.compose.presets.dialog.LocalOpenDialogRegistry
import com.todokanai.composepracticenew.compose.presets.dialog.OpenDialogRegistry
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.ProgressStateModel
import com.todokanai.composepracticenew.navigation.ProgressSection
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 다른 다이얼로그가 떠 있는 동안 진행률 다이얼로그가 그 다이얼로그를 덮지 않고 비켜나는지,
 * 다른 다이얼로그가 닫히면 다시 표시되는지 검증하는 기기 테스트.
 */
@RunWith(AndroidJUnit4::class)
class ProgressDialogYieldTest {

    /** 다이얼로그 창을 띄울 Activity를 제공하는 Compose 테스트 규칙. */
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** 진행 중인 업로드 하나가 표시 대상인 진행률 목록. */
    private val visibleProgress = MutableStateFlow(
        mapOf(1 to ProgressStateModel(progress = 30, operationType = FileOperationType.UPLOAD))
    )

    /** 진행률 다이얼로그 제목. */
    private val progressTitle get() = composeRule.activity.getString(R.string.progress_dialog_title)

    /** 테스트가 띄우는 다른 다이얼로그의 제목. */
    private val otherDialogTitle = "other dialog"

    /** 다른 다이얼로그를 띄울지 테스트가 바꾸는 값. */
    private var showOtherDialog by mutableStateOf(false)

    @Test
    fun 다른_다이얼로그가_없으면_진행률_다이얼로그를_표시한다() {
        setContent()

        composeRule.onNodeWithText(progressTitle).assertExists()
    }

    @Test
    fun 다른_다이얼로그가_뜨면_진행률_다이얼로그를_숨기고_닫히면_다시_표시한다() {
        setContent()
        composeRule.onNodeWithText(progressTitle).assertExists()

        showOtherDialog = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText(otherDialogTitle).assertExists()
        composeRule.onNodeWithText(progressTitle).assertDoesNotExist()

        showOtherDialog = false
        composeRule.waitForIdle()

        composeRule.onNodeWithText(progressTitle).assertExists()
    }

    @Test
    fun 두_다이얼로그가_처음부터_함께_만들어져도_진행률_다이얼로그를_숨긴다() {
        showOtherDialog = true
        setContent()

        composeRule.onNodeWithText(otherDialogTitle).assertExists()
        composeRule.onNodeWithText(progressTitle).assertDoesNotExist()
    }

    /** AppNavHost처럼 레지스트리를 제공하고, 다른 다이얼로그를 진행률 섹션보다 먼저 배치한다. */
    private fun setContent() {
        composeRule.setContent {
            // 키 없음 — 테스트 화면이 살아 있는 동안 하나만 둔다
            val registry = remember { OpenDialogRegistry() }
            CompositionLocalProvider(LocalOpenDialogRegistry provides registry) {
                if (showOtherDialog) {
                    AppAlertDialog(
                        onDismissRequest = {},
                        confirmButton = {},
                        title = { Text(otherDialogTitle) }
                    )
                }
            }
            ProgressSection(
                visibleProgress = visibleProgress,
                openDialogRegistry = registry,
                onDismiss = {},
                onCancel = {}
            )
        }
    }
}
