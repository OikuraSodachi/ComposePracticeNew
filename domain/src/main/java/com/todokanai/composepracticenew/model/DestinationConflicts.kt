package com.todokanai.composepracticenew.model

/**
 * 확인된 목적지 목록으로 판정한 이름 충돌 결과. 실행할 목적지를 판정에 쓴 목록의 경로로 고정해, 판정한 폴더와 실행하는 폴더가 달라지지 않게 한다.
 * @param destinationPath 충돌을 판정한 목록의 경로 — 작업은 이 경로에 실행한다
 * @param conflicts 목적지에 같은 이름이 이미 있는 작업 대상 항목(원래 순서 유지)
 */
data class DestinationConflicts<T>(val destinationPath: String, val conflicts: List<T>)
