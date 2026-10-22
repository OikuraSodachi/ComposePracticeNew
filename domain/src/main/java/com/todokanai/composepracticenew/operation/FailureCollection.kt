package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.RemoteConnectionLostException
import kotlinx.coroutines.CancellationException

/** 여러 항목 실패 메시지에 이름을 나열할 최대 항목 수. 나머지는 "외 n개"로 줄인다. */
private const val MAX_LISTED_FAILURES = 3

/**
 * [items]의 각 항목에 [action]을 수행한다. 한 항목이 실패해도 나머지를 끝까지 진행하고, 실패한 항목이 있으면 마지막에 한 번에 예외로 알린다.
 * 취소와 원격 연결 끊김([RemoteConnectionLostException])은 남은 항목도 진행할 수 없으므로 모으지 않고 즉시 전파한다.
 * @param items 처리할 항목
 * @param failurePrefix 실패 메시지 머리말(예: "삭제 실패")
 * @param nameOf 실패 메시지에 표시할 항목 이름
 * @param action 항목 하나를 처리하는 작업. 실패는 예외로 알린다
 * @throws Exception 실패한 항목이 하나 이상이면 실패 개수와 항목 이름을 메시지에 담아 던진다
 */
suspend fun <T> forEachCollectingFailures(
    items: Collection<T>,
    failurePrefix: String,
    nameOf: (T) -> String,
    action: suspend (T) -> Unit
) {
    val failures = mutableListOf<Pair<String, Exception>>()
    for (item in items) {
        try {
            action(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RemoteConnectionLostException) {
            throw e
        } catch (e: Exception) {
            failures += nameOf(item) to e
        }
    }
    if (failures.isNotEmpty()) throw Exception(failureMessage(failurePrefix, failures, items.size))
}

/**
 * 모은 실패로 표시할 메시지를 만든다. 실패가 하나면 그 원인을, 여럿이면 개수와 이름 일부를 담는다.
 * @param prefix 메시지 머리말
 * @param failures 실패한 항목 이름과 원인 예외
 * @param total 처리한 전체 항목 수
 * @return 표시할 실패 메시지
 */
private fun failureMessage(prefix: String, failures: List<Pair<String, Exception>>, total: Int): String {
    if (failures.size == 1) {
        val (name, cause) = failures.single()
        return "$prefix: $name" + (cause.message?.let { " ($it)" } ?: "")
    }
    val listed = failures.take(MAX_LISTED_FAILURES).joinToString { it.first }
    val omitted = failures.size - MAX_LISTED_FAILURES
    val omittedSuffix = if (omitted > 0) " 외 ${omitted}개" else ""
    return "$prefix: ${total}개 중 ${failures.size}개 실패 ($listed$omittedSuffix)"
}
