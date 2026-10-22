package com.todokanai.composepracticenew.data.ftp

import java.io.IOException

/**
 * 제어 채널의 응답 순서를 해치지 않은 실패 — 서버가 오류 응답을 보냈거나, 데이터 스트림·로컬 파일에서 실패한 경우.
 * 세션을 계속 쓸 수 있으므로 여러 항목 작업은 이 항목만 실패로 모으고 다음 항목을 진행한다.
 * 이 예외가 아닌 IOException은 응답을 읽다 실패한 것으로 보고 [FtpSession.withConnectedClient]가 세션을 종료한다.
 * @param message 표시할 오류 메시지
 * @param cause 실패 원인 예외
 */
internal class FtpItemFailure(message: String, cause: Throwable? = null) : IOException(message, cause)
