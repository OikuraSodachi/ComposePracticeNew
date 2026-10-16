package com.todokanai.composepracticenew.ui.model

import androidx.compose.runtime.Immutable
import com.todokanai.composepracticenew.model.StorageVolumeInfo
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td

/**
 * 스토리지 목록 UI 표시용 앱 레이어 모델. 표시용 문자열 포맷과 사용률 계산을 이 레이어에서 수행한다.
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
        /** domain 모듈의 원시 볼륨 정보를 표시용 모델로 변환한다. */
        fun from(info: StorageVolumeInfo): StorageHolderItem {
            val usedBytes = info.totalSpace - info.freeSpace
            return StorageHolderItem(
                absolutePath = info.path,
                used = readableFileSize_td(usedBytes),
                total = readableFileSize_td(info.totalSpace),
                progress = usageRatio(usedBytes, info.totalSpace)
            )
        }

        /** 전체 용량 대비 사용 용량 비율을 반환한다. 전체 용량이 0이면 0을 반환한다. */
        private fun usageRatio(usedBytes: Long, totalSpace: Long): Float =
            if (totalSpace == 0L) 0f else (usedBytes.toDouble() / totalSpace.toDouble()).toFloat()
    }
}
