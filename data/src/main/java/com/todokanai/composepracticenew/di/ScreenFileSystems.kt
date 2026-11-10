package com.todokanai.composepracticenew.di

import javax.inject.Qualifier

/**
 * 로컬 화면이 다루는 PathAwareFileSystem 집합의 한정자. 로컬 화면의 탐색·경로 규칙·이름 제약은 이 집합 하나를 함께 주입받아, 서로 다른 집합을 쓸 수 없다.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class LocalScreenFileSystems

/**
 * 원격 화면이 다루는 PathAwareFileSystem 집합의 한정자. 원격 화면의 탐색·경로 규칙·이름 제약은 이 집합 하나를 함께 주입받아, 서로 다른 집합을 쓸 수 없다.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class RemoteScreenFileSystems
