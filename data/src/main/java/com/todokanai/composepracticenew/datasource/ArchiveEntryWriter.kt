package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.WriteConflict
import java.io.File
import java.io.OutputStream
import java.nio.file.Files

/**
 * 아카이브 항목을 추출 루트 아래에 만드는 쓰기 도우미. 형식과 무관한 규칙(위치 검증, 폴더 생성, 파일 생성, 겹침 처리, 건너뜀 집계)을 한 곳에 두어 [ArchiveExtractor] 구현체마다 같은 항목을 다르게 다루지 않도록 한다.
 * 폴더는 이미 있으면 묻지 않고 합친다. 파일 자리에 같은 이름의 항목이 있거나 폴더 자리에 파일이 있으면 [ask]에 묻는다(`LocalConflictWrites.kt`).
 * 건너뜀으로 세는 항목은 루트 밖이나 루트 자신을 가리키는 항목뿐이며, 묻고 건너뛴 항목은 세지 않는다. 만들지 못한 폴더·파일은 IOException으로 알린다.
 * @param root 항목의 위치를 정하는 추출 루트
 * @param ask 겹침의 처리 방법을 묻는 함수. 추출 라이브러리의 콜백 안에서 부르므로 일시 중단하지 않고 답을 기다린다
 */
class ArchiveEntryWriter(private val root: ExtractionRoot, private val ask: (WriteConflict) -> ConflictChoice) {

    /** 루트 밖이나 루트 자신을 가리켜 건너뛴 항목 수. */
    var skippedEntries = 0
        private set

    /** 폴더 자리에 파일이 있어 건너뛰기로 한 자리. 그 아래 항목은 다시 묻지 않고 건너뛴다. */
    private val skippedFolders = mutableSetOf<File>()

    /**
     * 폴더 항목을 디렉터리로 만든다. 이미 디렉터리가 있으면 그대로 둔다(합친다).
     * @param entryName 아카이브 안의 '/' 구분 항목 경로
     * @throws java.io.IOException 디렉터리를 만들지 못했을 때
     */
    fun createFolder(entryName: String) {
        targetOrSkip(entryName)?.let(::folderReady)
    }

    /**
     * 파일 항목을 쓸 출력 스트림을 연다. 부모 디렉터리가 없으면 만든다. 같은 이름의 항목이 있으면 [ask]에 묻는다. 닫는 일은 호출부가 한다.
     * @param entryName 아카이브 안의 '/' 구분 항목 경로
     * @return 항목 위치의 출력 스트림. 건너뛴 항목이면 null
     * @throws java.io.IOException 부모 디렉터리나 파일을 만들지 못했을 때
     */
    fun openFile(entryName: String): OutputStream? {
        val target = targetOrSkip(entryName) ?: return null
        val parent = target.parentFile ?: return null
        if (!folderReady(parent)) return null
        return openNewFileResolving(target, ask)
    }

    /**
     * 취소로 멈춘 파일 항목을 지운다 — 쓰다 만 파일이 온전한 파일처럼 남지 않도록. 그 항목의 출력 스트림을 닫은 뒤에 호출한다.
     * @param entryName [openFile]로 연 항목의 '/' 구분 경로
     */
    fun discardFile(entryName: String) {
        root.targetOf(entryName)?.delete()
    }

    /**
     * [dir]과 루트까지의 그 상위 폴더가 모두 폴더로 있게 한다. 이미 있는 폴더는 합치고, 폴더 자리에 파일이 있으면 [ask]에 묻는다(건너뛰기만 가능).
     * @param dir 루트 아래(루트 포함)의 폴더 자리
     * @return [dir]에 쓸 수 있으면 true. 그 자리나 상위 자리를 건너뛰기로 했으면 false
     * @throws java.io.IOException 폴더를 만들지 못했을 때
     */
    private fun folderReady(dir: File): Boolean {
        if (skippedFolders.any { dir.startsWith(it) }) return false
        return foldersFromRootTo(dir).all { folder ->
            folder.isDirectory || mergeDirectoryResolving(folder, ask) || false.also { skippedFolders += folder }
        }
    }

    /**
     * 루트부터 [dir]까지의 폴더 자리를 위에서부터 차례로 돌려준다.
     * @param dir 루트 아래(루트 포함)의 폴더 자리
     */
    private fun foldersFromRootTo(dir: File): List<File> =
        generateSequence(dir) { if (it == root.directory) null else it.parentFile }.toList().asReversed()

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
