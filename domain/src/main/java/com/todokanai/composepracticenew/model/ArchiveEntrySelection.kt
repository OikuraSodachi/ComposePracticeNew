package com.todokanai.composepracticenew.model

/**
 * 내용 보기가 읽은 압축 파일 내용.
 * @param root 루트 폴더
 * @param listing 이 트리를 만든 항목 목록의 식별값 — 이 트리의 키로 고른 항목을 풀 때, 그사이 압축 파일이 바뀌었는지 가린다
 */
class ArchiveContents(val root: ArchiveFolder, val listing: ArchiveListingId)

/**
 * 압축 파일 항목 목록 하나를 가리키는 값. 항목의 순서·경로·종류·크기·수정 시각·암호화 여부가 모두 같은 목록이면 같고, 하나라도 다르면 다르다.
 * 항목 키([ArchiveEntryKey.Item])는 목록에서의 순번이라, 목록이 바뀌면 같은 키가 다른 항목을 가리킨다 — 키를 쓰는 쪽은 이 값으로 같은 목록인지 확인한다.
 * @param digest 목록 내용에서 계산한 값. 구현이 정하며 같음 비교에만 쓴다
 */
data class ArchiveListingId(val digest: String)

/**
 * 내용 보기의 한 폴더 안에서 고른 압축 파일 항목들.
 * 폴더를 고르면 그 아래 항목 전체를 고른 것이다. 고른 항목은 [folderNames] 폴더 기준의 상대 위치로 풀린다.
 * @param archivePath 압축 파일(분할 압축이면 시작 볼륨) 경로
 * @param listing 키를 읽은 항목 목록의 식별값
 * @param folderNames 고른 항목이 있는 폴더의 이름들(루트부터). 루트면 빈 목록
 * @param keys 고른 항목의 키. [folderNames] 폴더 안에서만 항목마다 다르다
 */
data class ArchiveEntrySelection(
    val archivePath: String,
    val listing: ArchiveListingId,
    val folderNames: List<String>,
    val keys: Set<ArchiveEntryKey>,
)
