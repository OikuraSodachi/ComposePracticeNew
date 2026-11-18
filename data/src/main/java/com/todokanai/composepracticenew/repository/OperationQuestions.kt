package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ConflictAnswer
import com.todokanai.composepracticenew.model.ConflictQuestion
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.OperationQuestion
import com.todokanai.composepracticenew.model.PasswordAnswer
import com.todokanai.composepracticenew.model.PasswordQuestion
import com.todokanai.composepracticenew.model.QuestionRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 파일 작업이 맡긴 질문(충돌·비밀번호)을 답을 받거나 기다림이 끝날 때까지 보관하는 질문함. 보이는 화면이 없을 때 생긴 질문도 화면이 다시 보일 때 묻는다.
 * 여러 작업이 동시에 물으면 종류와 무관하게 맡긴 순서대로 하나씩 [current]로 내보낸다.
 */
@Singleton
class OperationQuestions @Inject constructor() : OperationQuestionAsker, OperationQuestionRepository {

    /** [entries]와 [nextId]를 한 단위로 바꾸는 잠금. 묻는 작업과 답하는 화면이 다른 스레드에서 부른다. */
    private val lock = Any()

    /** 마지막으로 발급한 질문 id. [lock] 안에서만 읽고 쓴다. */
    private var nextId = 0L

    /** 답을 기다리는 질문(맡긴 순서). [lock] 안에서만 읽고 쓴다. */
    private val entries = mutableListOf<Entry<*>>()

    /** 지금 물을 질문([current]의 원본). [publish]만 바꾼다. */
    private val _current = MutableStateFlow<OperationQuestion?>(null)
    override val current: StateFlow<OperationQuestion?> = _current.asStateFlow()

    override suspend fun <A : Any> ask(operationType: FileOperationType, request: QuestionRequest<A>): A =
        submit { id -> questionOf(id, operationType, request) }

    override fun answerConflict(id: Long, answer: ConflictAnswer) = answer<ConflictQuestion, ConflictAnswer>(id, answer)

    override fun answerPassword(id: Long, answer: PasswordAnswer) = answer<PasswordQuestion, PasswordAnswer>(id, answer)

    /**
     * [request]를 발급한 [id]의 질문으로 만든다.
     * @param id 발급한 질문 id
     * @param operationType 질문하는 작업의 종류
     * @param request 묻는 내용
     * @return 화면이 보고 답할 질문
     */
    private fun questionOf(id: Long, operationType: FileOperationType, request: QuestionRequest<*>): OperationQuestion = when (request) {
        is QuestionRequest.Conflict -> ConflictQuestion(id, operationType, request.conflict)
        is QuestionRequest.ArchivePassword -> PasswordQuestion(id, operationType, request.archiveName, request.prompt)
    }

    /**
     * 새 id로 만든 질문을 맡기고 답을 기다린다. 취소·시간 초과로 기다림이 끝나면 질문을 거둔다.
     * @param question 발급한 id로 질문을 만드는 함수
     * @return 사용자의 답
     */
    private suspend fun <A> submit(question: (Long) -> OperationQuestion): A {
        val entry = synchronized(lock) {
            Entry<A>(question(++nextId)).also { entries += it; publish() }
        }
        try {
            return entry.reply.await()
        } finally {
            // 답을 받은 질문은 answer가 이미 뺐다
            remove(entry)
        }
    }

    /**
     * [id] 질문이 [Q] 종류일 때만 [answer]로 답한다 — 종류가 다른 답이 질문에 닿지 않도록.
     * @param id 답할 질문의 id
     * @param answer 사용자의 답
     */
    private inline fun <reified Q : OperationQuestion, A> answer(id: Long, answer: A) {
        val entry = synchronized(lock) { entries.firstOrNull { it.question.id == id && it.question is Q } } ?: return
        // 답한 질문은 묻는 작업이 다시 이어지기 전에 빼서, 화면이 같은 질문을 다시 띄우지 않게 한다
        remove(entry)
        // Q 질문은 언제나 그 종류의 답을 기다리는 Entry로 맡겼다(questionOf — 요청의 답 타입이 질문 종류를 정한다)
        @Suppress("UNCHECKED_CAST")
        (entry as Entry<A>).reply.complete(answer)
    }

    /** [entry]를 질문함에서 빼고 [current]를 갱신한다. 이미 뺐으면 아무것도 하지 않는다. */
    private fun remove(entry: Entry<*>) = synchronized(lock) {
        if (entries.remove(entry)) publish()
    }

    /** 가장 먼저 맡긴 질문을 [current]로 내보낸다. [lock] 안에서 부른다. */
    private fun publish() {
        _current.value = entries.firstOrNull()?.question
    }

    /**
     * 답을 기다리는 질문 하나.
     * @param question 질문
     */
    private class Entry<A>(val question: OperationQuestion) {
        /** 질문에 온 답. */
        val reply = CompletableDeferred<A>()
    }
}
