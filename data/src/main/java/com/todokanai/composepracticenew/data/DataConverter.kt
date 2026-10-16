package com.todokanai.composepracticenew.data

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.myobjects.Constants.BY_DATE_ASCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_DATE_DESCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_DEFAULT
import com.todokanai.composepracticenew.myobjects.Constants.BY_NAME_ASCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_NAME_DESCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_SIZE_ASCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_SIZE_DESCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_TYPE_ASCENDING
import com.todokanai.composepracticenew.myobjects.Constants.BY_TYPE_DESCENDING
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td
import com.todokanai.fileexplorer.FileEntry
import java.text.DateFormat
import javax.inject.Inject
import javax.inject.Singleton

/** Converts raw FileEntry lists into UI display models. */
@Singleton
class DataConverter @Inject constructor() {

    private fun FileEntry.toFileHolderItem(): FileHolderItem {
        val lastModified = DateFormat.getDateTimeInstance().format(this.lastModified)
        val size = if (this.isDirectory) "" else readableFileSize_td(this.size)
        return FileHolderItem(this.path, this.isDirectory, this.name, size, lastModified, this.size)
    }

    fun fileHolderItemList(files: List<FileEntry>, sortBy: String): List<FileHolderItem> {
        return sortFiles(sortBy, files).map { it.toFileHolderItem() }
    }

    private val sortFunctions: Map<String, (List<FileEntry>) -> List<FileEntry>> = mapOf(
        BY_DEFAULT         to { files -> files.sortedWith(compareBy({ !it.isDirectory }, { it.name })) },
        BY_NAME_ASCENDING  to { files -> files.sortedBy { it.name } },
        BY_NAME_DESCENDING to { files -> files.sortedByDescending { it.name } },
        BY_SIZE_ASCENDING  to { files -> files.sortedBy { it.size } },
        BY_SIZE_DESCENDING to { files -> files.sortedByDescending { it.size } },
        BY_TYPE_ASCENDING  to { files -> files.sortedBy { it.name.substringAfterLast('.', "") } },
        BY_TYPE_DESCENDING to { files -> files.sortedByDescending { it.name.substringAfterLast('.', "") } },
        BY_DATE_ASCENDING  to { files -> files.sortedBy { it.lastModified } },
        BY_DATE_DESCENDING to { files -> files.sortedByDescending { it.lastModified } }
    )

    private fun sortFiles(sortMode: String, files: List<FileEntry>): List<FileEntry> =
        sortFunctions[sortMode]?.invoke(files) ?: files
}
