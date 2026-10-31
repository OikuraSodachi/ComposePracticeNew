package com.todokanai.composepracticenew.model

/**
 * 크기를 모르는 파일이 섞여 전체 진행률을 알 수 없는 작업에서, 크기를 아는 파일과 모르는 파일을 나눈 진행.
 * 크기를 아는 부분은 [knownCompletedBytes]가 [knownTotalBytes]에 닿았을 때만 끝난 것이다 — [knownWrittenBytes]가 합에 닿아도 처리 완료(서버 ACK) 전이면 끝나지 않았다.
 * @param knownTotalBytes 크기를 아는 파일들의 크기 합
 * @param knownWrittenBytes 크기를 아는 파일들의 진행 바이트. 완료된 파일의 알려진 크기와 받는 중인 파일의 몫(알려진 크기까지)의 합이며, 실패한 파일은 들어가지 않는다
 * @param knownCompletedBytes 크기를 아는 파일 중 처리 완료된 파일들의 알려진 크기 합
 * @param unknownFileCount 크기를 모르는 파일 수(1 이상)
 * @param unknownWrittenBytes 크기를 모르는 파일들에서 처리한 바이트
 */
data class KnownSizeProgress(
    val knownTotalBytes: Long,
    val knownWrittenBytes: Long,
    val knownCompletedBytes: Long,
    val unknownFileCount: Int,
    val unknownWrittenBytes: Long
)
