package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [FileNameUseCase.validate]의 파일 이름 규칙(공백뿐·앞뒤 공백 거부, 경로 문자·제어 문자·예약 이름·255바이트 초과 거부)을 검증하는 단위 테스트. */
class FileNameUseCaseTest {

    private val useCase = FileNameUseCase()

    @Test
    fun `일반 이름·중간 공백·확장자 변경은 허용한다`() {
        listOf("docs", "a.txt", "a.md", "archive.tar.gz", ".hidden", "...", "이름 변경", "my  file.txt").forEach { name ->
            assertTrue("name=\"$name\"", useCase.validate(name).isEmpty())
        }
    }

    @Test
    fun `공백뿐인 이름은 거부한다`() {
        assertEquals(setOf(FileNameError.BLANK), useCase.validate(""))
        assertEquals(setOf(FileNameError.BLANK), useCase.validate("   "))
    }

    @Test
    fun `앞이나 뒤에 공백이 있으면 거부한다`() {
        // 탭 등 제어 문자인 공백은 CONTAINS_CONTROL_CHAR도 함께 걸리므로 여기서는 제어 문자가 아닌 공백만 사용
        listOf("  docs ", " docs", "docs ", "docs　").forEach { name ->
            assertEquals("name=\"$name\"", setOf(FileNameError.LEADING_OR_TRAILING_WHITESPACE), useCase.validate(name))
        }
    }

    @Test
    fun `경로 구분자를 포함하면 거부한다`() {
        assertEquals(setOf(FileNameError.CONTAINS_PATH_SEPARATOR), useCase.validate("a/b"))
        assertEquals(setOf(FileNameError.CONTAINS_PATH_SEPARATOR), useCase.validate("a\\b"))
    }

    @Test
    fun `제어 문자를 포함하면 거부한다`() {
        assertEquals(setOf(FileNameError.CONTAINS_CONTROL_CHAR), useCase.validate("a\u0000b"))
        assertEquals(setOf(FileNameError.CONTAINS_CONTROL_CHAR), useCase.validate("a\nb"))
        assertEquals(setOf(FileNameError.CONTAINS_CONTROL_CHAR), useCase.validate("a\u007Fb"))
    }

    @Test
    fun `이름 전체가 점 하나 또는 둘이면 거부한다`() {
        assertEquals(setOf(FileNameError.RESERVED_NAME), useCase.validate("."))
        assertEquals(setOf(FileNameError.RESERVED_NAME), useCase.validate(".."))
    }

    @Test
    fun `UTF-8 255바이트까지 허용하고 초과하면 거부한다`() {
        assertTrue(useCase.validate("a".repeat(255)).isEmpty())
        assertEquals(setOf(FileNameError.TOO_LONG), useCase.validate("a".repeat(256)))
        // 한글은 UTF-8 3바이트 — 85자(255바이트)는 허용, 86자(258바이트)는 거부
        assertTrue(useCase.validate("가".repeat(85)).isEmpty())
        assertEquals(setOf(FileNameError.TOO_LONG), useCase.validate("가".repeat(86)))
    }

    @Test
    fun `suffix를 포함한 길이로 한도를 판정한다`() {
        assertTrue(useCase.validate("a".repeat(251), suffix = ".zip").isEmpty())
        assertEquals(setOf(FileNameError.TOO_LONG), useCase.validate("a".repeat(252), suffix = ".zip"))
    }

    @Test
    fun `위반한 규칙을 모두 검사 순서대로 반환한다`() {
        assertEquals(
            listOf(FileNameError.CONTAINS_PATH_SEPARATOR, FileNameError.CONTAINS_CONTROL_CHAR),
            useCase.validate("a/\u0001").toList()
        )
    }
}
