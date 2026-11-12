package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ConflictAnswer
import com.todokanai.composepracticenew.model.ConflictQuestion
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.WriteConflict
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 파일 작업이 맡긴 충돌 질문을 답을 받거나 기다림이 끝날 때까지 보관하는 질문함. 보이는 화면이 없을 때 생긴 질문도 화면이 다시 보일 때 묻는다.
 * 여러 작업이 동시에 물으면 맡긴 순서대로 하나씩 [current]로 내보낸다.
 */
@Singleton
class ConflictQuestions @Inject constructor() : ConflictQuestionAsker, ConflictQuestionRepository {

    /** [entries]와 [nextId]를 한 단위로 바꾸는 잠금. 묻는 작업과 답하는 화면이 다른 스레드에서 부른다. */
    private val lock = Any()

    /** 마지막으로 발급한 질문 id. [lock] 안에서만 읽고 쓴다. */
    private var nextId = 0L

    /** 답을 기다리는 질문(맡긴 순서). [lock] 안에서만 읽고 쓴다. */
    private val entries = mutableListOf<Entry>()

    private val _current = MutableStateFlow<ConflictQuestion?>(null)
    override val current: StateFlow<ConflictQuestion?> = _current.asStateFlow()

    override suspend fun ask(operationType: FileOperationType, conflict: WriteConflict): ConflictAnswer {
        val entry = synchronized(lock) {
            Entry(ConflictQuestion(++nextId, operationType, conflict)).also { entries += it; publish() }
        }
        try {
            return entry.reply.await()
        } finally {
            // 취소·시간 초과로 기다림이 끝나면 질문을 거둔다. 답을 받은 질문은 answer가 이미 뺐다
            remove(entry)
        }
    }

    override fun answer(id: Long, answer: ConflictAnswer) {
        val entry = synchronized(lock) { entries.firstOrNull { it.question.id == id } } ?: return
        // 답한 질문은 묻는 작업이 다시 이어지기 전에 빼서, 화면이 같은 질문을 다시 띄우지 않게 한다
        remove(entry)
        entry.reply.complete(answer)
    }

    /** [entry]를 질문함에서 빼고 [current]를 갱신한다. 이미 뺐으면 아무것도 하지 않는다. */
    private fun remove(entry: Entry) = synchronized(lock) {
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
    private class Entry(val question: ConflictQuestion) {
        /** 질문에 온 답. */
        val reply = CompletableDeferred<ConflictAnswer>()
    }
}
