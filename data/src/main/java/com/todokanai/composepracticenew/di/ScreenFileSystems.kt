package com.todokanai.composepracticenew.di

import javax.inject.Qualifier

/**
 * 로컬 화면이 다루는 파일 시스템 역할 집합(탐색·경로 규칙·이름 제약)의 한정자. 세 역할의 집합은 모두 같은 구현(로컬)만 담도록 RepositoryModule이 함께 정한다.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class LocalScreenFileSystems

/**
 * 원격 화면이 다루는 파일 시스템 역할 집합(탐색·경로 규칙·이름 제약)의 한정자. 세 역할의 집합은 모두 등록된 모든 구현을 담도록 RepositoryModule이 함께 정한다.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class RemoteScreenFileSystems
