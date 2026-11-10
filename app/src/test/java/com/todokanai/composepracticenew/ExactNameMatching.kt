package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.repository.StorageNameMatching

/** 이름이 정확히 같을 때만 같은 항목으로 보는 테스트용 이름 비교 규칙. 이름 비교 규칙을 다루지 않는 테스트가 쓴다. */
internal object ExactNameMatching : StorageNameMatching {
    override fun matchKeyOf(name: String, location: String): String = name
}
