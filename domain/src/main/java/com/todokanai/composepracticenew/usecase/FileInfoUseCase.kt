package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ItemAttributes
import com.todokanai.composepracticenew.model.SizeMeasurement
import com.todokanai.composepracticenew.repository.FileInfoRepository

/**
 * 선택 항목의 정보(속성, 하위 항목을 포함한 크기, 폴더의 하위 항목 수)를 구한다.
 * 목록 조회 결과의 값(크기·종류·수정 시각)은 쓰지 않고 저장소에서 다시 읽는다 — 목록 조회 뒤 바뀐 항목의 옛 값이 지금 값으로 보이지 않도록.
 * @param repo 저장소에서 속성·크기·하위 항목 수를 읽는 FileInfoRepository 구현체
 */
class FileInfoUseCase(private val repo: FileInfoRepository) {

    /**
     * [item]이 들어 있는 폴더의 경로를 저장소의 경로 규칙으로 구한다.
     * @param item 위치를 구할 항목
     * @return 상위 폴더 경로. 경로에 상위가 없으면 null
     */
    fun locationOf(item: FileHolderItem): String? = repo.locationOf(item.path)

    /**
     * [item]의 속성(종류, 수정 시각)을 저장소에서 읽는다.
     * @param item 속성을 읽을 항목
     * @return 지금 읽은 항목 속성, 또는 항목을 읽지 못한 실패
     */
    suspend fun readAttributes(item: FileHolderItem): IoResult<ItemAttributes> = repo.readAttributes(item.path)

    /**
     * [items]의 크기 합을 저장소에서 측정한다.
     * 선택 항목마다의 크기를 더한 값이다 — 선택이 서로 겹치는 내용을 가리키면(폴더와 그 폴더를 가리키는 링크) 겹친 만큼 두 번 더한다.
     * @param items 크기를 구할 항목 목록
     * @return 크기 합. 읽지 못한 항목이 있으면 [SizeMeasurement.LowerBound]
     */
    suspend fun measureSize(items: List<FileHolderItem>): IoResult<SizeMeasurement> = repo.measureSize(items.map { it.path })

    /**
     * 폴더 [directory] 바로 아래의 항목 수를 센다.
     * @param directory 하위 항목 수를 셀 폴더
     * @return 바로 아래 항목 수, 또는 폴더를 읽지 못한 실패
     */
    suspend fun countChildren(directory: FileHolderItem): IoResult<Int> = repo.countChildren(directory.path)
}
