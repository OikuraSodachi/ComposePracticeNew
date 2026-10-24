package com.todokanai.composepracticenew

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 진행률 다이얼로그를 뺀 모든 다이얼로그가 AppAlertDialog를 거쳐 OpenDialogRegistry에 등록되는지 검증하는 단위 테스트.
 * 다이얼로그 API를 직접 쓰면 등록되지 않아 진행률 다이얼로그가 그 다이얼로그를 덮으므로, 직접 쓸 수 있는 파일을 둘로 제한한다.
 */
class DialogRegistrationContractTest {

    /** app 모듈의 main 소스 루트. Gradle 단위 테스트는 모듈 디렉터리에서 실행된다. */
    private val mainSourceRoot = File("src/main/java")

    /** 별도 창을 띄우는 Compose 다이얼로그 API의 import. */
    private val dialogImports = listOf(
        "import androidx.compose.material3.AlertDialog",
        "import androidx.compose.material3.BasicAlertDialog",
        "import androidx.compose.ui.window.Dialog"
    )

    /** 다이얼로그 API를 직접 써도 되는 파일: 등록을 담당하는 래퍼와, 비켜나는 쪽인 진행률 다이얼로그. */
    private val allowedFiles = setOf("AppAlertDialog.kt", "CustomProgressDialog.kt")

    @Test
    fun `래퍼와 진행률 다이얼로그 밖에서는 다이얼로그 API를 직접 쓰지 않는다`() {
        assertTrue("소스 루트를 찾지 못함: ${mainSourceRoot.absolutePath}", mainSourceRoot.isDirectory)

        val violations = mainSourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in allowedFiles }
            .filter { file -> file.useLines { lines -> lines.any { line -> dialogImports.any { line.trim().startsWith(it) } } } }
            .map { it.relativeTo(mainSourceRoot).path }
            .toList()

        assertEquals("AppAlertDialog 대신 다이얼로그 API를 직접 쓰는 파일", emptyList<String>(), violations)
    }
}
