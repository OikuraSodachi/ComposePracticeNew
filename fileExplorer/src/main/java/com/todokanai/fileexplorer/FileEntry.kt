package com.todokanai.fileexplorer

/**
 * Domain entity representing a single file or directory entry on the local filesystem.
 *
 * @param name Display name of the file or directory.
 * @param path Absolute path on the local filesystem.
 * @param isDirectory True if this entry is a directory; false if it is a file.
 * @param size File size in bytes; null for directories (a directory has no size of its own) and for files whose size the storage did not report.
 * @param lastModified Last modification timestamp in milliseconds since epoch.
 */
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long?,
    val lastModified: Long,
)
