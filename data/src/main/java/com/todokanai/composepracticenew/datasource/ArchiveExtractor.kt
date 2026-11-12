package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker

/**
 * 아카이브 한 형식의 크기 산정과 추출을 담당하는 전략 인터페이스.
 * 구현체는 Hilt `@IntoSet`으로 등록되며 [supports]는 형식 간 서로 배타적이어야 한다.
 * 새 형식은 이 인터페이스 구현과 DI 등록만으로 추가된다.
 * 읽지 못하는 아카이브(손상, 이름 해석 실패, 이름 없는 항목 등)는 [measure]·[extract] 모두 IOException으로 알린다 — 호출부는 IOException만 실패 결과로 바꾸므로(localIo), 라이브러리가 입력 결함을 다른 예외로 알리면 구현체가 IOException으로 바꾼다.
 */
interface ArchiveExtractor {

    /**
     * 이 추출기가 [path]의 아카이브 형식을 처리하는지 파일 이름의 확장자로만 판별한다(대소문자 무시).
     * 파일을 읽지 않으므로 이름과 내용이 다른 파일도 true일 수 있으며, 그런 파일은 [measure]·[extract]에서 실패로 끝난다.
     * @param path 아카이브 파일 경로
     * @return 처리 가능하면 true
     */
    fun supports(path: String): Boolean

    /**
     * 추출 전 진행률 계산에 필요한 총 바이트·파일 수를 산정한다. 아카이브가 크기를 알려주지 않는 항목은 따로 센다.
     * @param path 아카이브 파일 경로
     * @return 크기를 아는 항목의 바이트 합, 파일 개수, 크기를 모르는 파일 개수
     */
    fun measure(path: String): ArchiveSize

    /**
     * [path] 아카이브의 항목을 [writer]로 만들며 [tracker]로 진행률을 보고한다. [measure]가 크기를 모른다고 센 항목은 크기 없이(null) 보고한다.
     * 폴더 항목은 [ArchiveEntryWriter.createFolder], 파일 항목은 [ArchiveEntryWriter.openFile]로만 만든다 — 위치 검증과 건너뜀 집계는 [writer]가 한다.
     * 쓰기 실패와 풀 수 없는 항목(CRC·데이터 오류 등)은 그 항목에서 멈추고 IOException으로 알린다(작업 전체 실패). 이미 만든 항목은 남는다.
     * @param path 아카이브 파일 경로
     * @param writer 항목을 추출 루트 아래에 만드는 쓰기 도우미
     * @param tracker 누적 진행률 보고기
     */
    fun extract(path: String, writer: ArchiveEntryWriter, tracker: ByteProgressTracker)
}

/**
 * 아카이브 추출 대상의 크기 정보.
 * @param knownBytes 크기를 아는 항목의 바이트 합(0 이상)
 * @param fileCount 파일(디렉터리 제외) 개수
 * @param unknownSizeCount 아카이브가 크기를 알려주지 않는 파일 개수(0 이상, [fileCount] 이하)
 */
data class ArchiveSize(val knownBytes: Long, val fileCount: Int, val unknownSizeCount: Int)
