package com.todokanai.composepracticenew.data

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.fileexplorer.FileEntry
import javax.inject.Inject
import javax.inject.Singleton

/** Converts raw FileEntry lists into domain FileHolderItem models (raw values only, no display formatting) and sorts them by SortMode. */
@Singleton
class DataConverter @Inject constructor() {

    private fun FileEntry.toFileHolderItem(): FileHolderItem =
        FileHolderItem(
            path = path,
            isDirectory = isDirectory,
            name = name,
            sizeBytes = size,
            lastModifiedMillis = lastModified
        )

    fun fileHolderItemList(files: List<FileEntry>, sortBy: SortMode): List<FileHolderItem> {
        return files.map { it.toFileHolderItem() }.sortedWith(sortBy.comparator)
    }

    /**
     * FileEntry 목록을 정렬하지 않고 같은 순서의 domain FileHolderItem 목록으로 바꾼다. 찾은 순서를 지켜야 하는 검색 결과에 쓴다.
     * @param files 바꿀 FileEntry 목록
     * @return 같은 순서의 FileHolderItem 목록
     */
    fun fileHolderItemsInOrder(files: List<FileEntry>): List<FileHolderItem> = files.map { it.toFileHolderItem() }
}
