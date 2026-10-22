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

    /** 작업이 실패했다. */
    sealed interface Failure : IoResult<Nothing> {
        /** 사용자에게 보여줄 실패 메시지. 원인을 알 수 없으면 null이다. */
        val message: String?
    }

    /**
     * 대상 하나가 실패했다. 저장소·세션은 계속 쓸 수 있으므로, 여러 대상을 처리하는 작업은 이 대상만 실패로 모으고 다음 대상을 진행한다.
     * @param message 표시할 실패 메시지. 원인을 알 수 없으면 null
     */
    data class ItemFailed(override val message: String?) : Failure

    /**
     * 여러 하위 항목 중 일부가 실패했다. 실패를 문자열이 아닌 목록으로 담아, 바깥 작업이 실패 목록을 합칠 수 있게 한다(중첩 평탄화).
     * @param failurePrefix 메시지 머리말(예: "삭제 실패")
     * @param failures 실패한 항목 목록. 비어 있지 않아야 한다
     */
    data class PartiallyFailed(val failurePrefix: String, val failures: List<FailedItem>) : Failure {
        init {
            require(failures.isNotEmpty()) { "실패한 항목이 없는 부분 실패는 만들 수 없다" }
        }

        /** 실패 목록으로 만든 표시 메시지. */
        override val message: String get() = partialFailureMessage(failurePrefix, failures)
    }

    /**
     * 원격 연결이 없거나 작업 도중 끊겨 남은 원격 작업을 진행할 수 없다.
     * 여러 대상을 처리하는 작업은 이 결과를 받으면 나머지 대상을 시도하지 않고 즉시 중단한다.
     * @param message 표시할 실패 메시지
     */
    data class ConnectionLost(override val message: String) : Failure

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
