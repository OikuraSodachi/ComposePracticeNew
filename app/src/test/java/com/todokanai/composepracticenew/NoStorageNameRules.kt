package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.repository.StorageNameRules

/** 저장소의 이름 제약이 없는 테스트용 이름 규칙. 이름 규칙을 다루지 않는 화면 테스트의 파일 작업 UseCase 조립에 쓴다. */
internal object NoStorageNameRules : StorageNameRules {
    override fun violationsOf(fullName: String, location: String): Set<FileNameError> = emptySet()
}
