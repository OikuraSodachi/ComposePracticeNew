package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.repository.StoragePathRules

/** 로컬 경로 규칙('/' 기준 부모·마지막 구간 이름)을 따르는 가짜 경로 규칙. 로컬 파일 작업 UseCase의 경로 규칙 의존에 쓴다. */
internal class LocalPathRules : StoragePathRules {
    override fun getParentPath(path: String): String? = path.substringBeforeLast('/').takeIf { it.isNotEmpty() && it != path }
    override fun displayNameOf(path: String): String = path.substringAfterLast('/').ifEmpty { path }
}
