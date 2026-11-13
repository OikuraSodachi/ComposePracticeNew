package com.todokanai.composepracticenew.model

/**
 * 처리를 시작하기 전에 대상 트리를 훑는 준비 단계(전체 크기·항목 수 산정, 원격 트리 수집)의 진행.
 * 훑기가 끝나야 전체를 알 수 있으므로 퍼센트 없이 지금까지 찾은 항목 수만 담는다 — 훑는 동안에도 작업이 멈추지 않았음을 보이도록.
 * @param foundEntries 지금까지 찾은 항목 수
 * @param currentName 마지막으로 찾은 항목의 이름
 */
data class PreparationProgress(
    val foundEntries: Int,
    val currentName: String
)
