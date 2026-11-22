package com.todokanai.composepracticenew.repository

import android.util.Log
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.onSuccess

private const val TAG = "FileExplorerRepo"

/**
 * 경로를 처리하는 파일 시스템을 한 역할([T])의 집합에서 고르는 라우터.
 * 주입된 파일 시스템 집합에만 의존하므로, 탐색 상태에 조회 방법으로 넘겨도 보유한 저장소의 생성 중 상태를 읽지 않는다.
 * @param fileSystems 경로 종류별 처리를 담당하는 한 역할의 파일 시스템 집합 — 각 handles()는 서로 배타적이다
 */
internal class FileSystemRouter<T : PathHandlingFileSystem>(private val fileSystems: Set<T>) {

    /**
     * [path]를 처리하는 파일 시스템을 반환한다. 화면마다 그 화면이 다루는 경로의 파일 시스템 집합을 주입하므로, 처리자가 없는 경로는 배선 오류다 —
     * "읽을 수 없음·부모 없음"으로 덮으면 배선 오류가 틀린 원인으로 보이므로 실패시킨다.
     * @param path 처리할 경로
     * @return 경로를 처리하는 파일 시스템
     * @throws IllegalStateException 처리자가 없을 때(배선 오류)
     */
    fun resolve(path: String): T =
        checkNotNull(find(path)) { "No file system handles path: $path" }

    /**
     * [path]를 처리하는 파일 시스템을 로그 없이 찾는다. 처리자가 없다는 사실을 결과로 알리는 호출부가 쓴다 — 입력마다 불리는 판정이 경고 로그를 쌓지 않도록.
     * @param path 처리할 경로
     * @return 경로를 처리하는 파일 시스템. 없으면 null
     */
    fun find(path: String): T? =
        fileSystems.firstOrNull { it.handles(path) }
}

/**
 * [path]의 파일과 하위 디렉터리 목록을 경로를 처리하는 파일 시스템에서 조회한다.
 * @param path 조회할 디렉터리 경로
 * @return 항목 목록. 읽을 수 없으면 실패 결과
 * @throws IllegalStateException 처리할 파일 시스템이 없을 때(배선 오류)
 */
internal suspend fun FileSystemRouter<out NavigableFileSystem>.listFiles(path: String): IoResult<ListedDirectory> {
    Log.d(TAG, "fileList 갱신 — path=$path")
    return resolve(path).listFiles(path).onSuccess {
        Log.d(TAG, "fileList 결과 — path=$path size=${it.entries.size} unreadable=${it.unreadableCount}")
    }
}
