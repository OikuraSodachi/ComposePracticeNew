package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.model.ArchiveFormat
import java.io.File

/**
 * 아카이브 한 형식의 쓰기를 담당하는 전략 인터페이스.
 * 구현체는 Hilt `@IntoSet`으로 등록되며 [format]은 구현체끼리 겹치지 않아야 한다.
 * 새 형식은 [ArchiveFormat] 항목과 이 인터페이스 구현, DI 등록만으로 추가된다.
 * 항목 수집·공간 검사·새 파일 확보·진행 시작/완료 보고는 호출부([LocalArchiveCompression])가 하고, 구현체는 받은 항목을 그 형식으로 쓰기만 한다.
 * 쓰기 실패는 IOException으로 알린다 — 호출부는 IOException만 실패 결과로 바꾼다(localIo).
 */
interface ArchiveCompressor {

    /** 이 구현체가 쓰는 아카이브 형식. */
    val format: ArchiveFormat

    /**
     * [entries]를 [output]에 [format] 아카이브로 쓰며 [tracker]로 파일마다 진행을 보고한다.
     * @param entries 담을 항목. 폴더 항목은 언제나 그 하위 항목보다 앞에 온다
     * @param output 이 작업을 위해 새로 만든 빈 파일
     * @param tracker 누적 진행률 보고기
     */
    fun write(entries: List<ArchiveSource>, output: File, tracker: ByteProgressTracker)
}

/**
 * 아카이브에 담을 항목 하나.
 * @param name 아카이브 안의 '/' 구분 경로(폴더도 끝에 '/'를 붙이지 않는다 — 폴더 표기는 형식마다 다르므로 구현체가 정한다)
 * @param file 원본 파일 또는 폴더
 */
data class ArchiveSource(val name: String, val file: File)
