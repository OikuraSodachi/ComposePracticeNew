package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ConflictAnswer
import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.model.WriteConflictKind
import com.todokanai.composepracticenew.repository.ConflictQuestionAsker
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** 충돌 질문 하나에 답을 기다리는 최대 시간. 넘으면 작업을 실패로 끝낸다 — 답하지 않는 질문이 작업을 끝없이 붙잡지 않도록. */
internal val MAX_CONFLICT_ANSWER_WAIT: Duration = 6.hours

/**
 * 작업 하나의 쓰기 충돌을 사용자에게 묻고, 이 작업에서 "나머지에도 적용"한 답이 있으면 묻지 않고 쓴다.
 * 작업은 항목을 차례로 쓰므로 한 번에 하나만 묻는다. 묻는 동안 그 작업의 알림을 답을 기다리는 상태로 바꾸고, 답을 받으면 [onAnswered]로 진행 알림을 되돌린다.
 * @param operationType 이 작업의 종류
 * @param questions 질문을 맡기고 답을 기다리는 통로
 * @param onAwaiting 질문을 맡기기 직전에 부르는 함수(알림을 답을 기다리는 상태로 바꾼다)
 * @param onAnswered 답을 받은 직후에 부르는 함수(진행 알림을 되돌린다)
 * @param maxWait 질문 하나에 답을 기다리는 최대 시간
 */
internal class OperationConflictResolver(
    private val operationType: FileOperationType,
    private val questions: ConflictQuestionAsker,
    private val onAwaiting: () -> Unit,
    private val onAnswered: () -> Unit,
    private val maxWait: Duration = MAX_CONFLICT_ANSWER_WAIT,
) : WriteConflictResolver {

    /** "나머지에도 적용"으로 고른 처리 방법(겹침 종류별). */
    private val remembered = mutableMapOf<WriteConflictKind, ConflictChoice>()

    override suspend fun resolve(conflict: WriteConflict): ConflictChoice =
        remembered[conflict.kind] ?: choiceFromUser(conflict)

    /**
     * [conflict]를 묻고 답을 처리 방법으로 바꾼다. "나머지에도 적용"이면 [remembered]에 기억한다.
     * @param conflict 겹친 항목
     * @return 사용자가 고른 처리 방법
     * @throws UnansweredConflict [maxWait] 동안 답이 없을 때
     * @throws CancellationException 사용자가 질문에서 취소했을 때
     */
    private suspend fun choiceFromUser(conflict: WriteConflict): ConflictChoice {
        onAwaiting()
        val answer = withTimeoutOrNull(maxWait) { questions.ask(operationType, conflict) } ?: throw UnansweredConflict(conflict)
        onAnswered()
        return when (answer) {
            ConflictAnswer.Cancel -> throw CancellationException("충돌 질문에서 작업을 취소함")
            is ConflictAnswer.Choose -> answer.choice.also { choice ->
                check(choice == ConflictChoice.SKIP || conflict.kind.canProceed) { "${conflict.kind} 겹침은 건너뛰기만 할 수 있습니다" }
                if (answer.applyToRest) remembered[conflict.kind] = choice
            }
        }
    }
}

/**
 * 충돌 질문에 [MAX_CONFLICT_ANSWER_WAIT] 동안 답이 없어 작업을 멈춘다. 쓰기 구현이 취소와 같은 길로 멈추도록 CancellationException이며,
 * [FileOperationOrchestrator]가 받아 취소가 아닌 실패로 알린다.
 * @param conflict 답을 받지 못한 겹침
 */
internal class UnansweredConflict(val conflict: WriteConflict) :
    CancellationException("충돌 질문에 답이 없어 작업을 멈춤: ${conflict.name}")
