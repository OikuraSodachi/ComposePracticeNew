package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.FileNameError

/**
 * 이름을 쓸 저장소의 파일 시스템이 정하는 이름 제약(길이 한도, 쓸 수 없는 문자 등)을 판정하는 규칙.
 * 제약은 저장소(볼륨)마다 다르므로 이름이 놓일 위치로 규칙을 고른다. 앱이 모든 저장소에 강제하는 공통 규칙은 FileNameUseCase가 맡는다.
 */
interface StorageNameRules {
    /**
     * [fullName]을 [location]이 속한 저장소에 쓸 때 그 저장소가 받지 않는 규칙을 구한다.
     * @param fullName 저장될 전체 이름(덧붙는 확장자 포함)
     * @param location 이름이 놓일 저장소의 경로 — 새 항목이면 만들 폴더, 이름 변경이면 그 항목 자신
     * @return 위반한 규칙의 집합. 비어 있으면 저장소의 제약을 지킨다
     */
    fun violationsOf(fullName: String, location: String): Set<FileNameError>
}
