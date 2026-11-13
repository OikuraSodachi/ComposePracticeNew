package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult

/**
 * 항목 하나짜리 다운로드·업로드·삭제 묶음의 결과를 그 항목의 결과로 되돌린다. 묶음은 항목마다의 실패를 항목 이름 아래 모으므로([IoResult.PartiallyFailed]),
 * 항목 하나의 규칙(서버 거부·로컬 실패·완료 확인 실패)을 검증하는 테스트가 항목의 결과를 그대로 비교하도록 쓴다.
 * 실패한 항목이 하나가 아니면 바꾸지 않고 돌려준다 — 비교하는 쪽이 어긋남을 알리도록.
 */
internal fun IoResult<Unit>.singleItemResult(): IoResult<Unit> =
    if (this is IoResult.PartiallyFailed && failures.size == 1) IoResult.ItemFailed(failures.single().reason) else this
