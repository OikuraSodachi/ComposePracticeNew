package com.todokanai.composepracticenew.tools.independent

/** 이 method들은 독립적으로 사용 가능함 */

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionMethod
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.Arrays
import java.util.zip.ZipEntry
import kotlin.io.path.absolutePathString

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

fun zipFileEntrySize_td(file: java.util.zip.ZipFile): Long {
    var result = 0L
    val entries = file.entries()
    while (entries.hasMoreElements()) {
        val entry = entries.nextElement() as ZipEntry
        result += entry.size
    }
    return result
}

/** Todokanai */
fun compressFilesRecursivelyWithZip4j_td(files: Array<File>, zipFile: File) {
    val parameters = ZipParameters()
    parameters.compressionMethod = CompressionMethod.DEFLATE
    val zipFileObject = ZipFile(zipFile)
    for (file in files) {
        if (file.isFile) zipFileObject.addFile(file, parameters)
        else if (file.isDirectory) zipFileObject.addFolder(file, parameters)
    }
}

/** 진행상황 callback만 미완성 */
@Throws(IOException::class)
fun zipWithoutCallback_td(zipFile: ZipFile, files: Array<File>) {
    files.forEach { file ->
        if (file.isDirectory) zipFile.addFolder(file)
        else zipFile.addFile(file)
    }
}

/** 진행상황 callback만 미완성 — unzipHere에 해당 */
@Throws(IOException::class)
fun unzipWithoutCallback_td(zipFile: ZipFile, target: Path) {
    val folder = File("${target.absolutePathString()}/${zipFile.file.nameWithoutExtension}")
    if (!folder.exists()) folder.mkdir()
    zipFile.extractAll(folder.toString())
}

/** Todokanai */
@Throws(IOException::class)
fun unzipHereWithoutCallback_td(zipFile: ZipFile, target: Path) {
    zipFile.extractAll(target.toString())
}

/** Todokanai */
@Throws(IOException::class)
fun zip4j_td() {
    ZipFile("filename.zip").addFile("file.txt")
    val files = Arrays.asList(File("file1.txt"), File("file2.txt"))
    ZipFile("filename.zip").addFiles(files)
    ZipFile("filename.zip").addFolder(File("/home/mkyong/folder"))
}
