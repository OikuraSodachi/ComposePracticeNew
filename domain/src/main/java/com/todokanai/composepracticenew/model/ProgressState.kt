package com.todokanai.composepracticenew.model

/**
 * 진행 표시가 있는 파일 작업 하나의 진행 상태.
 * 작업은 진행 단계([ProgressPhase])만 보고하고, 작업 종류는 작업을 실행한 오케스트레이터가 붙인다.
 * @param operationType 진행 중인 작업 종류
 * @param phase 지금 단계와 그 단계의 진행
 */
data class ProgressState(
    val operationType: FileOperationType,
    val phase: ProgressPhase
)

/**
 * 파일 작업이 보고하는 진행 단계. 단계마다 담는 값이 다르므로 받는 쪽은 exhaustive `when`으로 단계를 가른다 —
 * 한 상태에 모든 값을 nullable로 담으면 있을 수 없는 조합(항목 수만 있고 전체가 없는 진행 등)도 표현되기 때문이다.
 * 진행 보고는 곧 작업 취소를 확인하는 자리이므로, 아직 셀 것이 없어도 [Starting]으로 보고할 수 있다.
 */
sealed interface ProgressPhase {

    /** 작업을 시작했지만 아직 진행을 셀 수 없는 상태(진행 보고 전, 대상을 훑기 전). */
    data object Starting : ProgressPhase

    /**
     * 항목 수로 세는 진행(삭제). 진행도는 처리한 항목 수의 비율이다.
     * @param processed 처리한 항목 수(0 이상 [total] 이하)
     * @param total 전체 항목 수(1 이상)
     * @param currentName 지금 처리하는 항목의 이름
     */
    data class Counted(
        val processed: Int,
        val total: Int,
        val currentName: String
    ) : ProgressPhase {
        /** 처리한 항목 수의 비율(0~100 퍼센트). */
        val percent: Int get() = processed * 100 / total
    }

    /**
     * 바이트로 세는 진행(복사·이동·압축·압축 해제·전송).
     * @param amount 작업 전체에 대한 바이트 진행. 크기를 모르는 파일이 섞였으면 퍼센트 없이 크기를 아는 부분의 진행이다
     * @param writtenBytes 지금까지 처리한 바이트 수
     * @param fileIndex 지금 처리하는 파일의 1-based 순번. 시작 전이면 0, 끝났으면 [fileCount]
     * @param fileCount 작업 대상 파일 개수
     * @param currentFile 지금 처리하는 파일. 시작·완료 보고처럼 처리 중인 파일이 없으면 null
     */
    data class Bytes(
        val amount: ByteAmount,
        val writtenBytes: Long,
        val fileIndex: Int,
        val fileCount: Int,
        val currentFile: CurrentFile?
    ) : ProgressPhase
}

/** 작업 전체에 대한 바이트 진행. 전체 크기를 아는지에 따라 담는 값이 다르다. */
sealed interface ByteAmount {

    /**
     * 모든 파일의 크기를 알아 퍼센트를 낼 수 있는 진행.
     * @param percent 진행도(0~100 퍼센트). 100은 작업 전체가 끝났다는 뜻이다
     * @param totalBytes 작업 전체 크기(bytes)
     */
    data class Known(
        val percent: Int,
        val totalBytes: Long
    ) : ByteAmount

    /**
     * 크기를 모르는 파일이 섞여 전체 퍼센트를 낼 수 없는 진행. 퍼센트를 지어내지 않고 크기를 아는 부분과 모르는 부분을 나눠 담는다.
     * @param knownSize 크기를 아는 부분과 모르는 부분의 진행
     */
    data class PartlyUnknown(
        val knownSize: KnownSizeProgress
    ) : ByteAmount
}

/**
 * 바이트 진행에서 지금 처리하는 파일.
 * @param name 파일 이름
 * @param bytes 파일의 전체 크기(bytes). 크기를 모르는 파일이면 null
 */
data class CurrentFile(
    val name: String,
    val bytes: Long?
)
