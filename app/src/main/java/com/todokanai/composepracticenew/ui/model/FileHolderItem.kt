package com.todokanai.composepracticenew.ui.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import com.todokanai.composepracticenew.model.FileHolderItem as DomainFileHolderItem
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td
import kotlinx.parcelize.Parcelize
import java.io.File
import java.text.DateFormat

/**
 * 파일 목록 UI 표시용 앱 레이어 모델. domain 모듈의 FileHolderItem을 앱 레이어에서 격리하고, 표시용 문자열 포맷을 이 레이어에서 수행한다.
 * @param path 파일 절대 경로
 * @param isDirectory 디렉터리 여부
 * @param name 파일명
 * @param size 파일 크기(사람이 읽을 수 있는 문자열, 디렉터리는 빈 문자열)
 * @param lastModified 마지막 수정 시각 문자열
 * @param sizeBytes 파일 크기(bytes)
 * @param lastModifiedMillis 마지막 수정 시각(epoch millis)
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
    val lastModifiedMillis: Long = 0L,
    val data: String? = null
) : Parcelable {
    companion object {
        /**
         * domain 모델 목록을 앱 레이어 모델 목록으로 변환한다. 날짜 포맷터는 목록당 1개만 만들어 재사용한다.
         * @param domains 변환할 domain 모델 목록
         * @param isLocalFile 로컬 파일이면 true — URI 문자열(data)을 채운다
         */
        fun fromList(domains: List<DomainFileHolderItem>, isLocalFile: Boolean = true): List<FileHolderItem> {
            // DateFormat은 스레드 안전하지 않으므로 공유하지 않고 변환 1회 범위에서만 재사용
            val dateFormat = DateFormat.getDateTimeInstance()
            return domains.map { from(it, isLocalFile, dateFormat) }
        }

        /** domain 모듈의 FileHolderItem을 앱 레이어 모델로 변환하며 크기·날짜를 표시용 문자열로 포맷한다. */
        fun from(
            domain: DomainFileHolderItem,
            isLocalFile: Boolean = true,
            dateFormat: DateFormat = DateFormat.getDateTimeInstance()
        ) = FileHolderItem(
            path = domain.path,
            isDirectory = domain.isDirectory,
            name = domain.name,
            size = if (domain.isDirectory) "" else readableFileSize_td(domain.sizeBytes),
            lastModified = dateFormat.format(domain.lastModifiedMillis),
            sizeBytes = domain.sizeBytes,
            lastModifiedMillis = domain.lastModifiedMillis,
            data = if (isLocalFile) File(domain.path).toUri().toString() else null
        )
    }

    /** UseCase 호출 시 domain 모델로 역변환한다. */
    fun toDomain() = DomainFileHolderItem(
        path = path,
        isDirectory = isDirectory,
        name = name,
        sizeBytes = sizeBytes,
        lastModifiedMillis = lastModifiedMillis
    )
}
