package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ArchivePasswordPrompt
import com.todokanai.composepracticenew.model.ConflictAnswer
import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.PasswordAnswer
import com.todokanai.composepracticenew.model.QuestionKind
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.model.WriteConflictKind
import com.todokanai.composepracticenew.repository.ArchivePasswordProvider
import com.todokanai.composepracticenew.repository.OperationQuestionAsker
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** 작업 질문 하나에 답을 기다리는 최대 시간. 넘으면 작업을 실패로 끝낸다 — 답하지 않는 질문이 작업을 끝없이 붙잡지 않도록. */
internal val MAX_QUESTION_ANSWER_WAIT: Duration = 6.hours

/**
 * 실행 중에 사용자에게 묻는 작업이 받는 통로 묶음. 쓰기 충돌과 압축 파일 비밀번호를 같은 질문함으로 묻는다.
 * 충돌만 묻는 작업(복사·이동·다운로드)은 [WriteConflictResolver]로만 넘긴다.
 */
interface OperationPrompts : WriteConflictResolver, ArchivePasswordProvider

/**
 * 작업 하나의 질문을 사용자에게 묻는다. 쓰기 충돌은 이 작업에서 "나머지에도 적용"한 답이 있으면 묻지 않고 쓴다.
 * 작업은 항목을 차례로 처리하므로 한 번에 하나만 묻는다. 묻는 동안 그 작업의 알림을 답을 기다리는 상태로 바꾸고, 답을 받으면 [onAnswered]로 진행 알림을 되돌린다.
 * 질문 하나에 [MAX_QUESTION_ANSWER_WAIT] 동안 답이 없으면 [UnansweredQuestion]으로, 질문에서 취소하면 CancellationException으로 작업을 멈춘다.
 * @param operationType 이 작업의 종류
 * @param questions 질문을 맡기고 답을 기다리는 통로
 * @param onAwaiting 질문을 맡기기 직전에 질문 종류로 부르는 함수(알림을 답을 기다리는 상태로 바꾼다)
 * @param onAnswered 답을 받은 직후에 부르는 함수(진행 알림을 되돌린다)
 */
internal class OperationPrompter(
    private val operationType: FileOperationType,
    private val questions: OperationQuestionAsker,
    private val onAwaiting: (QuestionKind) -> Unit,
    private val onAnswered: () -> Unit,
) : OperationPrompts {

    /** "나머지에도 적용"으로 고른 처리 방법(겹침 종류별). */
    private val remembered = mutableMapOf<WriteConflictKind, ConflictChoice>()

    override suspend fun resolve(conflict: WriteConflict): ConflictChoice =
        remembered[conflict.kind] ?: choiceFromUser(conflict)

    override suspend fun passwordFor(archiveName: String, prompt: ArchivePasswordPrompt): String =
        when (val answer = awaitAnswer(QuestionKind.ARCHIVE_PASSWORD, archiveName) { questions.askPassword(operationType, archiveName, prompt) }) {
            PasswordAnswer.Cancel -> throw CancellationException("비밀번호 질문에서 작업을 취소함")
            is PasswordAnswer.Enter -> answer.password
        }

    /**
     * [conflict]를 묻고 답을 처리 방법으로 바꾼다. "나머지에도 적용"이면 [remembered]에 기억한다.
     * @param conflict 겹친 항목
     * @return 사용자가 고른 처리 방법
     * @throws UnansweredQuestion [MAX_QUESTION_ANSWER_WAIT] 동안 답이 없을 때
     * @throws CancellationException 사용자가 질문에서 취소했을 때
     */
    private suspend fun choiceFromUser(conflict: WriteConflict): ConflictChoice =
        when (val answer = awaitAnswer(QuestionKind.CONFLICT, conflict.name) { questions.askConflict(operationType, conflict) }) {
            ConflictAnswer.Cancel -> throw CancellationException("충돌 질문에서 작업을 취소함")
            is ConflictAnswer.Choose -> answer.choice.also { choice ->
                check(choice == ConflictChoice.SKIP || conflict.kind.canProceed) { "${conflict.kind} 겹침은 건너뛰기만 할 수 있습니다" }
                if (answer.applyToRest) remembered[conflict.kind] = choice
            }
        }

    /**
     * 알림을 답을 기다리는 상태로 바꾸고 [ask]의 답을 기다린다. 답을 받으면 진행 알림을 되돌린다.
     * @param kind 묻는 질문의 종류
     * @param name 질문이 가리키는 이름(답이 없어 멈출 때 알린다)
     * @param ask 질문을 맡기고 답을 기다리는 호출
     * @return 사용자의 답
     * @throws UnansweredQuestion [MAX_QUESTION_ANSWER_WAIT] 동안 답이 없을 때
     */
    private suspend fun <A : Any> awaitAnswer(kind: QuestionKind, name: String, ask: suspend () -> A): A {
        onAwaiting(kind)
        val answer = withTimeoutOrNull(MAX_QUESTION_ANSWER_WAIT) { ask() } ?: throw UnansweredQuestion(kind, name)
        onAnswered()
        return answer
    }
}

/**
 * 작업 질문에 [MAX_QUESTION_ANSWER_WAIT] 동안 답이 없어 작업을 멈춘다. 작업 구현이 취소와 같은 길로 멈추도록 CancellationException이며,
 * [FileOperationOrchestrator]가 받아 취소가 아닌 실패로 알린다.
 * @param kind 답을 받지 못한 질문의 종류
 * @param name 질문이 가리킨 이름(충돌이면 겹친 항목, 비밀번호면 압축 파일)
 */
internal class UnansweredQuestion(val kind: QuestionKind, val name: String) :
    CancellationException("작업 질문에 답이 없어 작업을 멈춤: $name")
