package com.todokanai.composepracticenew.data.progress

/** [ByteProgressTracker]가 진행률을 계산하는 기준이 되는 작업 전체의 크기. 크기를 모르는 파일이 섞였는지를 타입으로 구분한다. */
sealed interface TransferTotal {

    /**
     * 모든 파일의 크기를 안다. 0은 모두 빈 파일인 작업이다.
     * @param bytes 작업 대상 전체 바이트 수(0 이상)
     */
    data class Known(val bytes: Long) : TransferTotal {
        init {
            require(bytes >= 0) { "전체 크기는 0 이상이어야 한다: $bytes" }
        }
    }

    /**
     * 크기를 모르는 파일이 있어 전체 크기를 모른다. 크기를 아는 부분만 따로 센다.
     * 크기를 모르는 파일이 없으면 [Known]이다 — 그때 이 타입을 쓰면 모든 크기를 아는데도 진행률을 모른다고 보고하게 되므로 만들 때 거부한다.
     * @param knownBytes 크기를 아는 파일들의 크기 합(0 이상)
     * @param unknownFileCount 크기를 모르는 파일 수(1 이상)
     */
    data class Partial(val knownBytes: Long, val unknownFileCount: Int) : TransferTotal {
        init {
            require(knownBytes >= 0) { "크기를 아는 부분의 합은 0 이상이어야 한다: $knownBytes" }
            require(unknownFileCount >= 1) { "크기를 모르는 파일이 없으면 Known이다: $unknownFileCount" }
        }
    }
}
