package com.todokanai.composepracticenew.datasource

import java.io.File
import java.io.OutputStream
import java.nio.file.Files

/**
 * 아카이브 항목을 추출 루트 아래에 만드는 쓰기 도우미. 형식과 무관한 규칙(위치 검증, 폴더 생성, 파일 생성, 건너뜀 집계)을 한 곳에 두어 [ArchiveExtractor] 구현체마다 같은 항목을 다르게 다루지 않도록 한다.
 * 건너뛰는 항목은 루트 밖이나 루트 자신을 가리키는 항목뿐이며, 만들지 못한 폴더·파일은 IOException으로 알린다.
 * @param root 항목의 위치를 정하는 추출 루트
 */
class ArchiveEntryWriter(private val root: ExtractionRoot) {

    /** 루트 밖이나 루트 자신을 가리켜 건너뛴 항목 수. */
    var skippedEntries = 0
        private set

    /**
     * 폴더 항목을 디렉터리로 만든다. 이미 디렉터리가 있으면 그대로 둔다.
     * @param entryName 아카이브 안의 '/' 구분 항목 경로
     * @throws java.io.IOException 디렉터리를 만들지 못했을 때(같은 이름의 파일이 있는 경우 포함)
     */
    fun createFolder(entryName: String) {
        targetOrSkip(entryName)?.let(::createDirectories)
    }

    /**
     * 파일 항목을 쓸 출력 스트림을 연다. 부모 디렉터리가 없으면 만든다. 닫는 일은 호출부가 한다.
     * @param entryName 아카이브 안의 '/' 구분 항목 경로
     * @return 항목 위치의 출력 스트림. 건너뛴 항목이면 null
     * @throws java.io.IOException 부모 디렉터리나 파일을 만들지 못했을 때
     */
    fun openFile(entryName: String): OutputStream? {
        val target = targetOrSkip(entryName) ?: return null
        target.parentFile?.let(::createDirectories)
        return target.outputStream()
    }

    /**
     * 취소로 멈춘 파일 항목을 지운다 — 쓰다 만 파일이 온전한 파일처럼 남지 않도록. 그 항목의 출력 스트림을 닫은 뒤에 호출한다.
     * @param entryName [openFile]로 연 항목의 '/' 구분 경로
     */
    fun discardFile(entryName: String) {
        root.targetOf(entryName)?.delete()
    }

    /**
     * [entryName]을 풀 위치를 정하고, 풀지 않는 항목이면 건너뜀으로 센다.
     * @param entryName 아카이브 안의 '/' 구분 항목 경로
     * @return 풀 위치. 건너뛴 항목이면 null
     */
    private fun targetOrSkip(entryName: String): File? = root.targetOf(entryName) ?: run { skippedEntries++; null }
}

/**
 * [dir]과 그 부모 디렉터리를 만든다. 만들지 못하면 false만 돌려주는 [File.mkdirs]와 달리 실패를 IOException으로 알린다.
 * @param dir 만들 디렉터리
 * @throws java.io.IOException 디렉터리를 만들지 못했을 때(같은 이름의 파일이 있는 경우 포함)
 */
internal fun createDirectories(dir: File) {
    Files.createDirectories(dir.toPath())
}
