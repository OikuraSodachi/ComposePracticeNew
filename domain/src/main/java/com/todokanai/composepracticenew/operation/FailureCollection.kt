package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.IoResult

/**
 * [items]의 각 항목에 [action]을 수행한다. 한 항목이 실패해도 나머지를 끝까지 진행하고, 실패한 항목이 있으면 모아서 한 번에 알린다.
 * 연결 끊김([IoResult.ConnectionLost])은 남은 항목도 진행할 수 없으므로 모으지 않고 즉시 돌려준다.
 * 앞선 실패 때문에 성공할 수 없는 항목(예: 지우지 못한 파일을 품은 폴더)은 [isBlockedBy]로 걸러 시도하지도, 실패로 세지도 않는다.
 * 그래서 실패 메시지에는 실제 원인 항목만 남는다.
 * 항목 작업이 [IoResult.PartiallyFailed]를 돌려주면 감싸지 않고, 그 실패 목록을 이 항목 이름 아래 경로로 옮겨 합친다(중첩 평탄화).
 * 취소와 구현 오류(예외)는 결과가 아니므로 잡지 않고 그대로 전파한다.
 * @param items 처리할 항목
 * @param failurePrefix 실패 메시지 머리말(예: "삭제 실패")
 * @param nameOf 실패 경로로 쓸 항목 이름. 바깥 작업 기준 상대 경로이며, 빈 문자열이면 작업 대상 자신이다
 * @param isBlockedBy (항목, 앞서 실패한 항목)을 받아 그 실패 때문에 항목을 건너뛰어야 하면 true를 반환한다. 기본은 항목이 서로 독립이다
 * @param action 항목 하나를 처리하는 작업
 * @return 모두 성공하면 성공, 실패한 항목이 있으면 그 목록을 담은 [IoResult.PartiallyFailed], 연결이 끊기면 [IoResult.ConnectionLost]
 */
suspend fun <T> forEachCollectingFailures(
    items: Collection<T>,
    failurePrefix: String,
    nameOf: (T) -> String,
    isBlockedBy: (item: T, failed: T) -> Boolean = { _, _ -> false },
    action: suspend (T) -> IoResult<Unit>
): IoResult<Unit> {
    val failedItems = mutableListOf<T>()
    val failures = mutableListOf<FailedItem>()
    for (item in items) {
        if (failedItems.any { failed -> isBlockedBy(item, failed) }) continue
        when (val result = action(item)) {
            is IoResult.Success -> Unit
            is IoResult.ConnectionLost -> return result
            is IoResult.PartiallyFailed -> {
                failedItems += item
                failures += result.failures.map { it.under(nameOf(item)) }
            }
            is IoResult.ItemFailed -> {
                failedItems += item
                failures += FailedItem(nameOf(item), result.message)
            }
        }
    }
    return if (failures.isEmpty()) IoResult.Done else IoResult.PartiallyFailed(failurePrefix, failures)
}
