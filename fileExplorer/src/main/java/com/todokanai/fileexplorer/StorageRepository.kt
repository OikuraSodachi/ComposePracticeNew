package com.todokanai.fileexplorer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/** Provides read access to a storage location (local or remote) for directory navigation. */
abstract class StorageRepository {

    private val _currentPath = MutableStateFlow<String?>(null)
    /** The currently active directory path; null when no directory is selected. */
    val currentPath: StateFlow<String?> = _currentPath.asStateFlow()

    /** Ancestor path entries from filesystem root down to [currentPath]; updates when [currentPath] changes. */
    val dirTree: Flow<List<FileEntry>> = currentPath
        .flatMapLatest { path -> flow { emit(path?.let { buildAncestors(it) } ?: emptyList()) } }

    private fun buildAncestors(path: String): List<FileEntry> {
        val result = mutableListOf<FileEntry>()
        var current: String? = path
        while (current != null) {
            result.add(0, FileEntry(
                name = current.substringAfterLast('/').ifEmpty { current!! },
                path = current,
                isDirectory = true,
                size = 0L,
                lastModified = 0L,
            ))
            current = getParent(current)
        }
        return result
    }

    /** Updates [currentPath] to [path]; pass null to return to the storage-root selection view. */
    open fun navigateTo(path: String?) { _currentPath.value = path }

    /** Navigates to the parent of [currentPath], or to null if already at a root. */
    open fun toParent() { navigateTo(currentPath.value?.let { getParent(it) }) }

    /**
     * Returns files and subdirectories located at [path].
     * Returns an empty list if [path] does not exist, is not a directory, or is inaccessible.
     */
    abstract suspend fun listFiles(path: String): List<FileEntry>

    /**
     * Returns the parent path of [path], or null if [path] is already a root (has no parent).
     *
     * Default implementation splits on '/'. Override for storage types that use a different separator.
     */
    open fun getParent(path: String): String? =
        path.substringBeforeLast('/').takeIf { it.isNotEmpty() && it != path }
}
