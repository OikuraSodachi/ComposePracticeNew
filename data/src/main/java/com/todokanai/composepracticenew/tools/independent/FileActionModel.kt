@file:JvmName("LocalFileActionModelKt")

package com.todokanai.composepracticenew.tools.independent

/** 이 method들은 독립적으로 사용 가능함 */

import java.io.File

/** Function to get the character sequence from after the last instance of File.separatorChar in a path. */
fun getFilenameForPath_td(path: String): String =
    if (!path.contains(File.separatorChar)) path
    else path.subSequence(
        path.lastIndexOf(File.separatorChar) + 1,
        path.length
    ).toString()

/** 안드로이드에서만 적용 가능 */
fun getPhysicalStorage_td(file: File): String {
    val path = file.canonicalFile.absolutePath
    val firstSlashIndex = path.indexOf('/')
    val secondSlashIndex = path.indexOf('/', startIndex = firstSlashIndex + 1)
    val thirdSlashIndex = path.indexOf('/', startIndex = secondSlashIndex + 1)
    return if (thirdSlashIndex > secondSlashIndex) {
        path.substring(secondSlashIndex + 1, thirdSlashIndex)
    } else {
        path.substring(secondSlashIndex + 1)
    }
}

/** files:Array<File> 과 하위 경로의 파일의 크기 합 */
fun getTotalSize_td(files: Array<File>): Long {
    var totalSize: Long = 0
    for (file in files) {
        if (file.isDirectory) {
            totalSize += getTotalSize_td(file.listFiles() ?: emptyArray())
        } else {
            totalSize += file.length()
        }
    }
    return totalSize
}

/** Todokanai */
fun dirTree_td(currentPath: File): List<File> {
    val result = mutableListOf<File>()
    var now = currentPath
    while (now.parent != null) {
        result.add(now)
        now = now.parentFile
    }
    return result.reversed()
}
