package com.todokanai.composepracticenew.repository

import android.util.Log
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.onSuccess
import com.todokanai.fileexplorer.FileEntry

/**
 * 경로를 처리하는 파일 시스템을 골라 그 경로의 목록 조회를 맡기는 라우터.
 * 주입된 파일 시스템 집합에만 의존하므로, 탐색 상태에 조회 방법으로 넘겨도 보유한 저장소의 생성 중 상태를 읽지 않는다.
 * @param fileSystems 경로 종류별 처리를 담당하는 PathAwareFileSystem 집합 — 각 handles()는 서로 배타적이다
 */
internal class FileSystemRouter(private val fileSystems: Set<PathAwareFileSystem>) {

    private companion object {
        const val TAG = "FileExplorerRepo"
        /** 경로를 처리할 파일 시스템이 없어 목록을 조회하지 못했을 때의 실패 메시지. */
        const val UNSUPPORTED_PATH_MESSAGE = "지원하지 않는 경로입니다"
    }

    /**
     * [path]를 처리하는 파일 시스템을 반환한다. 처리자가 없는 경로는 크래시 대신 경고 로그를 남기고 null을 반환해
     * 호출부가 "목록 조회 실패·부모 없음·이동 무시"로 다루게 한다.
     * @param path 처리할 경로
     * @return 경로를 처리하는 파일 시스템. 없으면 null
     */
    fun resolve(path: String): PathAwareFileSystem? =
        fileSystems.firstOrNull { it.handles(path) }
            ?: null.also { Log.w(TAG, "No file system handles path: $path") }

    /**
     * [path]의 파일과 하위 디렉터리 목록을 경로를 처리하는 파일 시스템에서 조회한다.
     * @param path 조회할 디렉터리 경로
     * @return 항목 목록. 처리할 파일 시스템이 없거나 읽을 수 없으면 실패 결과
     */
    suspend fun listFiles(path: String): IoResult<List<FileEntry>> {
        Log.d(TAG, "fileList 갱신 — path=$path")
        val fileSystem = resolve(path) ?: return IoResult.ItemFailed(UNSUPPORTED_PATH_MESSAGE)
        return fileSystem.listFiles(path).onSuccess {
            Log.d(TAG, "fileList 결과 — path=$path size=${it.size}")
        }
    }
}
