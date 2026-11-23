package com.todokanai.composepracticenew.ui.model

/** 저장소 화면에서 고른 저장소를 연 뒤 넘어갈 목록 화면. */
enum class StorageExit {
    /** 고른 로컬 저장소 루트로 이동했으므로 로컬 목록 화면으로 넘어간다. */
    LOCAL_LIST,

    /** 원격 저장소에 연결했으므로 원격 목록 화면으로 넘어간다. */
    REMOTE_LIST,
}
