package com.todokanai.composepracticenew.ui.thumbnail

import coil.ComponentRegistry
import coil.map.Mapper
import coil.request.Options
import java.io.File

/** 파일 목록 행에 표시할 썸네일의 Coil 요청 모델. 종류는 이 타입으로 닫혀 있고, 각 종류의 처리기는 [addThumbnailComponents]가 함께 등록한다. */
sealed interface Thumbnail {

    /**
     * 로컬 이미지 파일의 썸네일. [ImageThumbnailMapper]가 파일로 바꿔 Coil 기본 파일 처리로 불러온다.
     * @param path 이미지 파일 절대 경로
     */
    data class Image(val path: String) : Thumbnail

    /**
     * 로컬 동영상 파일의 썸네일. [VideoThumbnailFetcher]가 만든다.
     * @param path 동영상 파일 절대 경로
     * @param lastModifiedMillis 파일 마지막 수정 시각 — 캐시 키에 넣어 파일이 바뀌면 썸네일을 새로 만든다
     */
    data class Video(val path: String, val lastModifiedMillis: Long) : Thumbnail
}

/** [Thumbnail.Image]를 Coil이 기본으로 불러오는 [File]로 바꾸는 Mapper. 파일 캐시 키(경로·수정 시각)도 Coil 기본 규칙을 따른다. */
class ImageThumbnailMapper : Mapper<Thumbnail.Image, File> {
    override fun map(data: Thumbnail.Image, options: Options): File = File(data.path)
}

/**
 * [Thumbnail] 각 종류의 처리기를 Coil에 등록한다. [Thumbnail]에 종류를 추가하면 처리기도 여기에 함께 넣는다.
 * @return 처리기를 등록한 같은 빌더
 */
fun ComponentRegistry.Builder.addThumbnailComponents(): ComponentRegistry.Builder =
    add(ImageThumbnailMapper())
        .add(VideoThumbnailFetcher.Factory())
        .add(VideoThumbnailKeyer())
