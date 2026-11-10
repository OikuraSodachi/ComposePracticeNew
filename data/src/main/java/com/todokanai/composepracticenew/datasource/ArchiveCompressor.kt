package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import java.io.File

/**
 * 아카이브 한 형식의 쓰기를 담당하는 전략 인터페이스.
 * 구현체는 Hilt `@IntoMap`에 쓰는 형식을 키(`ArchiveFormatKey`)로 등록한다 — 형식은 키 하나로만 정하며, 같은 형식을 두 번 등록하면 컴파일 오류다.
 * 새 형식은 `ArchiveFormat` 항목과 이 인터페이스 구현, DI 등록만으로 추가된다.
 * 항목 수집·공간 검사·새 파일 확보·진행 시작/완료 보고는 호출부([LocalArchiveCompression])가 하고, 구현체는 받은 항목을 그 형식으로 쓰기만 한다.
 * 쓰기 실패는 IOException으로 알린다 — 호출부는 IOException만 실패 결과로 바꾼다(localIo).
 */
interface ArchiveCompressor {

    /**
     * [entries]를 [output]에 이 구현의 형식으로 쓰며 [tracker]로 파일마다 진행을 보고한다.
     * @param entries 담을 항목. 폴더 항목은 언제나 그 하위 항목보다 앞에 온다
     * @param output 이 작업을 위해 새로 만든 빈 파일
     * @param tracker 누적 진행률 보고기. 전체 크기·파일 수는 [entries]의 [ArchiveSource.Regular]로 셌다
     */
    fun write(entries: List<ArchiveSource>, output: File, tracker: ByteProgressTracker)
}

/**
 * 아카이브에 담을 항목 하나. 폴더인지 파일인지와 파일 크기는 항목을 모을 때 정하며, 구현체는 원본을 다시 확인하지 않고 이 값을 따른다 —
 * 진행 합계를 센 값과 쓰는 값이 같도록.
 */
sealed interface ArchiveSource {

    /** 아카이브 안의 '/' 구분 경로(폴더도 끝에 '/'를 붙이지 않는다 — 폴더 표기는 형식마다 다르므로 구현체가 정한다). */
    val name: String

    /**
     * 폴더 항목. 내용 없이 이름만 담아, 빈 폴더도 압축 해제 시 복원된다.
     * @param name 아카이브 안의 '/' 구분 경로
     */
    data class Folder(override val name: String) : ArchiveSource

    /**
     * 파일 항목.
     * @param name 아카이브 안의 '/' 구분 경로
     * @param file 내용을 읽을 원본 파일
     * @param bytes 항목을 모을 때 잰 파일 크기. 진행 합계에 넣은 값과 같다
     */
    data class Regular(override val name: String, val file: File, val bytes: Long) : ArchiveSource
}
