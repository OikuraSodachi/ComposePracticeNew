package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.RemoteProtocolSettings
import com.todokanai.composepracticenew.model.RemoteSession
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.RemoteSessionProvider

/** 늘 같은 연결된 세션을 돌려주는 RemoteSessionProvider. 원격 작업을 연결된 상태에서 요청하는 테스트가 쓴다. */
internal object ConnectedSessionProvider : RemoteSessionProvider {
    /** 늘 돌려주는 연결된 세션. */
    val session = RemoteSession(1, RemoteStorageItem(name = "server", address = "host", port = 21, userId = "user", password = "password", settings = RemoteProtocolSettings.Ftp("UTF-8")))

    override fun current(): RemoteSession? = session
}
