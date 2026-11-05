package com.todokanai.fileexplorer

/**
 * A single file or directory entry read from a storage location (a local filesystem or a remote server such as FTP).
 *
 * @param name Display name of the file or directory.
 * @param path Absolute local path, or a URI string for a remote entry (e.g. `ftp://host/dir`).
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
