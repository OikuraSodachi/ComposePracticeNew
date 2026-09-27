package com.todokanai.composepracticenew.model

import com.todokanai.composepracticenew.myobjects.Constants

/**
 * 원격 스토리지 접속 정보를 표현하는 도메인 모델.
 * @param id 고유 식별자
 * @param name 스토리지 표시 이름
 * @param address 서버 주소
 * @param port 서버 포트 번호
 * @param userId 로그인 아이디
 * @param password 로그인 비밀번호
 * @param encoding 파일명 인코딩
 */
data class RemoteStorageItem(
    val id: Long = 0,
    val name: String,
    val address: String,
    val port: Int,
    val userId: String,
    val password: String,
    val encoding: String = Constants.FTP_ENCODING_DEFAULT
)
