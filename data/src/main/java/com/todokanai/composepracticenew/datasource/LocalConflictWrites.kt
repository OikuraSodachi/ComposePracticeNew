package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.model.WriteConflictKind
import java.io.File
import java.io.OutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/*
 * 로컬 쓰기가 같은 이름의 항목을 묻지 않고 덮어쓰지 않게 하는 도우미.
 * 먼저 "없을 때만 만들기"(CREATE_NEW·createDirectory, 확인과 생성이 한 번의 파일 시스템 동작)로 시도하고,
 * 파일 시스템이 이미 있다고 알릴 때만(FileAlreadyExistsException) 묻는다 — 목록을 본 뒤 쓰기 전에 생긴 항목도 놓치지 않도록.
 * 이름이 같은지는 파일 시스템이 정하므로, 대소문자만 다른 이름을 같은 항목으로 다루는 볼륨에서도 그대로 맞다.
 * 이미 있는 항목의 종류는 링크를 따라가지 않고 정하며([entryKind]), 링크면 묻지 않고 [WriteSlot.Link]로 알린다(#338) —
 * 링크를 따라가면 덮어쓰기가 링크가 가리키는 파일(원본 자신일 수 있다)을 비우고, 합치기가 고른 목적지 밖에 쓴다.
 * 확인한 뒤 쓰기 전에 다른 앱이 그 자리를 링크로 바꾸는 경우는 지원 범위 밖이다.
 * 묻는 함수는 inline 람다로 받아, 일시 중단 함수(복사·이동·다운로드)와 라이브러리 콜백 안의 추출(일시 중단 불가)이 같은 규칙을 쓴다.
 */

/**
 * 쓰려는 자리를 확보한 결과.
 * @param T 확보했을 때 함께 돌려주는 값(폴더면 [Unit], 파일이면 출력 스트림)
 */
internal sealed interface WriteSlot<out T> {

    /**
     * 그 자리에 쓸 수 있다(새로 만들었거나 기존 항목과 합치기·덮어쓰기로 함).
     * @param value 확보한 자리에 쓰는 데 필요한 값
     */
    data class Ready<T>(val value: T) : WriteSlot<T>

    /** 같은 이름의 항목이 있어 건너뛰기로 했다. */
    data object Skipped : WriteSlot<Nothing>

    /** 그 자리에 심볼릭 링크가 있어 묻지 않고 건너뛴다. 링크는 지원 범위 밖이므로 호출부가 실패로 알린다. */
    data object Link : WriteSlot<Nothing>
}

/**
 * [dir] 폴더를 만든다. 같은 이름의 항목이 이미 있으면 [ask]에 묻는다.
 * @param dir 만들 폴더. 부모 폴더는 있어야 한다
 * @param ask 겹침의 처리 방법을 묻는 함수
 * @return 새로 만들었거나 기존 폴더와 합치기로 했으면 [WriteSlot.Ready]
 * @throws java.io.IOException 폴더를 만들지 못했을 때
 */
internal inline fun createDirectoryResolving(dir: File, ask: (WriteConflict) -> ConflictChoice): WriteSlot<Unit> {
    try {
        Files.createDirectory(dir.toPath())
        return WriteSlot.Ready(Unit)
    } catch (e: FileAlreadyExistsException) {
        return resolveExisting(dir, sourceIsDirectory = true, ask)
    }
}

/**
 * [dir] 폴더를 만든다. 이미 폴더가 있으면 묻지 않고 합치고, 폴더가 아닌 항목이 있으면 [ask]에 묻는다(건너뛰기만 가능). 압축 해제가 폴더를 다루는 규칙이다.
 * @param dir 만들 폴더. 부모 폴더는 있어야 한다
 * @param ask 겹침의 처리 방법을 묻는 함수
 * @return 새로 만들었거나 기존 폴더와 합치면 [WriteSlot.Ready]
 * @throws java.io.IOException 폴더를 만들지 못했을 때
 */
internal inline fun mergeDirectoryResolving(dir: File, ask: (WriteConflict) -> ConflictChoice): WriteSlot<Unit> {
    try {
        Files.createDirectory(dir.toPath())
        return WriteSlot.Ready(Unit)
    } catch (e: FileAlreadyExistsException) {
        if (dir.entryKind == LocalEntryKind.DIRECTORY) return WriteSlot.Ready(Unit)
        return resolveExisting(dir, sourceIsDirectory = true, ask)
    }
}

/**
 * [file]을 새로 만들어 쓸 출력 스트림을 연다. 같은 이름의 항목이 이미 있으면 [ask]에 묻고, 덮어쓰기로 했으면 기존 파일을 비우고 연다.
 * 덮어쓰기는 지우고 다시 만들지 않고 한 번에 비우며 연다 — 지운 뒤 만들기 전에 생긴 항목과 다시 겹치지 않도록.
 * 닫는 일은 호출부가 한다.
 * @param file 만들 파일. 부모 폴더는 있어야 한다
 * @param ask 겹침의 처리 방법을 묻는 함수
 * @return 연 출력 스트림을 담은 [WriteSlot.Ready]
 * @throws java.io.IOException 파일을 열지 못했을 때
 */
internal inline fun openNewFileResolving(file: File, ask: (WriteConflict) -> ConflictChoice): WriteSlot<OutputStream> {
    try {
        return WriteSlot.Ready(Files.newOutputStream(file.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
    } catch (e: FileAlreadyExistsException) {
        when (resolveExisting(file, sourceIsDirectory = false, ask)) {
            is WriteSlot.Ready -> Unit
            WriteSlot.Skipped -> return WriteSlot.Skipped
            WriteSlot.Link -> return WriteSlot.Link
        }
    }
    return WriteSlot.Ready(Files.newOutputStream(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))
}

/**
 * 같은 이름의 항목이 이미 있는 [target] 자리를 어떻게 할지 정한다. 링크면 묻지 않고, 아니면 [ask]에 묻는다.
 * @param target 같은 이름의 항목이 이미 있는 자리
 * @param sourceIsDirectory 쓰려는 항목이 폴더이면 true
 * @param ask 겹침의 처리 방법을 묻는 함수
 * @return 진행하기로 했으면 [WriteSlot.Ready], 건너뛰기로 했으면 [WriteSlot.Skipped], 링크면 [WriteSlot.Link]
 */
internal inline fun resolveExisting(target: File, sourceIsDirectory: Boolean, ask: (WriteConflict) -> ConflictChoice): WriteSlot<Unit> {
    val kind = target.entryKind
    if (kind == LocalEntryKind.LINK) return WriteSlot.Link
    return if (ask(conflictOf(target, kind, sourceIsDirectory)) == ConflictChoice.PROCEED) WriteSlot.Ready(Unit) else WriteSlot.Skipped
}

/**
 * 이미 있는 [target]과 그 자리에 쓰려는 항목의 종류로 겹침을 만든다.
 * @param target 같은 이름의 항목이 이미 있는 자리
 * @param existingKind [target]의 종류. 링크가 아니어야 한다
 * @param sourceIsDirectory 쓰려는 항목이 폴더이면 true
 * @return 겹침
 */
internal fun conflictOf(target: File, existingKind: LocalEntryKind, sourceIsDirectory: Boolean): WriteConflict {
    val kind = when {
        (existingKind == LocalEntryKind.DIRECTORY) != sourceIsDirectory -> WriteConflictKind.TYPE_MISMATCH
        sourceIsDirectory -> WriteConflictKind.FOLDER
        else -> WriteConflictKind.FILE
    }
    return WriteConflict(target.name, target.parent.orEmpty(), kind)
}
