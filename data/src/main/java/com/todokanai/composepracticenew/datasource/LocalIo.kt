package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.IoResult
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
 * @param describe 입출력 실패를 표시할 메시지로 바꾸는 함수. 기본은 [ioFailureMessageOf]다
 * @param block 실행할 로컬 파일 작업
 * @return [block]의 결과, 또는 입출력 실패
 */
internal inline fun <T> localIo(describe: (IOException) -> String? = ::ioFailureMessageOf, block: () -> IoResult<T>): IoResult<T> =
    try {
        block()
    } catch (e: IOException) {
        IoResult.ItemFailed(describe(e))
    }

/**
 * 입출력 실패를 원인이 담긴 표시 메시지로 바꾼다. [localIo]의 기본값이며, 문맥을 붙이는 describe도 예외 메시지 대신 이것을 쓴다.
 * java.nio는 권한 없음·없음·이미 있음 등을 전용 예외로 바꾸며 이유를 비워 두므로 메시지가 경로뿐이다. 그런 예외는 종류로 이유를 붙인다.
 * @param e 입출력 실패
 * @return 표시할 실패 메시지. 이유가 있는 예외는 그 메시지 그대로다
 */
internal fun ioFailureMessageOf(e: IOException): String? =
    if (e is FileSystemException && e.reason == null) "${e.message}: ${missingReasonOf(e)}" else e.message

/**
 * 이유를 비워 둔 java.nio 예외의 이유를 예외 종류로 정한다.
 * @param e 이유가 없는 파일 시스템 예외
 * @return 표시할 이유
 */
private fun missingReasonOf(e: FileSystemException): String = when (e) {
    is FileAlreadyExistsException -> "같은 이름의 항목이 이미 있음"
    is AccessDeniedException -> "권한 없음"
    is NoSuchFileException -> "찾을 수 없음"
    is DirectoryNotEmptyException -> "비어 있지 않은 폴더"
    is NotDirectoryException -> "폴더가 아님"
    else -> "파일 시스템 오류(${e.javaClass.simpleName})"
}
