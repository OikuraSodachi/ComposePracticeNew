package com.todokanai.composepracticenew.model

/**
 * 파일 입출력 작업(로컬·원격 파일 작업, 목록 조회)의 결과.
 * 실패의 의미(이 대상만 실패 / 일부 하위 항목 실패 / 연결 끊김)를 예외 종류나 catch 순서가 아니라 타입으로 나타내,
 * 받는 쪽이 `when`으로 모든 경우를 빠짐없이 다루게 한다. 빠뜨리면 컴파일러가 알린다.
 * 예외는 취소와 구현 오류에만 쓴다.
 */
sealed interface IoResult<out T> {

    /**
     * 작업이 성공했다.
     * @param value 작업 결과값
     */
    data class Success<out T>(val value: T) : IoResult<T>

    /** 작업이 실패했다. 표시 문구는 app 레이어가 실패 종류와 원인([FailureReason])으로 정한다(#218). */
    sealed interface Failure : IoResult<Nothing>

    /**
     * 대상 하나가 실패했다. 저장소·세션은 계속 쓸 수 있으므로, 여러 대상을 처리하는 작업은 이 대상만 실패로 모으고 다음 대상을 진행한다.
     * @param reason 실패 원인. 원인을 알 수 없으면 null
     */
    data class ItemFailed(val reason: FailureReason?) : Failure

    /**
     * 여러 하위 항목 중 일부가 실패했다. 실패를 문자열이 아닌 목록으로 담아, 바깥 작업이 실패 목록을 합칠 수 있게 한다(중첩 평탄화).
     * 머리말은 담지 않는다 — 실패를 알리는 쪽이 작업 종류로 정한다(안쪽 수집의 머리말은 평탄화되며 버려지므로).
     * @param failures 실패한 항목 목록. 비어 있지 않아야 한다
     */
    data class PartiallyFailed(val failures: List<FailedItem>) : Failure {
        init {
            require(failures.isNotEmpty()) { "실패한 항목이 없는 부분 실패는 만들 수 없다" }
        }
    }

    /**
     * 원격 연결이 없거나 작업 도중 끊겨 남은 원격 작업을 진행할 수 없다.
     * 여러 대상을 처리하는 작업은 이 결과를 받으면 나머지 대상을 시도하지 않고 즉시 중단한다.
     * @param reason 실패 원인
     */
    data class ConnectionLost(val reason: FailureReason) : Failure

    companion object {
        /** 결과값 없이 성공한 작업의 결과. */
        val Done: IoResult<Unit> = Success(Unit)
    }
}

/**
 * 성공이면 [next]를 이어 실행하고, 실패면 그 실패를 그대로 돌려준다.
 * @param next 성공 결과값으로 이어서 실행할 작업
 * @return [next]의 결과, 또는 이 실패
 */
inline fun <T, R> IoResult<T>.andThen(next: (T) -> IoResult<R>): IoResult<R> = when (this) {
    is IoResult.Success -> next(value)
    is IoResult.Failure -> this
}

/**
 * 성공이면 결과값을 [transform]으로 바꾸고, 실패면 그 실패를 그대로 돌려준다.
 * @param transform 성공 결과값을 바꾸는 함수
 * @return 바뀐 결과값을 담은 성공, 또는 이 실패
 */
inline fun <T, R> IoResult<T>.map(transform: (T) -> R): IoResult<R> = when (this) {
    is IoResult.Success -> IoResult.Success(transform(value))
    is IoResult.Failure -> this
}

/**
 * 실패의 원인을 [wrap]으로 감싼다(예: 저장소가 알린 원인을 "이름 변경 실패"의 원인으로). 실패 종류는 그대로 둬,
 * 연결 끊김을 받은 여러 대상 작업이 남은 대상을 시도하지 않게 한다. 부분 실패는 항목별 원인을 이미 담고 있으므로 그대로 돌려준다.
 * @param wrap 원래 원인(모르면 null)을 받아 감싼 원인을 만드는 함수
 * @return 성공·부분 실패면 이 결과 그대로, 그 밖의 실패면 원인을 감싼 같은 종류의 실패
 */
inline fun <T> IoResult<T>.wrappingFailureReason(wrap: (FailureReason?) -> FailureReason): IoResult<T> = when (this) {
    is IoResult.Success, is IoResult.PartiallyFailed -> this
    is IoResult.ItemFailed -> IoResult.ItemFailed(wrap(reason))
    is IoResult.ConnectionLost -> IoResult.ConnectionLost(wrap(reason))
}

/**
 * 이 실패를 [path] 항목의 실패 목록으로 바꾼다. 부분 실패는 항목마다 [path] 아래 경로로 옮긴다(중첩 평탄화).
 * @param path 이 실패를 낸 항목의 경로. 빈 문자열이면 작업 대상 자신이다
 * @return 실패한 항목 목록
 */
fun IoResult.Failure.failedItemsAt(path: String): List<FailedItem> = when (this) {
    is IoResult.ItemFailed -> listOf(FailedItem(path, reason))
    is IoResult.ConnectionLost -> listOf(FailedItem(path, reason))
    is IoResult.PartiallyFailed -> failures.map { it.under(path) }
}

/**
 * 성공 결과값을 꺼낸다. 실패면 [onFailure]가 흐름을 끝낸다 — 보통 `valueOr { return it }`처럼 그 실패를 그대로 돌려주는 데 쓴다.
 * @param onFailure 실패를 받아 호출부의 흐름을 끝내는 함수
 * @return 성공 결과값
 */
inline fun <T> IoResult<T>.valueOr(onFailure: (IoResult.Failure) -> Nothing): T = when (this) {
    is IoResult.Success -> value
    is IoResult.Failure -> onFailure(this)
}

/**
 * 성공이면 [action]을 실행한다.
 * @param action 성공 결과값으로 실행할 작업
 * @return 이 결과 그대로
 */
inline fun <T> IoResult<T>.onSuccess(action: (T) -> Unit): IoResult<T> = also { if (it is IoResult.Success) action(it.value) }
