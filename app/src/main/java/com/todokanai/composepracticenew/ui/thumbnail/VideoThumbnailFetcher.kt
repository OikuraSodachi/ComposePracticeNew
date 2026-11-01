package com.todokanai.composepracticenew.ui.thumbnail

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.CancellationSignal
import android.util.Size
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.disk.DiskCache
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import coil.size.Dimension
import coil.size.pxOrElse
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import coil.size.Size as CoilSize

/**
 * [Thumbnail.Video]를 플랫폼 썸네일 함수 [ThumbnailUtils.createVideoThumbnail]로 만드는 Coil Fetcher. 영상에 내장된 표지가 있으면 그것을, 없으면 영상 프레임을 쓴다.
 * 만든 썸네일은 Coil 디스크 캐시에 WebP로 저장해 앱을 다시 켜도 다시 만들지 않는다. 만들지 못하면 예외가 그대로 Coil 오류 결과가 되어 종류 아이콘이 남는다.
 * @param data 썸네일을 만들 동영상
 * @param options 요청 크기·캐시 정책·Context를 담은 Coil 요청 옵션
 * @param diskCache 썸네일을 저장할 Coil 디스크 캐시. null이면 저장하지 않는다
 * @param generation 동시 생성 수와 실패 기억을 모든 요청이 공유하는 생성 조율기
 */
class VideoThumbnailFetcher(
    private val data: Thumbnail.Video,
    private val options: Options,
    private val diskCache: Lazy<DiskCache?>,
    private val generation: VideoThumbnailGeneration
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val key = videoThumbnailKey(data, options.size)
        readFromDiskCache(key)?.let { return it }
        val bitmap = generation.generate(data, targetSize())
        writeToDiskCache(key, bitmap)
        return DrawableResult(
            drawable = bitmap.toDrawable(options.context.resources),
            isSampled = isBounded(),
            dataSource = DataSource.DISK
        )
    }

    /** Coil 요청 크기를 플랫폼 함수에 넘길 크기로 바꾼다. 크기가 정해지지 않은 축은 원본 크기를 요청한 것이므로 제한하지 않는다. */
    private fun targetSize(): Size =
        Size(options.size.width.pxOrElse { Int.MAX_VALUE }, options.size.height.pxOrElse { Int.MAX_VALUE })

    /**
     * 요청 크기로 썸네일을 제한했는지 판별한다. 제한했으면 원본보다 작을 수 있으므로 축소본으로 보고한다.
     * 캐시 키에 요청 크기가 들어가 같은 크기 요청끼리만 결과를 공유하므로, 축소본 보고가 다른 크기 요청에 쓰이지 않는다.
     * @return 어느 한 축이라도 픽셀 크기로 정해졌으면 true
     */
    private fun isBounded(): Boolean = options.size.width is Dimension.Pixels || options.size.height is Dimension.Pixels

    /**
     * 디스크 캐시에 저장된 썸네일을 읽는다.
     * @param key [videoThumbnailKey]로 만든 캐시 키
     * @return 저장된 썸네일. 읽기가 꺼져 있거나 저장된 것이 없으면 null
     */
    private fun readFromDiskCache(key: String): SourceResult? {
        if (!options.diskCachePolicy.readEnabled) return null
        val cache = diskCache.value ?: return null
        val snapshot = cache[key] ?: return null
        return SourceResult(
            source = ImageSource(snapshot.data, cache.fileSystem, key, snapshot),
            mimeType = STORED_MIME_TYPE,
            dataSource = DataSource.DISK
        )
    }

    /**
     * [bitmap]을 디스크 캐시에 WebP로 저장한다. 저장에 실패하면 기록을 버리고 넘어간다 — 썸네일은 이미 만들어져 표시할 수 있으므로 캐시 실패로 표시를 막지 않는다(다음 실행에 다시 만든다).
     * @param key [videoThumbnailKey]로 만든 캐시 키
     * @param bitmap 저장할 썸네일
     */
    private fun writeToDiskCache(key: String, bitmap: Bitmap) {
        if (!options.diskCachePolicy.writeEnabled) return
        val cache = diskCache.value ?: return
        val editor = cache.edit(key) ?: return
        try {
            val written = cache.fileSystem.write(editor.data) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, outputStream())
            }
            if (written) editor.commit() else editor.abort()
        } catch (e: IOException) {
            editor.abort()
        }
    }

    /**
     * [Thumbnail.Video] 요청마다 [VideoThumbnailFetcher]를 만드는 팩토리.
     * 생성 조율기는 팩토리가 하나만 갖고 모든 요청이 공유한다 — ImageLoader당 팩토리가 하나이므로 앱 실행 동안 유지된다.
     */
    class Factory : Fetcher.Factory<Thumbnail.Video> {

        /** 모든 동영상 썸네일 요청이 공유하는 생성 조율기. */
        private val generation = VideoThumbnailGeneration()

        override fun create(data: Thumbnail.Video, options: Options, imageLoader: ImageLoader): Fetcher =
            VideoThumbnailFetcher(data, options, lazy { imageLoader.diskCache }, generation)
    }

    private companion object {
        /** 디스크 캐시에 저장하는 썸네일 형식. */
        const val STORED_MIME_TYPE = "image/webp"

        /** 디스크 캐시에 저장할 때의 WebP 품질(0~100). */
        const val WEBP_QUALITY = 80
    }
}

/**
 * 동영상 썸네일 생성을 모든 요청이 함께 조율한다. 동시 생성 수를 [MAX_PARALLELISM]으로 묶고, 만들지 못한 파일을 앱 실행 동안 기억해 다시 시도하지 않는다.
 * 플랫폼 함수는 영구 실패(손상 파일)와 일시 실패를 구별하지 않으므로 일시 실패도 기억된다 — 파일이 바뀌거나(수정 시각) 앱을 다시 켜면 다시 시도한다.
 */
class VideoThumbnailGeneration {

    /** 동시에 실행하는 생성 수를 묶는 세마포어. 기다리는 동안 요청이 취소되면 생성을 시작하지 않는다. */
    private val permits = Semaphore(MAX_PARALLELISM)

    /** 만들지 못한 동영상의 키(경로·수정 시각) 집합. 요청 크기와 무관하게 실패하므로 크기는 넣지 않는다. */
    private val failures: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * [video]의 썸네일을 [size] 안에 맞춰 만든다. 요청이 취소되면 진행 중인 생성도 멈추며, 취소는 실패로 기억하지 않는다.
     * @param video 썸네일을 만들 동영상
     * @param size 썸네일 최대 크기
     * @return 만든 썸네일
     * @throws IOException 만들지 못했거나, 앱 실행 중 이전에 만들지 못한 파일일 때
     */
    suspend fun generate(video: Thumbnail.Video, size: Size): Bitmap {
        val failureKey = "${video.path}:${video.lastModifiedMillis}"
        if (failureKey in failures) throw IOException("이전에 썸네일을 만들지 못한 동영상: ${video.path}")
        return permits.withPermit {
            try {
                createCancellable(File(video.path), size)
            } catch (e: IOException) {
                // 취소 신호로 끝난 경우도 IOException이므로, 취소면 실패로 기억하지 않고 취소를 그대로 전파한다
                currentCoroutineContext().ensureActive()
                failures += failureKey
                throw e
            }
        }
    }

    /** 코루틴이 취소되면 플랫폼 함수에 넘긴 [CancellationSignal]도 취소해 진행 중인 생성을 멈추게 한다. */
    private suspend fun createCancellable(file: File, size: Size): Bitmap = coroutineScope {
        val signal = CancellationSignal()
        // UNDISPATCHED — 생성을 시작하기 전에 감시를 걸어, 감시 코루틴이 시작되기 전에 온 취소도 놓치지 않는다
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                signal.cancel()
            }
        }
        try {
            ThumbnailUtils.createVideoThumbnail(file, size, signal)
        } finally {
            watcher.cancel()
        }
    }

    private companion object {
        /** 동시에 실행하는 동영상 썸네일 생성 수. Coil이 이미지 디코딩을 묶는 수와 같게 둔다. */
        const val MAX_PARALLELISM = 4
    }
}

/** [Thumbnail.Video]의 메모리 캐시 키를 만드는 Keyer. 디스크 캐시와 같은 키를 쓴다. 키가 없으면 Coil이 메모리 캐시를 쓰지 않아 행이 다시 보일 때마다 새로 불러온다. */
class VideoThumbnailKeyer : Keyer<Thumbnail.Video> {
    override fun key(data: Thumbnail.Video, options: Options): String = videoThumbnailKey(data, options.size)
}

/**
 * 동영상 썸네일의 캐시 키를 만든다. `video:` 접두사로 이미지 파일 키(`경로:수정시각`)와 겹치지 않게 하고, 요청 크기를 넣어 작은 썸네일이 큰 요청에 쓰이지 않게 한다.
 * @param data 동영상
 * @param size Coil 요청 크기
 * @return `video:경로:수정시각:가로x세로` 형태의 키
 */
internal fun videoThumbnailKey(data: Thumbnail.Video, size: CoilSize): String =
    "video:${data.path}:${data.lastModifiedMillis}:${size.width}x${size.height}"
