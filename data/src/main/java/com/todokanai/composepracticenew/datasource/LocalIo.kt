package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.IoResult
import java.io.IOException

/**
 * 로컬 파일 작업 [block]을 실행하고, java.io·java.nio가 던진 입출력 실패(IOException)를 이 작업만의 실패로 바꾼다.
 * 로컬 작업의 입출력 실패는 의미가 하나(이 작업 실패)뿐이라 예외 종류로 판정해도 다른 의미와 섞이지 않는다.
 * 작업이 정한 실패(디스크 공간 부족 등)는 [block]이 결과로 돌려준다. 취소와 구현 오류(그 밖의 예외)는 결과로 바꾸지 않고 전파한다.
 * 입출력 실패에 작업 문맥(어느 항목인지 등)을 붙여야 하면 그 단계만 [describe]를 준 localIo로 감싼다.
 * @param describe 입출력 실패를 표시할 메시지로 바꾸는 함수. 기본은 예외 메시지 그대로다
 * @param block 실행할 로컬 파일 작업
 * @return [block]의 결과, 또는 입출력 실패
 */
internal inline fun <T> localIo(describe: (IOException) -> String? = { it.message }, block: () -> IoResult<T>): IoResult<T> =
    try {
        block()
    } catch (e: IOException) {
        IoResult.ItemFailed(describe(e))
    }
