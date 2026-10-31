package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.SizeMeasurement
import com.todokanai.composepracticenew.model.map
import com.todokanai.composepracticenew.repository.FileInfoRepository

/**
 * 선택 항목의 정보(하위 항목을 포함한 크기, 폴더의 하위 항목 수)를 구한다.
 * @param repo 저장소에서 크기·하위 항목 수를 읽는 FileInfoRepository 구현체
 */
class FileInfoUseCase(private val repo: FileInfoRepository) {

    /**
     * [items]의 크기 합을 구한다. 목록에 크기가 있는 파일은 그 값을 쓰고, 크기가 없는 항목(폴더·크기 미확인 파일)만 저장소에서 측정한다.
     * 선택 항목마다의 크기를 더한 값이다 — 선택이 서로 겹치는 내용을 가리키면(폴더와 그 폴더를 가리키는 링크) 겹친 만큼 두 번 더한다.
     * @param items 크기를 구할 항목 목록
     * @return 크기 합. 측정 중 읽지 못한 하위 항목이 있으면 [SizeMeasurement.LowerBound]
     */
    suspend fun measureSize(items: List<FileHolderItem>): IoResult<SizeMeasurement> {
        val listedBytes = listedBytesOf(items)
        val unmeasured = unmeasuredPathsOf(items)
        if (unmeasured.isEmpty()) return IoResult.Success(SizeMeasurement.Complete(listedBytes))
        return repo.measureSize(unmeasured).map { it.withExtraBytes(listedBytes) }
    }

    /**
     * 폴더 [directory] 바로 아래의 항목 수를 센다.
     * @param directory 하위 항목 수를 셀 폴더
     * @return 바로 아래 항목 수, 또는 폴더를 읽지 못한 실패
     */
    suspend fun countChildren(directory: FileHolderItem): IoResult<Int> = repo.countChildren(directory.path)

    /**
     * 목록에 크기가 있는 항목의 크기 합을 구한다.
     * @param items 선택 항목 목록
     * @return 크기가 있는 항목의 크기 합(bytes)
     */
    private fun listedBytesOf(items: List<FileHolderItem>): Long = items.sumOf { it.sizeBytes ?: 0L }

    /**
     * 목록에 크기가 없어 저장소에서 측정해야 하는 항목을 고른다.
     * @param items 선택 항목 목록
     * @return 크기가 없는 항목(폴더·크기 미확인 파일)의 경로 목록
     */
    private fun unmeasuredPathsOf(items: List<FileHolderItem>): List<String> = items.filter { it.sizeBytes == null }.map { it.path }
}
