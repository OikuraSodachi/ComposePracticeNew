package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.repository.StorageNameRules

/** 저장소의 이름 제약이 없는 테스트용 이름 규칙. 저장소의 제약을 다루지 않는 테스트가 쓴다. */
internal object NoStorageNameRules : StorageNameRules {
    override fun violationsOf(fullName: String, location: String): Set<FileNameError> = emptySet()
}

/**
 * 받은 이름과 위치를 기록하고 정해 둔 위반을 돌려주는 테스트용 이름 규칙.
 * @param violations 모든 호출에 돌려줄 위반 규칙 집합
 */
internal class RecordingStorageNameRules(private val violations: Set<FileNameError> = emptySet()) : StorageNameRules {
    /** 받은 (전체 이름, 위치) 쌍. */
    val calls = mutableListOf<Pair<String, String>>()

    override fun violationsOf(fullName: String, location: String): Set<FileNameError> {
        calls += fullName to location
        return violations
    }
}
