package com.todokanai.composepracticenew

import coil.size.Size
import com.todokanai.composepracticenew.ui.thumbnail.Thumbnail
import com.todokanai.composepracticenew.ui.thumbnail.videoThumbnailKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** [videoThumbnailKey]가 이미지 파일 키와 겹치지 않고, 파일 변경·요청 크기를 구별하는지 검증하는 단위 테스트. */
class VideoThumbnailKeyTest {

    private val video = Thumbnail.Video("/d/clip.mp4", 42L)

    @Test
    fun `키는 video 접두사와 경로·수정 시각·요청 크기로 이뤄진다`() {
        assertEquals("video:/d/clip.mp4:42:120x120", videoThumbnailKey(video, Size(120, 120)))
    }

    @Test
    fun `요청 크기가 다르면 키가 다르다`() {
        assertNotEquals(videoThumbnailKey(video, Size(120, 120)), videoThumbnailKey(video, Size(240, 240)))
        assertNotEquals(videoThumbnailKey(video, Size(120, 120)), videoThumbnailKey(video, Size.ORIGINAL))
    }

    @Test
    fun `수정 시각이 바뀌면 키가 다르다`() {
        assertNotEquals(videoThumbnailKey(video, Size(120, 120)), videoThumbnailKey(video.copy(lastModifiedMillis = 43L), Size(120, 120)))
    }
}
