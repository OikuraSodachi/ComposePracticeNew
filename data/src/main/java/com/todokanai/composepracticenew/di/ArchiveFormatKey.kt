package com.todokanai.composepracticenew.di

import com.todokanai.composepracticenew.model.ArchiveFormat
import dagger.MapKey

/**
 * 형식별 압축 구현(ArchiveCompressor)을 맵 멀티바인딩에 등록하는 키. 같은 형식을 두 번 등록하면 Dagger가 컴파일 오류로 알려, 한 형식에 구현이 하나만 있다.
 * @param value 등록하는 구현이 쓰는 아카이브 형식
 */
@MapKey
annotation class ArchiveFormatKey(val value: ArchiveFormat)
