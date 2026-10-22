package com.todokanai.composepracticenew.datasource

import java.io.File

/**
 * 이 항목 아래 [entry]를 이 항목 기준 '/' 구분 상대 경로로 바꾼다. 자기 자신이면 빈 문자열이다.
 * zip 항목 이름·원격 경로·표시용 경로처럼 OS 구분자와 무관하게 '/'를 쓰는 곳에서 사용한다.
 * @param entry 이 항목 자신 또는 그 아래 항목
 * @return '/' 구분 상대 경로
 */
internal fun File.slashRelativePathOf(entry: File): String = toPath().relativize(entry.toPath()).toString().replace("\\", "/")
