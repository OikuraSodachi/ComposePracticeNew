package com.todokanai.composepracticenew.ui.model

import androidx.compose.runtime.Immutable

/** breadcrumb 경로 표시용 앱 레이어 모델. */
@Immutable
data class DirectoryItem(
    val name: String,
    val path: String
)
