package com.todokanai.composepracticenew.model

import com.todokanai.composepracticenew.myobjects.Constants

/** 원격 스토리지 접속 정보를 표현하는 도메인 모델. */
data class RemoteStorageItem(
    val id: Long = 0,
    val name: String,
    val address: String,
    val port: Int,
    val userId: String,
    val password: String,
    val encoding: String = Constants.FTP_ENCODING_DEFAULT
)
