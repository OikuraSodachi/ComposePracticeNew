package com.todokanai.fileexplorer

/**
 * Domain entity representing a single file or directory entry on the local filesystem.
 *
 * @param name Display name of the file or directory.
 * @param path Absolute path on the local filesystem.
 * @param isDirectory True if this entry is a directory; false if it is a file.
 * @param size File size in bytes; 0 for directories.
 * @param lastModified Last modification timestamp in milliseconds since epoch.
 */
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
)
