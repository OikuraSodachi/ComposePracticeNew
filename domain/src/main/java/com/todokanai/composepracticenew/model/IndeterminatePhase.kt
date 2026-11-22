package com.todokanai.composepracticenew.model

/**
 * 퍼센트 없이 움직이는 막대로 보이는 진행 단계. 끝나야 전체를 알 수 있거나 진행량을 셀 수 없는 단계다 —
 * 그동안에도 작업이 멈추지 않았음을 보이고, 진행 보고가 작업 취소를 확인하는 자리가 되도록 보고한다.
 * 진행 단계([ProgressPhase])의 한 갈래다. 단계마다 보일 문구가 다르므로, 받는 쪽은 exhaustive `when`으로 단계를 가른다.
 */
sealed interface IndeterminatePhase : ProgressPhase {

    /**
     * 처리를 시작하기 전에 대상 트리를 훑는 준비 단계(전체 크기·항목 수 산정, 원격 트리 수집).
     * 훑기가 끝나야 전체를 알 수 있으므로 지금까지 찾은 항목 수만 담는다.
     * @param foundEntries 지금까지 찾은 항목 수
     * @param currentName 마지막으로 찾은 항목의 이름
     */
    data class Preparing(
        val foundEntries: Int,
        val currentName: String
    ) : IndeterminatePhase

    /**
     * 압축 해제 전에 비밀번호를 압축 파일의 내용으로 확인하는 단계. 확인할 항목에 닿기까지 앞 항목을 모두 풀어야 하는 형식(solid RAR)이 있어 오래 걸릴 수 있다.
     * @param archiveName 비밀번호를 확인하는 압축 파일의 표시 이름
     */
    data class CheckingPassword(
        val archiveName: String
    ) : IndeterminatePhase
}
