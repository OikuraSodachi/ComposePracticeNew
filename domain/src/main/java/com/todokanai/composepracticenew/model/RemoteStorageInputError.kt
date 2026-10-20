package com.todokanai.composepracticenew.model

/** 원격 스토리지 접속 정보 입력이 거부되는 사유. */
enum class RemoteStorageInputError {
    /** 이름이 비어 있거나 공백뿐이다. */
    NAME_BLANK,

    /** 주소가 비어 있거나 공백뿐이다. */
    ADDRESS_BLANK,

    /** 포트가 정수가 아니거나 1~65535 범위를 벗어났다. */
    PORT_OUT_OF_RANGE
}
