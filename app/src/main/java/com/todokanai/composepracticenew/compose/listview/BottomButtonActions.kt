package com.todokanai.composepracticenew.compose.listview

import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.SelectionSummary
import com.todokanai.composepracticenew.viewmodel.SubmittedSelection

/**
 * 로컬 화면 하단 버튼이 쓰는 전송 콜백 묶음.
 * @param onConfirmDownload 다운로드 확인 콜백 — 선택 초기화는 실제 실행 시점에 coordinator가 담당한다
 * @param onEnterUploadMode 업로드 모드 진입 콜백
 */
class LocalTransferActions(
    val onConfirmDownload: () -> Unit,
    val onEnterUploadMode: () -> Unit,
)

/**
 * 압축 다이얼로그가 쓰는 콜백 묶음. 압축 파일을 만들 폴더는 다이얼로그를 열 때 [folderOf]로 한 번 정해 검증과 실행에 함께 넘긴다.
 * @param folderOf 압축 대상으로 압축 파일을 만들 폴더를 정하는 함수. 정할 수 없으면 null이며, 그때는 압축 메뉴를 두지 않는다
 * @param onCompress 선택에서 꺼낸 압축 대상, 만들 폴더, 이름(확장자 제외)으로 압축을 실행하는 콜백
 * @param suggestName 압축 대상으로 다이얼로그에 미리 채울 이름(확장자 제외)을 만드는 함수
 * @param validateName 만들 폴더와 압축 파일 이름(확장자 제외)을 검증해 위반한 규칙 집합을 반환하는 함수
 */
class CompressActions(
    val folderOf: (List<FileHolderItem>) -> String?,
    val onCompress: (SubmittedSelection, String, String) -> Unit,
    val suggestName: (List<FileHolderItem>) -> String,
    val validateName: (String, String) -> Set<FileNameError>,
)

/**
 * 이름 변경 다이얼로그가 쓰는 콜백 묶음. 로컬·원격 화면이 함께 쓴다.
 * @param onRename 선택에서 꺼낸 항목 하나와 새 이름으로 이름 변경을 실행하는 콜백
 * @param validate 대상 항목과 새 이름을 검증해 위반한 규칙 집합을 반환하는 함수(같은 폴더의 종류가 다른 같은 이름 항목 포함)
 * @param onConfirmOverwrite 이름 변경 덮어쓰기(합치기) 확인 콜백
 * @param onCancelOverwrite 이름 변경 덮어쓰기 취소 콜백
 */
class RenameActions(
    val onRename: (SubmittedSelection, String) -> Unit,
    val validate: (FileHolderItem, String) -> Set<FileNameError>,
    val onConfirmOverwrite: () -> Unit,
    val onCancelOverwrite: () -> Unit,
)

/**
 * 선택 항목 정보 다이얼로그가 쓰는 표시 값과 콜백 묶음.
 * @param summary 다이얼로그에 표시할 선택 요약을 반환하는 함수(null이면 다이얼로그를 표시하지 않음) — 다이얼로그 호스트에서만 읽는다
 * @param onShow 선택 항목의 정보 다이얼로그를 여는 콜백
 * @param onDismiss 정보 다이얼로그를 닫는 콜백
 */
class SelectionInfoActions(
    val summary: () -> SelectionSummary?,
    val onShow: (List<FileHolderItem>) -> Unit,
    val onDismiss: () -> Unit,
)
