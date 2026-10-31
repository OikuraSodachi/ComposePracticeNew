package com.todokanai.composepracticenew.data.progress

/** [ByteProgressTracker]가 진행률을 계산하는 기준이 되는 작업 전체의 크기. 크기를 모르는 파일이 섞였는지를 타입으로 구분한다. */
sealed interface TransferTotal {

    /**
     * 모든 파일의 크기를 안다.
     * @param bytes 작업 대상 전체 바이트 수(0 나눗셈 방지를 위해 1 이상)
     */
    data class Known(val bytes: Long) : TransferTotal

    /**
     * 크기를 모르는 파일이 있어 전체 크기를 모른다. 크기를 아는 부분만 따로 센다.
     * @param knownBytes 크기를 아는 파일들의 크기 합
     * @param unknownFileCount 크기를 모르는 파일 수(1 이상)
     */
    data class Partial(val knownBytes: Long, val unknownFileCount: Int) : TransferTotal
}
