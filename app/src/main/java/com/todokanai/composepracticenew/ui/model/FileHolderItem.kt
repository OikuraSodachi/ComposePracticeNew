package com.todokanai.composepracticenew.ui.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import com.todokanai.composepracticenew.model.FileHolderItem as DomainFileHolderItem
import kotlinx.parcelize.Parcelize
import java.io.File

/**
 * 파일 목록 UI 표시용 앱 레이어 모델. domain 모듈의 FileHolderItem을 앱 레이어에서 격리한다.
 * @param path 파일 절대 경로
 * @param isDirectory 디렉터리 여부
 * @param name 파일명
 * @param size 파일 크기(사람이 읽을 수 있는 문자열)
 * @param lastModified 마지막 수정 시각 문자열
 * @param sizeBytes 파일 크기(bytes)
 * @param data 로컬 파일의 URI 문자열; 원격 파일이면 null
 */
@Immutable
@Parcelize
data class FileHolderItem(
    val path: String,
    val isDirectory: Boolean,
    val name: String,
    val size: String,
    val lastModified: String,
    val sizeBytes: Long = 0L,
    val data: String? = null
) : Parcelable {
    companion object {
        /** domain 모듈의 FileHolderItem을 앱 레이어 모델로 변환한다. */
        fun from(domain: DomainFileHolderItem, isLocalFile: Boolean = true) = FileHolderItem(
            path = domain.path,
            isDirectory = domain.isDirectory,
            name = domain.name,
            size = domain.size,
            lastModified = domain.lastModified,
            sizeBytes = domain.sizeBytes,
            data = if (isLocalFile) File(domain.path).toUri().toString() else null
        )
    }

    /** UseCase 호출 시 domain 모델로 역변환한다. */
    fun toDomain() = DomainFileHolderItem(
        path = path,
        isDirectory = isDirectory,
        name = name,
        size = size,
        lastModified = lastModified,
        sizeBytes = sizeBytes
    )
}
