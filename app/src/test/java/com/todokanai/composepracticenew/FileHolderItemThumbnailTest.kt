package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.thumbnail.Thumbnail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [FileHolderItem.thumbnail]이 종류와 로컬 여부로 썸네일 요청 모델을 정하는지 검증하는 단위 테스트. */
class FileHolderItemThumbnailTest {

    @Test
    fun `로컬 이미지는 경로로 이미지 썸네일을 요청한다`() {
        assertEquals(Thumbnail.Image("/d/photo.jpg"), item("/d/photo.jpg", isLocal = true).thumbnail)
    }

    @Test
    fun `로컬 동영상은 경로와 수정 시각으로 동영상 썸네일을 요청한다`() {
        assertEquals(Thumbnail.Video("/d/clip.mp4", 42L), item("/d/clip.mp4", isLocal = true).thumbnail)
    }

    @Test
    fun `원격 이미지와 동영상은 썸네일이 없다`() {
        assertNull(item("/d/photo.jpg", isLocal = false).thumbnail)
        assertNull(item("/d/clip.mp4", isLocal = false).thumbnail)
    }

    @Test
    fun `이미지와 동영상이 아닌 종류는 썸네일이 없다`() {
        assertNull(item("/d/notes.txt", isLocal = true).thumbnail)
        assertNull(item("/d/clip.mp4", isLocal = true, isDirectory = true).thumbnail)
    }

    /** [path]의 마지막 경로 조각을 이름으로 갖고 수정 시각이 42인 테스트 항목을 만든다. */
    private fun item(path: String, isLocal: Boolean, isDirectory: Boolean = false) = FileHolderItem(
        path = path,
        isDirectory = isDirectory,
        name = path.substringAfterLast('/'),
        size = "",
        lastModified = "",
        sizeBytes = null,
        lastModifiedMillis = 42L,
        isLocal = isLocal
    )
}
