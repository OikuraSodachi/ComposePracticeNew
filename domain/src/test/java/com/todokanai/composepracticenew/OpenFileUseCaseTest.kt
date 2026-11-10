package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.FileLaunchResult
import com.todokanai.composepracticenew.repository.FileLauncher
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** [OpenFileUseCase]가 디렉터리는 이동하고, 파일은 다른 앱으로 넘기며, 열지 못하면 이유를 알리는지 검증하는 단위 테스트(#236). */
class OpenFileUseCaseTest {

    private val nav = FakeNavigator()
    private val notices = RecordingOperationNotices()
    private val dir = FileHolderItem(path = "/storage/music", isDirectory = true, name = "music", sizeBytes = null, lastModifiedMillis = 0L)
    private val file = FileHolderItem(path = "/storage/music/song.mp3", isDirectory = false, name = "song.mp3", sizeBytes = 3, lastModifiedMillis = 0L)

    @Test
    fun `디렉터리는 열지 않고 그 안으로 이동한다`() = runBlocking {
        val launcher = RecordingFileLauncher(FileLaunchResult.Launched)

        OpenFileUseCase(nav, launcher, notices).open(dir)

        assertEquals(listOf(dir.path), nav.navigated)
        assertEquals(emptyList<String>(), launcher.launched)
    }

    @Test
    fun `파일을 다른 앱으로 넘겼으면 알리지 않는다`() = runBlocking {
        val launcher = RecordingFileLauncher(FileLaunchResult.Launched)

        OpenFileUseCase(nav, launcher, notices).open(file)

        assertEquals(listOf(file.path), launcher.launched)
        assertEquals(emptyList<String>(), nav.navigated)
        assertEquals(emptyList<String>(), notices.failures)
    }

    @Test
    fun `형식을 알 수 없어 열지 못하면 그 이유를 알린다`() = runBlocking {
        OpenFileUseCase(nav, RecordingFileLauncher(FileLaunchResult.UnknownType), notices).open(file)

        assertEquals(listOf("열기 실패: 형식을 알 수 없는 파일입니다 (song.mp3)"), notices.failures)
    }

    @Test
    fun `여는 앱이 없으면 그 이유를 알린다`() = runBlocking {
        OpenFileUseCase(nav, RecordingFileLauncher(FileLaunchResult.NoHandler), notices).open(file)

        assertEquals(listOf("열기 실패: 이 파일을 열 수 있는 앱이 없습니다 (song.mp3)"), notices.failures)
    }

    /**
     * 열려고 한 경로를 기록하고 언제나 [result]로 답하는 가짜 파일 열기.
     * @param result 여는 요청에 돌려줄 결과
     */
    private class RecordingFileLauncher(private val result: FileLaunchResult) : FileLauncher {
        /** 열려고 한 경로. 받은 순서대로 쌓는다. */
        val launched = mutableListOf<String>()

        override fun launch(path: String): FileLaunchResult {
            launched += path
            return result
        }
    }
}
