package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FileNameUseCase.validate]의 공통 이름 규칙(공백뿐·앞뒤 공백 거부, 경로 문자·제어 문자·예약 이름 거부)과,
 * 저장소의 제약을 이름이 놓일 위치의 규칙에 맡기는 동작을 검증하는 단위 테스트.
 */
class FileNameUseCaseTest {

    private val useCase = FileNameUseCase(NoStorageNameRules)

    @Test
    fun `일반 이름·중간 공백·확장자 변경은 허용한다`() {
        listOf("docs", "a.txt", "a.md", "archive.tar.gz", ".hidden", "...", "이름 변경", "my  file.txt").forEach { name ->
            assertTrue("name=\"$name\"", useCase.validate(name, LOCATION).isEmpty())
        }
    }

    @Test
    fun `공백뿐인 이름은 거부한다`() {
        assertEquals(setOf(FileNameError.BLANK), useCase.validate("", LOCATION))
        assertEquals(setOf(FileNameError.BLANK), useCase.validate("   ", LOCATION))
    }

    @Test
    fun `앞이나 뒤에 공백이 있으면 거부한다`() {
        // 탭 등 제어 문자인 공백은 CONTAINS_CONTROL_CHAR도 함께 걸리므로 여기서는 제어 문자가 아닌 공백만 사용
        listOf("  docs ", " docs", "docs ", "docs　").forEach { name ->
            assertEquals("name=\"$name\"", setOf(FileNameError.LEADING_OR_TRAILING_WHITESPACE), useCase.validate(name, LOCATION))
        }
    }

    @Test
    fun `경로 구분자를 포함하면 거부한다`() {
        assertEquals(setOf(FileNameError.CONTAINS_PATH_SEPARATOR), useCase.validate("a/b", LOCATION))
        assertEquals(setOf(FileNameError.CONTAINS_PATH_SEPARATOR), useCase.validate("a\\b", LOCATION))
    }

    @Test
    fun `제어 문자를 포함하면 거부한다`() {
        assertEquals(setOf(FileNameError.CONTAINS_CONTROL_CHAR), useCase.validate("a\u0000b", LOCATION))
        assertEquals(setOf(FileNameError.CONTAINS_CONTROL_CHAR), useCase.validate("a\nb", LOCATION))
        assertEquals(setOf(FileNameError.CONTAINS_CONTROL_CHAR), useCase.validate("a\u007Fb", LOCATION))
    }

    @Test
    fun `C1 제어 문자를 포함하면 거부한다`() {
        listOf('\u0080', '\u0085', '\u009F').forEach { c ->
            assertEquals("U+%04X".format(c.code), setOf(FileNameError.CONTAINS_CONTROL_CHAR), useCase.validate("a${c}b", LOCATION))
        }
    }

    @Test
    fun `글자 방향 제어 문자를 포함하면 거부한다`() {
        val bidiControls = listOf('\u061C', '\u200E', '\u200F', '\u202A', '\u202B', '\u202C', '\u202D', '\u202E', '\u2066', '\u2067', '\u2068', '\u2069')
        bidiControls.forEach { c ->
            assertEquals("U+%04X".format(c.code), setOf(FileNameError.CONTAINS_CONTROL_CHAR), useCase.validate("a${c}b", LOCATION))
        }
        // 화면에는 "reportkpa.pdf"로 보이지만 실제 확장자가 .apk인 위장 이름
        assertEquals(setOf(FileNameError.CONTAINS_CONTROL_CHAR), useCase.validate("report\u202Efdp.apk", LOCATION))
    }

    @Test
    fun `이름 중간의 줄 구분자와 문단 구분자는 거부한다`() {
        assertEquals(setOf(FileNameError.CONTAINS_CONTROL_CHAR), useCase.validate("a\u2028b", LOCATION))
        assertEquals(setOf(FileNameError.CONTAINS_CONTROL_CHAR), useCase.validate("a\u2029b", LOCATION))
    }

    @Test
    fun `이모지 결합 문자 같은 폭 없는 문자는 허용한다`() {
        // ZWJ(U+200D)로 이은 가족 이모지, ZWNJ(U+200C)를 쓰는 페르시아어 이름
        listOf("👨\u200D👩\u200D👧.jpg", "می\u200Cخواهم.txt").forEach { name ->
            assertTrue("name=\"$name\"", useCase.validate(name, LOCATION).isEmpty())
        }
    }

    @Test
    fun `이름 전체가 점 하나 또는 둘이면 거부한다`() {
        assertEquals(setOf(FileNameError.RESERVED_NAME), useCase.validate(".", LOCATION))
        assertEquals(setOf(FileNameError.RESERVED_NAME), useCase.validate("..", LOCATION))
    }

    @Test
    fun `공통 규칙에는 길이 한도가 없다`() {
        assertTrue(useCase.validate("a".repeat(1000), LOCATION).isEmpty())
    }

    @Test
    fun `저장소의 제약은 suffix를 붙인 이름과 위치로 묻고 그 위반을 돌려준다`() {
        val storage = RecordingStorageNameRules(setOf(FileNameError.TOO_LONG))

        assertEquals(setOf(FileNameError.TOO_LONG), FileNameUseCase(storage).validate("archive", LOCATION, suffix = ".zip"))
        assertEquals(listOf("archive.zip" to LOCATION), storage.calls)
    }

    @Test
    fun `위반한 규칙을 공통 규칙, 저장소의 제약 순서로 모두 반환한다`() {
        assertEquals(
            listOf(FileNameError.CONTAINS_PATH_SEPARATOR, FileNameError.CONTAINS_CONTROL_CHAR, FileNameError.TOO_LONG),
            FileNameUseCase(RecordingStorageNameRules(setOf(FileNameError.TOO_LONG))).validate("a/\u0001", LOCATION).toList()
        )
    }

    private companion object {
        /** 이름이 놓일 위치로 넘기는 경로. */
        const val LOCATION = "/dir"
    }
}
