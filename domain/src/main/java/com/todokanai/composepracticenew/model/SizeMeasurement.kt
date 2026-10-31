package com.todokanai.composepracticenew.model

/**
 * 항목들의 크기(하위 항목 포함) 측정 결과.
 * 읽지 못한 하위 항목이 있으면 [LowerBound]로 구분해, 받는 쪽이 일부 실패를 정상 합계로 보이지 않게 `when`으로 다루게 한다.
 */
sealed interface SizeMeasurement {

    /** 측정한 크기 합(bytes). [LowerBound]면 읽지 못한 항목을 뺀 하한이다. */
    val bytes: Long

    /**
     * 모든 하위 항목을 읽어 정확한 합계를 구했다.
     * @param bytes 크기 합(bytes)
     */
    data class Complete(override val bytes: Long) : SizeMeasurement

    /**
     * 일부 하위 항목을 읽지 못해, 읽은 항목만 더한 하한을 구했다.
     * @param bytes 읽은 항목의 크기 합(bytes)
     * @param unreadable 읽지 못한 항목 목록. 비어 있지 않아야 한다
     */
    data class LowerBound(override val bytes: Long, val unreadable: List<FailedItem>) : SizeMeasurement {
        init {
            require(unreadable.isNotEmpty()) { "읽지 못한 항목이 없는 하한은 만들 수 없다" }
        }
    }

    /**
     * 측정 밖에서 이미 알고 있는 크기를 더한다. 하한이면 하한으로 남는다.
     * @param extraBytes 더할 크기(bytes)
     * @return 크기를 더한 같은 종류의 측정 결과
     */
    fun withExtraBytes(extraBytes: Long): SizeMeasurement = when (this) {
        is Complete -> copy(bytes = bytes + extraBytes)
        is LowerBound -> copy(bytes = bytes + extraBytes)
    }
}
