package com.todokanai.composepracticenew.model

import java.io.IOException

/**
 * 원격 스토리지와의 연결이 없거나 작업 도중 끊겨 남은 원격 작업을 더 진행할 수 없음을 알리는 예외.
 * 여러 항목을 처리하는 작업은 이 예외를 받으면 나머지 항목을 시도하지 않고 즉시 중단한다.
 * @param message 표시할 오류 메시지
 * @param cause 연결 끊김을 일으킨 원인 예외
 */
class RemoteConnectionLostException(message: String, cause: Throwable? = null) : IOException(message, cause)
