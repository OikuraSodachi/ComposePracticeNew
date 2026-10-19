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
}
