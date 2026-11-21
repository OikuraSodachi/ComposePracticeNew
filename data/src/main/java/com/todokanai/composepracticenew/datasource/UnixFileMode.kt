package com.todokanai.composepracticenew.datasource

/**
 * Unix 파일 모드(`st_mode`)가 심볼릭 링크를 나타내는지 판별한다. 아카이브가 Unix 모드로 항목 종류를 담는 형식(ZIP 외부 속성, p7zip의 7z 속성, ISO Rock Ridge)이 함께 쓴다.
 * @param mode 하위 16비트에 파일 종류(S_IFMT)와 권한을 담은 Unix 파일 모드
 * @return 파일 종류가 S_IFLNK면 true
 */
internal fun isSymlinkMode(mode: Int): Boolean = (mode and S_IFMT) == S_IFLNK

/** Unix 파일 모드에서 파일 종류를 고르는 마스크. */
private const val S_IFMT = 0xF000

/** Unix 파일 모드의 심볼릭 링크 종류 값. */
private const val S_IFLNK = 0xA000
