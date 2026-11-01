package com.todokanai.composepracticenew.ui.thumbnail

import android.media.ThumbnailUtils
import android.util.Size
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import coil.size.pxOrElse
import java.io.File

/**
 * 동영상 파일의 썸네일을 Coil로 불러오는 요청 모델. 이 타입은 [VideoThumbnailFetcher]만 처리하므로, 동영상인지는 MIME 추정이 아니라 이 모델을 만든 쪽(FileType)이 정한다.
 * @param path 동영상 파일 절대 경로
 * @param lastModifiedMillis 파일 마지막 수정 시각 — 캐시 키에 넣어 파일이 바뀌면 썸네일을 새로 만든다
 */
data class VideoThumbnail(val path: String, val lastModifiedMillis: Long)

/**
 * [VideoThumbnail]을 플랫폼 썸네일 함수 [ThumbnailUtils.createVideoThumbnail]로 만드는 Coil Fetcher. 영상에 내장된 표지가 있으면 그것을, 없으면 영상 프레임을 쓴다.
 * 만들지 못하면 플랫폼 함수의 예외가 그대로 Coil 오류 결과가 되어 종류 아이콘이 남는다.
 * 이미 시작한 생성은 요청이 취소되어도 끝까지 진행된다 — 시작 전 취소는 Coil이 처리한다.
 * @param data 썸네일을 만들 동영상
 * @param options 요청 크기와 Context를 담은 Coil 요청 옵션
 */
class VideoThumbnailFetcher(
    private val data: VideoThumbnail,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bitmap = ThumbnailUtils.createVideoThumbnail(File(data.path), targetSize(), null)
        return DrawableResult(
            drawable = bitmap.toDrawable(options.context.resources),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    /** Coil 요청 크기를 플랫폼 함수에 넘길 크기로 바꾼다. 크기가 정해지지 않은 축은 원본 크기를 요청한 것이므로 제한하지 않는다. */
    private fun targetSize(): Size =
        Size(options.size.width.pxOrElse { Int.MAX_VALUE }, options.size.height.pxOrElse { Int.MAX_VALUE })

    /** [VideoThumbnail] 요청마다 [VideoThumbnailFetcher]를 만드는 팩토리. */
    class Factory : Fetcher.Factory<VideoThumbnail> {
        override fun create(data: VideoThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            VideoThumbnailFetcher(data, options)
    }
}

/** [VideoThumbnail]의 메모리 캐시 키를 경로와 수정 시각으로 만드는 Keyer. 키가 없으면 Coil이 메모리 캐시를 쓰지 않아 행이 다시 보일 때마다 썸네일을 새로 만든다. */
class VideoThumbnailKeyer : Keyer<VideoThumbnail> {
    override fun key(data: VideoThumbnail, options: Options): String = "${data.path}:${data.lastModifiedMillis}"
}
