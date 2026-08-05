package com.todokanai.composepracticenew.di

import javax.inject.Qualifier

/** 원격 스토리지 전용 FileNavigatorRepository 인스턴스를 구분하는 Hilt 한정자. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RemoteNavigator
