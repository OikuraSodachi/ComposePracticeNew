package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import java.io.File

/**
 * 아카이브 한 형식의 크기 산정과 추출을 담당하는 전략 인터페이스.
 * 구현체는 Hilt `@IntoSet`으로 등록되며 [supports]는 형식 간 서로 배타적이어야 한다.
 * 새 형식은 이 인터페이스 구현과 DI 등록만으로 추가된다.
 */
interface ArchiveExtractor {

    /**
     * 이 추출기가 [path]의 아카이브 형식을 처리하는지 판별한다.
     * @param path 아카이브 파일 경로
     * @return 처리 가능하면 true
     */
    fun supports(path: String): Boolean

    /**
     * 추출 전 진행률 계산에 필요한 총 바이트·파일 수를 산정한다.
     * @param path 아카이브 파일 경로
     * @return 추출될 항목의 바이트 합과 파일 개수
     */
    fun measure(path: String): ArchiveSize

    /**
     * [path] 아카이브를 [root] 아래로 추출하며 [tracker]로 진행률을 보고한다.
     * @param path 아카이브 파일 경로
     * @param root 추출 대상 루트 디렉터리
     * @param tracker 누적 진행률 보고기
     * @return 경로 검증 실패 등으로 건너뛴 항목 수
     */
    fun extract(path: String, root: File, tracker: ByteProgressTracker): Int
}

/**
 * 아카이브 추출 대상의 크기 정보.
 * @param bytes 항목 바이트 합
 * @param fileCount 파일(디렉터리 제외) 개수
 */
data class ArchiveSize(val bytes: Long, val fileCount: Int)
