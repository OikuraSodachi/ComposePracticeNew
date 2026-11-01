package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.thumbnail.VideoThumbnail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [FileHolderItem.thumbnail]이 종류와 로컬 여부로 썸네일 요청 모델을 정하는지 검증하는 단위 테스트. 이미지의 URI 변환은 Android 프레임워크가 필요해 여기서 다루지 않는다. */
class FileHolderItemThumbnailTest {

    @Test
    fun `로컬 동영상은 경로와 수정 시각으로 동영상 썸네일을 요청한다`() {
        assertEquals(VideoThumbnail("/d/clip.mp4", 42L), item("/d/clip.mp4", data = "file:///d/clip.mp4").thumbnail)
    }

    @Test
    fun `원격 동영상은 썸네일이 없다`() {
        assertNull(item("/d/clip.mp4", data = null).thumbnail)
    }

    @Test
    fun `이미지와 동영상이 아닌 종류는 썸네일이 없다`() {
        assertNull(item("/d/notes.txt", data = "file:///d/notes.txt").thumbnail)
        assertNull(item("/d/clip.mp4", data = "file:///d/clip.mp4", isDirectory = true).thumbnail)
    }

    /** [path]의 마지막 경로 조각을 이름으로 갖고 수정 시각이 42인 테스트 항목을 만든다. */
    private fun item(path: String, data: String?, isDirectory: Boolean = false) = FileHolderItem(
        path = path,
        isDirectory = isDirectory,
        name = path.substringAfterLast('/'),
        size = "",
        lastModified = "",
        sizeBytes = null,
        lastModifiedMillis = 42L,
        data = data
    )
}
