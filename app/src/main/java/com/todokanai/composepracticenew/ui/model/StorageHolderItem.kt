package com.todokanai.composepracticenew.ui.model

import androidx.compose.runtime.Immutable
import com.todokanai.composepracticenew.model.StorageHolderItem as DomainStorageHolderItem

/**
 * 스토리지 목록 UI 표시용 앱 레이어 모델. domain 모듈의 StorageHolderItem을 앱 레이어에서 격리한다.
 * @param absolutePath 스토리지 절대 경로
 * @param used 사용 중인 용량(사람이 읽을 수 있는 문자열)
 * @param total 전체 용량(사람이 읽을 수 있는 문자열)
 * @param progress 사용률(0.0~1.0)
 */
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
