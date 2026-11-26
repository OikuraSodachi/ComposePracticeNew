package com.todokanai.composepracticenew.di

import javax.inject.Qualifier

/**
 * 압축 파일 내용 보기에서 넣을 로컬 항목을 고르는 화면 전용 탐색 인스턴스(FileNavigatorRepository와 그것을 쓰는 FileNavigatorUseCase)를 로컬 목록의 탐색과 구분하는 Hilt 한정자.
 * 고르는 동안 폴더를 옮겨도 아래에 있는 로컬 목록의 경로·목록이 바뀌지 않도록 따로 둔다.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ArchivePickerNavigator
