package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.RemoteSession

/** 지금 연결된 원격 세션을 읽기만 하는 계약. 원격 작업이 실행 요청 때 자기 세션을 정하는 데 쓴다(#436). */
interface RemoteSessionProvider {

    /**
     * 지금 연결된 세션의 표지를 돌려준다. 잠금 없이 읽는다.
     * @return 연결된 세션의 표지. 연결돼 있지 않으면(연결 작업 중·끊김·종료됨) null
     */
    fun current(): RemoteSession?
}
