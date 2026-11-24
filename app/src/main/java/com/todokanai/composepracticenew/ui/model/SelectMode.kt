package com.todokanai.composepracticenew.ui.model

import androidx.annotation.StringRes
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.model.ArchiveEntrySelection

/**
 * 파일 목록 화면의 선택 모드.
 * 화면별로 가능한 모드는 [LocalSelectMode] / [RemoteSelectMode]로, 확인 버튼을 갖는 모드는 [ConfirmMode]로 타입을 좁혀
 * 해당 화면·함수가 처리할 수 없는 모드 조합을 컴파일 단계에서 배제한다.
 */
sealed interface SelectMode {

    /** 일반 탐색 모드. 두 화면 공통. */
    data object Default : LocalSelectMode, RemoteSelectMode

    /** 체크박스 다중 선택 모드. 두 화면 공통. */
    data object MultiSelect : LocalSelectMode, RemoteSelectMode

    /**
     * 로컬 화면에서 목적지로 이동한 뒤 확인 버튼으로 실행하는 로컬 파일 작업 모드. 실행 전 이름 충돌을 검사한다.
     * @param labelRes 확인 버튼과, 다중 선택에서 이 모드로 들어가는 버튼에 함께 표시할 문자열 리소스. 두 버튼의 문구를 다르게 하려면 진입 버튼에 별도 리소스를 둔다
     */
    sealed class LocalOperation(@StringRes override val labelRes: Int) : LocalSameStorageOperation {
        /** 복사 목적지 탐색 후 확인하는 모드. */
        data object Copy : LocalOperation(R.string.btn_copy)
        /** 이동 목적지 탐색 후 확인하는 모드. */
        data object Move : LocalOperation(R.string.btn_move)
        /** 압축 해제 목적지 탐색 후 확인하는 모드. */
        data object Unzip : LocalOperation(R.string.btn_unzip)
        /** 현재 위치에 압축 해제 후 확인하는 모드. */
        data object UnzipHere : LocalOperation(R.string.btn_unzip_here)
    }

    /**
     * 로컬 화면에서 고른 항목을 넣을 ZIP 압축 파일을 찾는 모드. 압축 파일을 누르면 내용 보기로 가서, 넣을 폴더에서 확인한다 —
     * 확인은 압축 파일 안의 폴더에서 하므로 로컬 목록에는 확인 버튼이 없다. 확인 버튼 문구([labelRes])는 내용 보기의 것이다.
     */
    data object AddToArchive : LocalSameStorageOperation {
        @StringRes override val labelRes: Int = R.string.btn_add_here
    }

    /**
     * 로컬 화면에서 다운로드 목적지를 탐색한 뒤 확인하는 모드.
     * @param pending 원격 화면에서 고른 다운로드 대기 항목
     */
    data class Download(val pending: List<FileHolderItem>) : LocalTransferMode {
        @StringRes override val labelRes: Int = R.string.btn_download
    }

    /**
     * 로컬 화면에서 압축 파일 내용 보기에서 고른 항목을 풀 목적지를 탐색한 뒤 확인하는 모드.
     * @param selection 내용 보기에서 고른 압축 파일 항목
     */
    data class ArchiveCopy(val selection: ArchiveEntrySelection) : LocalTransferMode {
        @StringRes override val labelRes: Int = R.string.btn_copy_here
    }

    /**
     * 원격 화면에서 업로드 목적지를 탐색한 뒤 확인하는 모드.
     * @param pending 로컬 화면에서 고른 업로드 대기 항목
     */
    data class Upload(val pending: List<FileHolderItem>) : RemoteSelectMode, TransferMode {
        @StringRes override val labelRes: Int = R.string.btn_upload_confirm
    }
}

/** 로컬 파일 목록 화면에서 가능한 선택 모드. */
sealed interface LocalSelectMode : SelectMode

/** 로컬 화면에서 선택을 유지한 채 목적지를 고르는 같은 저장소 작업. 확인하는 곳이 작업마다 다르다(로컬 목록의 지금 폴더, 압축 파일 안의 폴더). */
sealed interface LocalSameStorageOperation : LocalSelectMode, SameStorageOperation

/** 로컬 화면이 목적지가 되는 전송 모드. */
sealed interface LocalTransferMode : LocalSelectMode, TransferMode

/**
 * 이 모드에서 로컬 항목 [item]을 눌렀을 때 할 일을 정한다. 목록 행과 검색 결과가 같은 규칙을 따르도록 한 곳에 둔다.
 * 기본 모드에서는 모두 열고, 다중 선택 중에는 선택을 토글하며, 목적지 확인 중에는 목적지를 찾아 들어갈 폴더만 연다.
 * 압축 파일에 추가할 곳을 찾는 중에는 폴더와 함께 압축 파일도 연다(내용 보기로).
 * @param item 누른 항목
 * @return 누른 항목에 할 일
 */
fun LocalSelectMode.clickActionOf(item: FileHolderItem): ItemClickAction = when (this) {
    SelectMode.Default -> ItemClickAction.OPEN
    SelectMode.MultiSelect -> ItemClickAction.TOGGLE
    is SelectMode.LocalOperation, is SelectMode.Download, is SelectMode.ArchiveCopy -> opensFolderOnly(item)
    SelectMode.AddToArchive -> if (item.isDirectory) ItemClickAction.OPEN else ItemClickAction.OPEN_ARCHIVE
}

/** 원격 파일 목록 화면에서 가능한 선택 모드. */
sealed interface RemoteSelectMode : SelectMode

/**
 * 이 모드에서 원격 항목 [item]을 눌렀을 때 할 일을 정한다. 다중 선택 중에는 선택을 토글하고, 그 밖에는 폴더만 연다
 * (원격 파일 열기는 먼저 내려받아야 하는 별도 기능이며 지원 범위 밖이다).
 * @param item 누른 항목
 * @return 누른 항목에 할 일
 */
fun RemoteSelectMode.clickActionOf(item: FileHolderItem): ItemClickAction = when (this) {
    SelectMode.MultiSelect -> ItemClickAction.TOGGLE
    SelectMode.Default, is SelectMode.Upload -> opensFolderOnly(item)
}

/**
 * 이 모드에서 경로 표시줄을 눌러 다른 폴더로 이동하는지. 로컬·원격 화면이 같은 규칙을 따르도록 한 곳에 둔다.
 * 다중 선택은 지금 폴더 안에서만 하므로 이동하지 않고(#519 — 이동하면 선택이 보이지 않는 폴더의 항목을 담는다), 목적지를 찾는 모드는 이동한다.
 */
val SelectMode.allowsPathBarNavigation: Boolean
    get() = when (this) {
        SelectMode.MultiSelect -> false
        SelectMode.Default, is SelectMode.LocalOperation, SelectMode.AddToArchive, is SelectMode.Download, is SelectMode.ArchiveCopy, is SelectMode.Upload -> true
    }

/**
 * 폴더만 여는 모드에서 [item]에 할 일을 정한다.
 * @param item 누른 항목
 * @return 폴더면 열기, 파일이면 무시
 */
private fun opensFolderOnly(item: FileHolderItem): ItemClickAction =
    if (item.isDirectory) ItemClickAction.OPEN else ItemClickAction.IGNORE

/** 확인 버튼을 표시하는 선택 모드. */
sealed interface ConfirmMode : SelectMode {
    /** 확인 버튼에 표시할 문자열 리소스. */
    @get:StringRes
    val labelRes: Int
}

/** 선택한 항목과 같은 저장소에서 목적지를 고르는 작업 모드. 화면의 선택 목록이 작업 대상이다. */
sealed interface SameStorageOperation : ConfirmMode

/** 다른 화면에서 고른 항목을 받을 목적지를 고르는 전송 모드. 작업 대상은 이 화면의 선택이 아닌 전송 대기 목록이다. */
sealed interface TransferMode : ConfirmMode
