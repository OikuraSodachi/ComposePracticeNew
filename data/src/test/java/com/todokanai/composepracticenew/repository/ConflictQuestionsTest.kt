package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ConflictAnswer
import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.model.WriteConflictKind
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 충돌 질문함([ConflictQuestions])이 질문을 맡긴 순서대로 하나씩 내보내고, 답하거나 기다림이 끝나면 거두는지 검증하는 단위 테스트(#316).
 */
class ConflictQuestionsTest {

    private val questions = ConflictQuestions()
    private val first = WriteConflict("a.txt", "/dest", WriteConflictKind.FILE)
    private val second = WriteConflict("dir", "/dest", WriteConflictKind.FOLDER)
    private val skip = ConflictAnswer.Choose(ConflictChoice.SKIP, applyToRest = false)

    @Test
    fun `맡긴 순서대로 하나씩 내보내고 답하면 다음 질문으로 넘어간다`() = runTest {
        val firstReply = async { questions.ask(FileOperationType.COPY, first) }
        val secondReply = async { questions.ask(FileOperationType.UNZIP, second) }
        runCurrent()
        assertEquals(first, questions.current.value?.conflict)

        questions.answer(questions.current.value!!.id, skip)
        runCurrent()

        assertEquals(skip, firstReply.await())
        assertEquals(second, questions.current.value?.conflict)
        questions.answer(questions.current.value!!.id, ConflictAnswer.Cancel)
        assertEquals(ConflictAnswer.Cancel, secondReply.await())
        assertNull(questions.current.value)
    }

    @Test
    fun `답한 질문에 다시 답해도 아무것도 바뀌지 않는다`() = runTest {
        val reply = async { questions.ask(FileOperationType.COPY, first) }
        runCurrent()
        val id = questions.current.value!!.id

        questions.answer(id, skip)
        questions.answer(id, ConflictAnswer.Cancel)

        assertEquals(skip, reply.await())
    }

    @Test
    fun `기다리던 작업이 취소되면 질문을 거둔다`() = runTest {
        val reply = async { questions.ask(FileOperationType.COPY, first) }
        runCurrent()

        reply.cancel()
        runCurrent()

        assertNull(questions.current.value)
    }
}
