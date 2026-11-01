package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.ui.model.ChildCountInfo
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.ListingKey
import com.todokanai.composepracticenew.usecase.FileInfoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import javax.inject.Inject

/** 동시에 세는 폴더 수의 상한. 화면에 보이는 폴더 행 수보다 작게 두어 디렉터리 읽기가 한꺼번에 몰리지 않게 한다. */
private const val MAX_CONCURRENT_COUNTS = 4

/**
 * 파일 목록의 폴더 행에 표시할 하위 항목 수를, 목록 조회 요청([ListingKey])마다 [ListingChildCounts]로 묶어 제공한다.
 * 조회 요청으로 묶으므로 정렬을 바꾸거나 화면 상태를 다시 구독해 항목 목록이 새로 만들어져도 다시 세지 않고, 새 조회 결과(이동·새로고침·작업 후 갱신)만 다시 센다.
 * 결과를 이 ViewModel(목록 destination의 백스택 항목에 스코프)에 두므로 화면 회전·스크롤로 행이 다시 나타나도 다시 세지 않는다.
 * 로컬 목록 화면 전용이다 — [FileInfoUseCase]는 로컬 파일 시스템 구현에 바인딩돼 있다. 원격 목록은 세지 않는다(연결 하나를 사용자 작업과 나눠 써야 하므로 범위 밖).
 * @param fileInfoUseCase 폴더의 하위 항목 수를 세는 UseCase
 */
@HiltViewModel
class ChildCountViewModel @Inject constructor(
    private val fileInfoUseCase: FileInfoUseCase
) : ViewModel() {

    /** 모든 목록이 함께 쓰는 동시 세기 제한. 목록이 바뀌어도 이전 목록의 남은 세기와 합쳐 상한을 지킨다. */
    private val permits = Semaphore(MAX_CONCURRENT_COUNTS)

    /** 가장 나중 목록 조회 요청과 그 하위 항목 수. 그 조회 결과의 폴더가 처음 세기를 시작할 때 바뀌고, 이때 이전 요청의 결과를 버린다. */
    private var latest: Pair<ListingKey, ListingChildCounts>? = null

    /**
     * 목록 조회 요청 [listing]의 결과에 속한 폴더들의 하위 항목 수를 읽는 창구를 돌려준다.
     * 상태를 바꾸지 않으므로 컴포지션 중에 불러도 되고, 같은 요청이면 서로 같은(equals) 창구를 돌려준다.
     * @param listing 표시 중인 항목이 나온 목록 조회 요청
     * @return 그 조회 결과의 하위 항목 수를 읽는 창구
     */
    fun countsFor(listing: ListingKey): ChildCountSource = ListingSource(listing, this)

    /**
     * [listing] 결과의 폴더 [directory]에 대해 지금 알고 있는 표시 상태를 돌려준다. 세지 않고 상태도 바꾸지 않는다.
     * @param listing 폴더가 나온 목록 조회 요청
     * @param directory 그 조회 결과의 폴더 항목
     * @return 그 요청이 가장 나중 요청이고 이미 셌으면 그 결과, 아니면 [ChildCountInfo.Loading]
     */
    private fun currentOf(listing: ListingKey, directory: FileHolderItem): ChildCountInfo =
        latest?.takeIf { (key, _) -> key == listing }?.second?.currentOf(directory) ?: ChildCountInfo.Loading

    /**
     * [listing] 결과의 폴더 [directory]의 하위 항목 수를 내보낸다. 결과 보관소는 구독을 시작할 때 고른다 — 컴포지션이 아니라 수집 중에 상태를 바꾸도록.
     * @param listing 폴더가 나온 목록 조회 요청
     * @param directory 그 조회 결과의 폴더 항목
     * @return 표시 상태를 한 번 내보내는 Flow
     */
    private fun countOf(listing: ListingKey, directory: FileHolderItem): Flow<ChildCountInfo> = flow {
        emitAll(countsOf(listing).countOf(directory))
    }

    /**
     * [listing]의 결과 보관소를 돌려준다. 가장 나중 요청이면 그 보관소를, 더 새 요청이면 새로 만들어 이전 요청의 결과를 버린다.
     * 이미 지나간 요청이면 보관하지 않는 보관소를 새로 만든다 — 곧 사라질 이전 목록의 행이 가장 나중 요청의 결과를 버리지 않도록.
     * @param listing 폴더가 나온 목록 조회 요청
     * @return 그 요청의 결과 보관소
     */
    private fun countsOf(listing: ListingKey): ListingChildCounts {
        val latest = latest
        if (latest != null && latest.first == listing) return latest.second
        if (latest != null && latest.first.generation > listing.generation) return newCounts()
        return newCounts().also { this.latest = listing to it }
    }

    /**
     * 비어 있는 결과 보관소를 만든다.
     * @return 아직 아무 폴더도 세지 않은 보관소
     */
    private fun newCounts(): ListingChildCounts =
        ListingChildCounts(countChildren = { fileInfoUseCase.countChildren(it.toDomain()) }, permits = permits, scope = viewModelScope)

    /**
     * 목록 조회 요청 하나에 묶인 [ChildCountSource]. 같은 요청이면 서로 같아(equals) 폴더 행이 구독을 다시 시작하지 않는다.
     * @param listing 묶인 목록 조회 요청
     * @param owner 결과를 보관하는 ViewModel
     */
    private data class ListingSource(val listing: ListingKey, val owner: ChildCountViewModel) : ChildCountSource {

        override fun currentOf(directory: FileHolderItem): ChildCountInfo = owner.currentOf(listing, directory)

        override fun countOf(directory: FileHolderItem): Flow<ChildCountInfo> = owner.countOf(listing, directory)
    }
}
