package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.FolderEntryNames

/** 업로드 원본 저장소에서 올릴 폴더 안쪽의 항목 이름을 읽는다. */
interface FolderEntryNamesRepository {

    /**
     * [paths] 중 폴더마다 그 아래 모든 폴더(자신 포함)의 바로 안 항목 이름을 읽는다. 폴더가 아닌 경로는 건너뛴다.
     * 업로드가 폴더를 올릴 때와 같은 방식으로 읽어, 업로드가 같은 폴더에 함께 올릴 이름을 얻는다. 코루틴이 취소되면 읽기를 멈춘다.
     * @param paths 항목 경로 목록
     * @return 폴더별 항목 이름(바로 안에 항목이 있는 폴더만, 위 폴더부터)
     */
    suspend fun folderEntryNames(paths: List<String>): List<FolderEntryNames>
}
