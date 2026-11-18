package com.todokanai.composepracticenew.model

/**
 * 원격 작업을 실행 요청 때 묶는 연결 세션의 표지(#436). 작업은 이 표지의 세션·서버에서만 실행되고, 그 사이 다른 서버에 접속해도 옮겨 가지 않는다.
 * 서버 접속 정보(비밀번호 포함)를 담으므로 메모리에만 두고, 저장소나 화면 저장 상태(`rememberSaveable`)에 넣지 않는다.
 * 같은 세션인지는 [id]로만 판단한다. 접속 정보가 문자열로 드러나지 않도록 data class로 두지 않는다.
 * @param id 연결할 때마다 새로 발급되는 세션 식별값
 * @param server 이 세션이 연결한 서버의 접속 정보. 작업용 연결을 따로 열 때 쓴다
 */
class RemoteSession(val id: Long, val server: RemoteStorageItem)
