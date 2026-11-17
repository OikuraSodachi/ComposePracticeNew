package com.todokanai.composepracticenew.datasource

import kotlinx.coroutines.CancellationException
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.SevenZipException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * RAR 비밀번호 확인 콜백([SevenZipOpenCallback], [SevenZipPasswordCheckCallback], [passwordCheckOf])을 라이브러리 없이 검증하는 단위 테스트.
 * 실제 RAR 파일로 여는 동작은 네이티브 라이브러리가 필요해 기기에서 확인한다.
 */
class SevenZipPasswordCallbacksTest {

    @Test
    fun `항목 결과를 비밀번호 확인 결과로 바꾼다`() {
        assertEquals(PasswordCheck.ACCEPTED, passwordCheckOf(ExtractOperationResult.OK))
        assertEquals(PasswordCheck.WRONG, passwordCheckOf(ExtractOperationResult.WRONG_PASSWORD))
        assertEquals(PasswordCheck.WRONG_OR_DAMAGED, passwordCheckOf(ExtractOperationResult.DATAERROR))
        assertEquals(PasswordCheck.WRONG_OR_DAMAGED, passwordCheckOf(ExtractOperationResult.CRCERROR))
        assertNull(passwordCheckOf(ExtractOperationResult.UNSUPPORTEDMETHOD))
    }

    @Test
    fun `비밀번호가 없는 열기 콜백은 비밀번호를 물으면 열기를 멈추고 물었다는 것을 남긴다`() {
        val callback = SevenZipOpenCallback(password = null)

        assertThrows(SevenZipException::class.java) { callback.cryptoGetTextPassword() }
        assertTrue(callback.passwordRequested)
    }

    @Test
    fun `비밀번호를 묻지 않은 열기 콜백은 물었다고 남기지 않는다`() {
        assertFalse(SevenZipOpenCallback(password = "secret").passwordRequested)
    }

    @Test
    fun `확인 콜백은 후보 비밀번호를 주고 항목 결과를 확인 결과로 남긴다`() {
        val callback = SevenZipPasswordCheckCallback("secret", reportChecking = {})

        assertEquals("secret", callback.cryptoGetTextPassword())
        callback.getStream(0, ExtractAskMode.EXTRACT)!!.write(ByteArray(4))
        callback.setOperationResult(ExtractOperationResult.WRONG_PASSWORD)

        assertEquals(PasswordCheck.WRONG, callback.result)
    }

    @Test
    fun `확인 콜백은 비밀번호와 무관한 실패를 IOException으로 멈춘다`() {
        val callback = SevenZipPasswordCheckCallback("secret", reportChecking = {})

        assertThrows(IOException::class.java) { callback.setOperationResult(ExtractOperationResult.UNSUPPORTEDMETHOD) }
        assertTrue(callback.failure is IOException)
    }

    @Test
    fun `확인 콜백은 내용을 받을 때 취소를 확인해 원래 취소 예외를 남긴다`() {
        val cancelled = CancellationException("취소")
        val callback = SevenZipPasswordCheckCallback("secret", reportChecking = { throw cancelled })
        val stream = callback.getStream(0, ExtractAskMode.EXTRACT)!!

        assertThrows(CancellationException::class.java) { stream.write(ByteArray(4)) }
        assertSame(cancelled, callback.failure)
    }

    @Test
    fun `확인 콜백은 내용 없이 라이브러리가 진행만 알릴 때도 진행을 보고해 원래 취소 예외를 남긴다`() {
        // solid RAR이 확인할 항목 앞의 항목을 출력 없이 푸는 동안에는 기록 없이 setCompleted만 온다(#418)
        val cancelled = CancellationException("취소")
        val callback = SevenZipPasswordCheckCallback("secret", reportChecking = { throw cancelled })

        assertThrows(CancellationException::class.java) { callback.setCompleted(0) }
        assertSame(cancelled, callback.failure)
    }
}
