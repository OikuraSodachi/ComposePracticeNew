package com.todokanai.composepracticenew.ui.model

import androidx.compose.runtime.Immutable

/**
 * breadcrumb 경로 표시용 앱 레이어 모델.
 * @param name 디렉터리 이름
 * @param path 절대 경로 문자열
 */
@Immutable
data class DirectoryItem(
    val name: String,
    val path: String
)
