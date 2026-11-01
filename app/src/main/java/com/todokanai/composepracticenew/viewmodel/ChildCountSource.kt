package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.ui.model.ChildCountInfo
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import kotlinx.coroutines.flow.Flow

/** 목록 조회 결과 하나에 속한 폴더들의 하위 항목 수를 폴더 행이 읽는 창구. */
interface ChildCountSource {

    /**
     * [directory]의 지금 알고 있는 표시 상태를 돌려준다. 세지 않는다.
     * @param directory 이 조회 결과의 폴더 항목
     * @return 다 셌으면 그 결과, 아니면 [ChildCountInfo.Loading]
     */
    fun currentOf(directory: FileHolderItem): ChildCountInfo

    /**
     * [directory]의 하위 항목 수를 내보낸다. 이 조회 결과에서 이미 셌으면 그 결과를, 아니면 세어 결과를 한 번 내보낸다.
     * @param directory 이 조회 결과의 폴더 항목
     * @return 표시 상태를 한 번 내보내는 Flow
     */
    fun countOf(directory: FileHolderItem): Flow<ChildCountInfo>
}
