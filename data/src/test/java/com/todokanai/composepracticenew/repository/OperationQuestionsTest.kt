package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ArchivePasswordPrompt
import com.todokanai.composepracticenew.model.ConflictAnswer
import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.ConflictQuestion
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.OperationQuestion
import com.todokanai.composepracticenew.model.PasswordAnswer
import com.todokanai.composepracticenew.model.PasswordQuestion
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.model.WriteConflictKind
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 작업 질문함([OperationQuestions])이 충돌·비밀번호 질문을 종류와 무관하게 맡긴 순서대로 하나씩 내보내고, 답하거나 기다림이 끝나면 거두며, 종류가 다른 답은 받지 않는지 검증하는 단위 테스트(#316).
 */
class OperationQuestionsTest {

    private val questions = OperationQuestions()
    private val first = WriteConflict("a.txt", "/dest", WriteConflictKind.FILE)
    private val second = WriteConflict("dir", "/dest", WriteConflictKind.FOLDER)
    private val skip = ConflictAnswer.Choose(ConflictChoice.SKIP, applyToRest = false)
    private fun OperationQuestion?.conflictOf() = (this as? ConflictQuestion)?.conflict

    @Test
    fun `맡긴 순서대로 하나씩 내보내고 답하면 다음 질문으로 넘어간다`() = runTest {
        val firstReply = async { questions.askConflict(FileOperationType.COPY, first) }
        val secondReply = async { questions.askConflict(FileOperationType.UNZIP, second) }
        runCurrent()
        assertEquals(first, questions.current.value.conflictOf())

        questions.answerConflict(questions.current.value!!.id, skip)
        runCurrent()

        assertEquals(skip, firstReply.await())
        assertEquals(second, questions.current.value.conflictOf())
        questions.answerConflict(questions.current.value!!.id, ConflictAnswer.Cancel)
        assertEquals(ConflictAnswer.Cancel, secondReply.await())
        assertNull(questions.current.value)
    }

    @Test
    fun `충돌 질문과 비밀번호 질문은 종류와 무관하게 맡긴 순서대로 하나씩 나온다`() = runTest {
        val conflictReply = async { questions.askConflict(FileOperationType.UNZIP, first) }
        val passwordReply = async { questions.askPassword(FileOperationType.UNZIP, "a.zip", ArchivePasswordPrompt.FIRST) }
        runCurrent()
        assertEquals(first, questions.current.value.conflictOf())

        questions.answerConflict(questions.current.value!!.id, skip)
        runCurrent()
        val password = questions.current.value as PasswordQuestion
        assertEquals("a.zip", password.archiveName)

        questions.answerPassword(password.id, PasswordAnswer.Enter("secret"))
        assertEquals(skip, conflictReply.await())
        assertEquals(PasswordAnswer.Enter("secret"), passwordReply.await())
        assertNull(questions.current.value)
    }

    @Test
    fun `종류가 다른 답은 질문에 닿지 않는다`() = runTest {
        val reply = async { questions.askPassword(FileOperationType.UNZIP, "a.zip", ArchivePasswordPrompt.FIRST) }
        runCurrent()
        val id = questions.current.value!!.id

        questions.answerConflict(id, skip)
        runCurrent()
        assertEquals(id, questions.current.value?.id)

        questions.answerPassword(id, PasswordAnswer.Cancel)
        assertEquals(PasswordAnswer.Cancel, reply.await())
    }

    @Test
    fun `답한 질문에 다시 답해도 아무것도 바뀌지 않는다`() = runTest {
        val reply = async { questions.askConflict(FileOperationType.COPY, first) }
        runCurrent()
        val id = questions.current.value!!.id

        questions.answerConflict(id, skip)
        questions.answerConflict(id, ConflictAnswer.Cancel)

        assertEquals(skip, reply.await())
    }

    @Test
    fun `기다리던 작업이 취소되면 질문을 거둔다`() = runTest {
        val reply = async { questions.askConflict(FileOperationType.COPY, first) }
        runCurrent()

        reply.cancel()
        runCurrent()

        assertNull(questions.current.value)
    }
}
