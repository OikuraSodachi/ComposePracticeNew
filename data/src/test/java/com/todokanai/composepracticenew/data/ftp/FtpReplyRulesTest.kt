package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import org.junit.Assert.assertEquals
import org.junit.Test

/** FTP 클라이언트 없이 서버 응답만으로 [FtpReplyRules]의 해석을 검증하는 단위 테스트(#234). */
class FtpReplyRulesTest {

    @Test
    fun `거부 응답은 명령 이름과 다듬은 응답 문자열을 담은 실패다`() {
        assertEquals(IoResult.ItemFailed("DELE 실패: 550 Denied"), FtpReplyRules.refusal(FtpCommand.DELE, "550 Denied\r\n"))
    }

    @Test
    fun `RMD 거부에는 숨김 항목 안내를 덧붙인다`() {
        assertEquals(
            IoResult.ItemFailed("RMD 실패: 550 Not empty" + FtpReplyRules.HIDDEN_ENTRIES_NOTE),
            FtpReplyRules.refusal(FtpCommand.RMD, "550 Not empty")
        )
    }

    @Test
    fun `완료를 확인받지 못한 전송은 다듬은 응답 문자열을 담은 실패다`() {
        assertEquals(IoResult.ItemFailed("전송 미완료: 451 Aborted"), FtpReplyRules.unacknowledgedTransfer("451 Aborted\r\n"))
    }

    @Test
    fun `MLST 성공 응답의 항목 줄에서 크기를 읽는다`() {
        assertEquals(12L, FtpReplyRules.mlstEntrySize(250, arrayOf("250-Listing a.txt", " size=12;type=file; a.txt", "250 End")))
    }

    @Test
    fun `MLST 거부 응답은 크기를 모른다`() {
        assertEquals(-1L, FtpReplyRules.mlstEntrySize(550, arrayOf("550-Denied", " size=12;type=file; a.txt", "550 End")))
    }

    @Test
    fun `MLST 성공 응답에 항목 줄이 없으면 크기를 모른다`() {
        assertEquals(-1L, FtpReplyRules.mlstEntrySize(250, arrayOf("250 End")))
    }

    @Test
    fun `MLST 항목 줄을 해석할 수 없으면 크기를 모른다`() {
        listOf(" size=abc;type=file; a.txt", " type=file;unix.mode=7; a.txt").forEach { entryLine ->
            assertEquals(entryLine, -1L, FtpReplyRules.mlstEntrySize(250, arrayOf("250-Listing a.txt", entryLine, "250 End")))
        }
    }
}
