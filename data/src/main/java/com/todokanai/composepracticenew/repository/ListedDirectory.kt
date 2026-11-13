package com.todokanai.composepracticenew.repository

import com.todokanai.fileexplorer.FileEntry

/**
 * 디렉터리 목록 조회 결과.
 * @param entries 읽은 항목
 * @param isComplete 저장소가 보낸 항목을 모두 읽었으면 true. 읽지 못한 항목(FTP 목록에서 해석하지 못한 줄)이 있으면 false — 빠진 항목이 있을 수 있어 이 목록으로 이름 충돌을 판정하지 않는다
 */
data class ListedDirectory(val entries: List<FileEntry>, val isComplete: Boolean)
