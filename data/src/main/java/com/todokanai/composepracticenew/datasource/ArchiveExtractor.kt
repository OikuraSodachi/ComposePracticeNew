package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker

/**
 * 아카이브 한 형식의 크기 산정과 추출을 담당하는 전략 인터페이스.
 * 구현체는 Hilt `@IntoSet`으로 등록되며 [nameRoleOf]가 역할을 주는 이름은 형식 간 서로 배타적이어야 한다.
 * 분할 압축의 [measure]·[extract]는 시작 볼륨 경로를 받아 같은 폴더의 다른 볼륨까지 읽고, 볼륨이 빠졌으면 [MissingArchiveVolumeException]으로 알린다.
 * 새 형식은 이 인터페이스 구현과 DI 등록만으로 추가된다.
 * 읽지 못하는 아카이브(손상, 이름 해석 실패, 이름 없는 항목 등)는 [measure]·[extract] 모두 IOException으로 알린다 — 호출부는 IOException만 실패 결과로 바꾸므로(localIo), 라이브러리가 입력 결함을 다른 예외로 알리면 구현체가 IOException으로 바꾼다.
 */
interface ArchiveExtractor {

    /**
     * 이 추출기의 형식에서 [fileName]이 맡는 역할(단일 아카이브·시작 볼륨, 또는 다른 볼륨)을 파일 이름으로만 정한다.
     * 파일을 읽지 않으므로 이름과 내용이 다른 파일도 역할을 받을 수 있으며, 그런 파일은 [measure]·[extract]에서 실패로 끝난다.
     * @param fileName 파일 이름
     * @return 역할. 이 형식의 이름이 아니면 null
     */
    fun nameRoleOf(fileName: String): ArchiveNameRole?

    /**
     * 추출 전 진행률 계산에 필요한 총 바이트·파일 수를 산정한다. 아카이브가 크기를 알려주지 않는 항목은 따로 센다.
     * 항목 목록까지 암호화한 아카이브는 목록을 읽으려고 [passwords]로 비밀번호를 받는다.
     * @param path 아카이브 파일 경로
     * @param passwords 이 아카이브의 비밀번호를 묻고 확인한 것을 기억하는 세션([extract]와 같은 것을 넘긴다)
     * @return 크기를 아는 항목의 바이트 합, 파일 개수, 크기를 모르는 파일 개수
     */
    fun measure(path: String, passwords: ArchivePasswordSession): ArchiveSize

    /**
     * [path] 아카이브의 항목을 [writer]로 만들며 [tracker]로 진행률을 보고한다. [measure]가 크기를 모른다고 센 항목은 크기 없이(null) 보고한다.
     * 폴더 항목은 [ArchiveEntryWriter.createFolder], 파일 항목은 [ArchiveEntryWriter.openFile]로만 만든다 — 위치 검증과 건너뜀 집계는 [writer]가 한다.
     * 암호화된 항목은 그 출력을 만들기 전에 [passwords]로 확인한 비밀번호를 받는다([ArchivePasswordSession.verifiedPassword]).
     * 쓰기 실패와 풀 수 없는 항목(CRC·데이터 오류 등)은 그 항목에서 멈추고 IOException으로 알린다(작업 전체 실패). 이미 만든 항목은 남는다.
     * @param path 아카이브 파일 경로
     * @param writer 항목을 추출 루트 아래에 만드는 쓰기 도우미
     * @param passwords 이 아카이브의 비밀번호를 묻고 확인한 것을 기억하는 세션
     * @param tracker 누적 진행률 보고기
     */
    fun extract(path: String, writer: ArchiveEntryWriter, passwords: ArchivePasswordSession, tracker: ByteProgressTracker)
}

/**
 * 아카이브 추출 대상의 크기 정보.
 * @param knownBytes 크기를 아는 항목의 바이트 합(0 이상)
 * @param fileCount 파일(디렉터리 제외) 개수
 * @param unknownSizeCount 아카이브가 크기를 알려주지 않는 파일 개수(0 이상, [fileCount] 이하)
 */
data class ArchiveSize(val knownBytes: Long, val fileCount: Int, val unknownSizeCount: Int)
