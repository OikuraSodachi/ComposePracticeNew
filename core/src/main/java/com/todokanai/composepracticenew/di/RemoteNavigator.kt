package com.todokanai.composepracticenew.di

import javax.inject.Qualifier

/** 원격 스토리지 전용 탐색 인스턴스(FileNavigatorRepository·StoragePathRules와 그것을 쓰는 FileNavigatorUseCase)를 로컬 탐색과 구분하는 Hilt 한정자. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RemoteNavigator
