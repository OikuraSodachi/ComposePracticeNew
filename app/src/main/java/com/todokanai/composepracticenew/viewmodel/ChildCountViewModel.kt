package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.usecase.FileInfoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.sync.Semaphore
import javax.inject.Inject

/** 동시에 세는 폴더 수의 상한. 화면에 보이는 폴더 행 수보다 작게 두어 디렉터리 읽기가 한꺼번에 몰리지 않게 한다. */
private const val MAX_CONCURRENT_COUNTS = 4

/**
 * 파일 목록의 폴더 행에 표시할 하위 항목 수를, 표시 중인 목록 조회 결과마다 [ListingChildCounts]로 묶어 제공한다.
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

    /** 마지막으로 내준 목록 조회 결과(항목 목록 인스턴스)와 그 하위 항목 수. */
    private var current: Pair<List<FileHolderItem>, ListingChildCounts>? = null

    /**
     * 목록 조회 결과 [items]의 하위 항목 수를 돌려준다. 같은 조회 결과(같은 목록 인스턴스)면 같은 인스턴스를 돌려줘 센 결과를 다시 쓰고,
     * 새 조회 결과면 새로 만들어 이전 목록의 결과를 버린다. 다시 조회하는 동안 보이는 직전 목록은 같은 인스턴스이므로 결과가 유지된다.
     * @param items 표시 중인 목록 조회 결과의 항목 목록
     * @return 그 목록의 하위 항목 수
     */
    fun countsFor(items: List<FileHolderItem>): ListingChildCounts {
        current?.let { (shownItems, counts) -> if (shownItems === items) return counts }
        return ListingChildCounts(countChildren = { fileInfoUseCase.countChildren(it.toDomain()) }, permits = permits)
            .also { current = items to it }
    }
}
