package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.FileSystemRefusal
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException

/**
 * 로컬 파일 작업 [block]을 실행하고, java.io·java.nio가 던진 입출력 실패(IOException)를 이 작업만의 실패로 바꾼다.
 * 로컬 작업의 입출력 실패는 의미가 하나(이 작업 실패)뿐이라 예외 종류로 판정해도 다른 의미와 섞이지 않는다.
 * 작업이 정한 실패(디스크 공간 부족 등)는 [block]이 결과로 돌려준다. 취소와 구현 오류(그 밖의 예외)는 결과로 바꾸지 않고 전파한다.
 * 입출력 실패에 작업 문맥(어느 항목인지 등)을 붙여야 하면 그 단계만 [describe]를 준 localIo로 감싼다.
 * @param describe 입출력 실패를 원인으로 바꾸는 함수. 기본은 [ioFailureReasonOf]다
 * @param block 실행할 로컬 파일 작업
 * @return [block]의 결과, 또는 입출력 실패
 */
internal inline fun <T> localIo(describe: (IOException) -> FailureReason? = ::ioFailureReasonOf, block: () -> IoResult<T>): IoResult<T> =
    try {
        block()
    } catch (e: IOException) {
        IoResult.ItemFailed(describe(e))
    }

/**
 * [file]을 쓰는 [block]을 실행하고, 취소로 멈추면 [file]을 지운 뒤 취소를 전파한다 — 쓰다 만 파일이 온전한 파일처럼 남지 않도록.
 * 입출력 실패로 멈춘 파일은 지우지 않는다(실패는 결과로 알린다). [block]은 [file]의 출력을 닫고 끝나야 한다.
 * @param file [block]이 쓰는 파일
 * @param block [file]을 쓰는 작업
 * @return [block]의 결과
 */
internal inline fun <T> deletingOnCancel(file: File, block: () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        file.delete()
        throw e
    }

/**
 * [file]을 쓰는 [block]을 실행하고, 끝까지 성공하지 못하면(취소·입출력 실패·구현 오류) [file]을 지운 뒤 그 예외를 그대로 전파한다.
 * 닫을 때 목차를 써서 멈춘 곳까지만 담고도 온전하게 열리는 형식(ZIP — 압축, #427)에 쓴다. 지우지 못해도 원래 실패를 알린다.
 * [file]은 이 작업이 새로 만든 파일이어야 한다 — 기존 파일을 지우지 않도록.
 * @param file [block]이 쓰는 파일
 * @param block [file]을 쓰는 작업
 * @return [block]의 결과
 */
internal inline fun <T> deletingUnlessCompleted(file: File, block: () -> T): T =
    try {
        block()
    } catch (e: Throwable) {
        file.delete()
        throw e
    }

/**
 * 입출력 실패를 원인으로 바꾼다. [localIo]의 기본값이며, 문맥을 붙이는 describe도 예외 메시지 대신 이것을 쓴다.
 * java.nio는 권한 없음·없음·이미 있음 등을 전용 예외로 바꾸며 이유를 비워 두므로 메시지가 경로뿐이다. 그런 예외는 종류로 원인을 정한다.
 * @param e 입출력 실패
 * @return 실패 원인. 이유가 있는 예외는 그 메시지 그대로이고, 메시지가 없으면 null
 */
internal fun ioFailureReasonOf(e: IOException): FailureReason? =
    if (e is FileSystemException && e.reason == null) FailureReason.FileSystemRefused(e.message, missingReasonOf(e))
    else e.message?.let(FailureReason::IoFailed)

/**
 * 이유를 비워 둔 java.nio 예외의 이유를 예외 종류로 정한다.
 * @param e 이유가 없는 파일 시스템 예외
 * @return 거부 종류
 */
private fun missingReasonOf(e: FileSystemException): FileSystemRefusal = when (e) {
    is FileAlreadyExistsException -> FileSystemRefusal.AlreadyExists
    is AccessDeniedException -> FileSystemRefusal.AccessDenied
    is NoSuchFileException -> FileSystemRefusal.NotFound
    is DirectoryNotEmptyException -> FileSystemRefusal.NotEmpty
    is NotDirectoryException -> FileSystemRefusal.NotFolder
    else -> FileSystemRefusal.Other(e.javaClass.simpleName)
}
