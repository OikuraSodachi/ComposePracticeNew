package com.todokanai.composepracticenew.ui.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import com.todokanai.composepracticenew.model.FileHolderItem as DomainFileHolderItem
import com.todokanai.composepracticenew.model.FileNameParts
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td
import com.todokanai.composepracticenew.ui.thumbnail.VideoThumbnail
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.io.File
import java.text.DateFormat

/**
 * 파일 목록 UI 표시용 앱 레이어 모델. domain 모듈의 FileHolderItem을 앱 레이어에서 격리하고, 표시용 문자열 포맷을 이 레이어에서 수행한다.
 * @param path 파일 절대 경로
 * @param isDirectory 디렉터리 여부
 * @param name 파일명
 * @param size 파일 크기(사람이 읽을 수 있는 문자열, 크기가 없으면 빈 문자열)
 * @param lastModified 마지막 수정 시각 문자열
 * @param sizeBytes 파일 크기(bytes). 디렉터리와 크기를 알 수 없는 파일은 null
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
    val sizeBytes: Long?,
    val lastModifiedMillis: Long,
    val data: String? = null
) : Parcelable {

    /** 아이콘·미리보기 방식을 고르는 항목 종류. name·isDirectory에서 생성 시 한 번 정하므로 두 값과 어긋날 수 없다. */
    @IgnoredOnParcel
    val fileType: FileType = FileType.of(name, isDirectory)

    /** 이름이 점으로 시작하는 숨김 항목인지 여부. name에서 생성 시 한 번 정한다. */
    @IgnoredOnParcel
    val isHidden: Boolean = FileNameParts.isHidden(name)

    /**
     * 목록에 표시할 썸네일의 Coil 요청 모델. 이미지는 로컬 URI, 동영상은 [VideoThumbnail]이며 그 밖의 종류와 원격 항목(data 없음)은 null이다.
     * 종류에서 생성 시 한 번 정하므로 썸네일 여부와 불러오는 방법이 [fileType]과 어긋날 수 없다.
     */
    @IgnoredOnParcel
    val thumbnail: Any? = data?.let { uri ->
        when (fileType) {
            FileType.Image -> uri.toUri()
            FileType.Video -> VideoThumbnail(path, lastModifiedMillis)
            else -> null
        }
    }

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

        /** domain 모듈의 FileHolderItem을 앱 레이어 모델로 변환하며 크기·날짜를 표시용 문자열로 포맷한다. 포맷터 재사용을 위해 [fromList]로만 호출한다. */
        private fun from(
            domain: DomainFileHolderItem,
            isLocalFile: Boolean,
            dateFormat: DateFormat
        ) = FileHolderItem(
            path = domain.path,
            isDirectory = domain.isDirectory,
            name = domain.name,
            size = domain.sizeBytes?.let(::readableFileSize_td) ?: "",
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
