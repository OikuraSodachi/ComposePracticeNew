package com.todokanai.composepracticenew.di

import javax.inject.Qualifier

/** 앱 생명주기와 동일한 범위를 가지는 CoroutineScope 주입을 식별하는 한정자. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
