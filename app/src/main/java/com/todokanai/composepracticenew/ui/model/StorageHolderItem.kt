package com.todokanai.composepracticenew.ui.model

import androidx.compose.runtime.Immutable
import com.todokanai.composepracticenew.model.StorageHolderItem as DomainStorageHolderItem

/** 스토리지 목록 UI 표시용 앱 레이어 모델. domain 모듈의 StorageHolderItem을 앱 레이어에서 격리한다. */
@Immutable
data class StorageHolderItem(
    val absolutePath: String,
    val used: String,
    val total: String,
    val progress: Float
) {
    companion object {
        /** domain 모듈의 StorageHolderItem을 앱 레이어 모델로 변환한다. */
        fun from(domain: DomainStorageHolderItem) = StorageHolderItem(
            absolutePath = domain.absolutePath,
            used = domain.used,
            total = domain.total,
            progress = domain.progress
        )
    }
}
