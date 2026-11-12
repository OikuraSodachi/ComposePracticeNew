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
 * 묻는 함수는 inline 람다로 받아, 일시 중단 함수(복사·이동)와 라이브러리 콜백 안의 추출(일시 중단 불가)이 같은 규칙을 쓴다.
 */

/**
 * [dir] 폴더를 만든다. 같은 이름의 항목이 이미 있으면 [ask]에 묻는다.
 * @param dir 만들 폴더. 부모 폴더는 있어야 한다
 * @param ask 겹침의 처리 방법을 묻는 함수
 * @return [dir]에 쓸 수 있으면 true(새로 만들었거나 기존 폴더와 합치기로 함). 건너뛰기로 했으면 false
 * @throws java.io.IOException 폴더를 만들지 못했을 때
 */
internal inline fun createDirectoryResolving(dir: File, ask: (WriteConflict) -> ConflictChoice): Boolean {
    try {
        Files.createDirectory(dir.toPath())
        return true
    } catch (e: FileAlreadyExistsException) {
        return ask(conflictAt(dir, sourceIsDirectory = true)) == ConflictChoice.PROCEED
    }
}

/**
 * [dir] 폴더를 만든다. 이미 폴더가 있으면 묻지 않고 합치고, 폴더가 아닌 항목이 있으면 [ask]에 묻는다(건너뛰기만 가능). 압축 해제가 폴더를 다루는 규칙이다.
 * @param dir 만들 폴더. 부모 폴더는 있어야 한다
 * @param ask 겹침의 처리 방법을 묻는 함수
 * @return [dir]에 쓸 수 있으면 true. 건너뛰기로 했으면 false
 * @throws java.io.IOException 폴더를 만들지 못했을 때
 */
internal inline fun mergeDirectoryResolving(dir: File, ask: (WriteConflict) -> ConflictChoice): Boolean {
    try {
        Files.createDirectory(dir.toPath())
        return true
    } catch (e: FileAlreadyExistsException) {
        return dir.isDirectory || ask(conflictAt(dir, sourceIsDirectory = true)) == ConflictChoice.PROCEED
    }
}

/**
 * [file]을 새로 만들어 쓸 출력 스트림을 연다. 같은 이름의 항목이 이미 있으면 [ask]에 묻고, 덮어쓰기로 했으면 기존 파일을 비우고 연다.
 * 덮어쓰기는 지우고 다시 만들지 않고 한 번에 비우며 연다 — 지운 뒤 만들기 전에 생긴 항목과 다시 겹치지 않도록.
 * 닫는 일은 호출부가 한다.
 * @param file 만들 파일. 부모 폴더는 있어야 한다
 * @param ask 겹침의 처리 방법을 묻는 함수
 * @return 출력 스트림. 건너뛰기로 했으면 null
 * @throws java.io.IOException 파일을 열지 못했을 때
 */
internal inline fun openNewFileResolving(file: File, ask: (WriteConflict) -> ConflictChoice): OutputStream? {
    try {
        return Files.newOutputStream(file.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    } catch (e: FileAlreadyExistsException) {
        if (ask(conflictAt(file, sourceIsDirectory = false)) != ConflictChoice.PROCEED) return null
    }
    return Files.newOutputStream(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
}

/**
 * 이미 있는 [target]과 그 자리에 쓰려는 항목의 종류로 겹침을 만든다.
 * @param target 같은 이름의 항목이 이미 있는 자리
 * @param sourceIsDirectory 쓰려는 항목이 폴더이면 true
 * @return 겹침
 */
internal fun conflictAt(target: File, sourceIsDirectory: Boolean): WriteConflict {
    val kind = when {
        target.isDirectory != sourceIsDirectory -> WriteConflictKind.TYPE_MISMATCH
        sourceIsDirectory -> WriteConflictKind.FOLDER
        else -> WriteConflictKind.FILE
    }
    return WriteConflict(target.name, target.parent.orEmpty(), kind)
}
