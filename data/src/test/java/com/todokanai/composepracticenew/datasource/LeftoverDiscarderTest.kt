package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.FileSystemRefusal
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.LeftoverCause
import com.todokanai.composepracticenew.model.NoticeMessage
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** [LeftoverDiscarder]가 남은 파일을 지우고, 지우지 못하면 결과나 알림함으로 빠짐없이 알리는지 검증하는 단위 테스트(#431). */
class LeftoverDiscarderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** 정리 실패 알림을 모으는 알림함. */
    private val reported = mutableListOf<NoticeMessage>()

    private val discarder = LeftoverDiscarder(object : OperationNoticeReporter {
        override fun report(message: NoticeMessage) { reported += message }
    })

    /** 지울 수 없는 남은 항목. 안에 파일이 있는 폴더라 지우면 DirectoryNotEmptyException이 난다. */
    private fun undeletable(): File = tmp.newFolder("left").also { File(it, "inner").writeText("x") }

    /** [undeletable]을 지우려 할 때 나는 실패의 원인. */
    private fun notEmptyReason(file: File) = FailureReason.FileSystemRefused(file.path, FileSystemRefusal.NotEmpty)

    @Test
    fun `결과로 돌려줄 수 없는 경로는 남은 파일을 지우고 알리지 않는다`() {
        val file = tmp.newFile("a.part")

        discarder.discardReporting(file)

        assertFalse(file.exists())
        assertEquals(emptyList<NoticeMessage>(), reported)
    }

    @Test
    fun `이미 없는 파일은 지운 것으로 보고 알리지 않는다`() {
        discarder.discardReporting(File(tmp.root, "none"))

        assertEquals(emptyList<NoticeMessage>(), reported)
    }

    @Test
    fun `결과로 돌려줄 수 없는 경로에서 지우지 못하면 경로와 원인을 알림함에 넣는다`() {
        val left = undeletable()

        discarder.discardReporting(left)

        assertEquals(listOf(NoticeMessage.Leftover(left.path, LeftoverCause.LocalRemovalFailed(notEmptyReason(left)))), reported)
    }

    @Test
    fun `실패 결과를 돌려주는 경로에서 지웠으면 실패를 그대로 돌려준다`() {
        val file = tmp.newFile("a.part")
        val failure = IoResult.ItemFailed(FailureReason.IoFailed("디스크 오류"))

        assertEquals(failure, discarder.discardInto(file, failure))
        assertFalse(file.exists())
    }

    @Test
    fun `지우지 못하면 실패의 종류를 지키며 원인에 남은 파일을 담고 알림함에는 넣지 않는다`() {
        val left = undeletable()
        val cause = FailureReason.IoFailed("디스크 오류")
        val leftover = FailureReason.LeftoverNotRemoved(cause, notEmptyReason(left))

        assertEquals(IoResult.ItemFailed(leftover), discarder.discardInto(left, IoResult.ItemFailed(cause)))
        assertEquals(IoResult.ConnectionLost(leftover), discarder.discardInto(left, IoResult.ConnectionLost(cause)))
        assertEquals(
            IoResult.PartiallyFailed(listOf(FailedItem("a", cause), FailedItem("", FailureReason.LeftoverNotRemoved(null, notEmptyReason(left))))),
            discarder.discardInto(left, IoResult.PartiallyFailed(listOf(FailedItem("a", cause))))
        )
        assertEquals(emptyList<NoticeMessage>(), reported)
    }

    @Test
    fun `끝까지 쓴 파일은 지우지 않는다`() {
        val file = tmp.newFile("a.zip")

        assertEquals(IoResult.Success(1), discarder.deletingUnlessCompleted(file) { 1 })
        assertTrue(file.exists())
    }

    @Test
    fun `입출력 실패로 멈춘 쓰기는 파일을 지우고 실패를 결과로 돌려준다`() {
        val file = tmp.newFile("a.zip")

        val result = discarder.deletingUnlessCompleted(file) { throw IOException("디스크 오류") }

        assertEquals(IoResult.ItemFailed(FailureReason.IoFailed("디스크 오류")), result)
        assertFalse(file.exists())
    }

    @Test
    fun `입출력 실패로 멈춘 쓰기의 파일을 지우지 못하면 그 사실을 결과에 담는다`() {
        val left = undeletable()

        val result = discarder.deletingUnlessCompleted(left) { throw IOException("디스크 오류") }

        assertEquals(IoResult.ItemFailed(FailureReason.LeftoverNotRemoved(FailureReason.IoFailed("디스크 오류"), notEmptyReason(left))), result)
        assertEquals(emptyList<NoticeMessage>(), reported)
    }

    @Test
    fun `취소로 멈춘 쓰기의 파일을 지우지 못하면 알림함에 넣고 취소를 전파한다`() {
        val left = undeletable()

        assertThrows(CancellationException::class.java) { discarder.deletingUnlessCompleted<Unit>(left) { throw CancellationException("취소") } }
        assertThrows(CancellationException::class.java) { discarder.deletingOnCancel<Unit>(left) { throw CancellationException("취소") } }

        val notice = NoticeMessage.Leftover(left.path, LeftoverCause.LocalRemovalFailed(notEmptyReason(left)))
        assertEquals(listOf(notice, notice), reported)
    }

    @Test
    fun `취소가 아닌 예외로 멈춘 쓰기는 지우지 않는다`() {
        val file = tmp.newFile("a.part")

        assertThrows(IOException::class.java) { discarder.deletingOnCancel<Unit>(file) { throw IOException("디스크 오류") } }
        assertTrue(file.exists())
    }
}
