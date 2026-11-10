package com.todokanai.composepracticenew.compose.frag

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.dialog.FileNameDialog
import com.todokanai.composepracticenew.compose.dialog.SortDialog
import com.todokanai.composepracticenew.compose.holder.DirectoryHolder
import com.todokanai.composepracticenew.compose.presets.dropdownmenu.MyDropdownMenu
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.composepracticenew.ui.model.DirectoryItem

/**
 * 파일 목록 위의 옵션 영역. 상단 버튼 바, 경로 표시줄, 새 폴더·정렬 다이얼로그를 조합한다. 로컬·원격 화면이 공통으로 사용한다.
 * @param modifier 레이아웃 수정자
 * @param navigateToStorage 저장소 선택 화면으로 이동하는 콜백
 * @param dirTree 경로 표시줄에 보여줄 현재 경로의 디렉터리 목록
 * @param onDirClick 경로 표시줄 항목 클릭 콜백
 * @param sortMode 현재 정렬 기준
 * @param onSortModeSelected 정렬 기준 선택 콜백
 * @param newFolderTarget 새 폴더를 만들 현재 폴더. null이면 만들 곳이 없어 새 폴더 메뉴를 두지 않는다
 * @param onNewFolder 다이얼로그를 연 때의 대상 폴더와 입력한 이름으로 새 폴더를 만드는 콜백
 * @param suggestFolderName 새 폴더 다이얼로그에 미리 채울 이름을 만드는 함수
 * @param validateFolderName 대상 폴더와 새 폴더 이름을 검증해 위반한 규칙 집합을 반환하는 함수
 * @param onExit 앱 종료 콜백
 * @param onSearchClick 검색 버튼 클릭 콜백. null이면 검색 버튼을 두지 않는다(원격 화면, 다중 선택 중)
 */
@Composable
fun OptionFrag(
    modifier: Modifier,
    navigateToStorage: () -> Unit,
    dirTree: List<DirectoryItem>,
    onDirClick: (DirectoryItem) -> Unit,
    sortMode: SortMode,
    onSortModeSelected: (SortMode) -> Unit,
    newFolderTarget: String?,
    onNewFolder: (String, String) -> Unit,
    suggestFolderName: () -> String,
    validateFolderName: (String, String) -> Set<FileNameError>,
    onExit: () -> Unit,
    onSearchClick: (() -> Unit)? = null,
) {
    // 화면 단위 ViewModel에 보관된 다이얼로그 상태 — 회전 시 유지, 화면 이동·프로세스 종료 후 복귀 시 닫힘(rememberOptionDialogState 참고)
    val dialogState = rememberOptionDialogState()
    /** 새 폴더 메뉴 클릭 콜백. 대상 폴더가 없으면 null이라 메뉴에 두지 않는다. */
    // newFolderTarget·dialogState를 키로 — 대상 폴더가 바뀔 때만 새로 만들어, 메뉴 항목 목록의 remember가 리컴포지션마다 깨지지 않게 한다
    val onNewFolderClick = remember(newFolderTarget, dialogState) {
        newFolderTarget?.let { target -> { dialogState.openNewFolder(target) } }
    }

    OptionDialogHost(
        dialogState = dialogState,
        sortMode = sortMode,
        onSortModeSelected = onSortModeSelected,
        onNewFolder = onNewFolder,
        suggestFolderName = suggestFolderName,
        validateFolderName = validateFolderName,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        OptionTopBar(
            navigateToStorage = navigateToStorage,
            onNewFolderClick = onNewFolderClick,
            onSortClick = dialogState::openSort,
            onExit = onExit,
            onSearchClick = onSearchClick,
        )
        PathBreadcrumb(
            dirTree = dirTree,
            onDirClick = onDirClick,
        )
    }
}

/**
 * 저장소 이동 버튼, 검색 버튼, 더보기 메뉴(새 폴더·정렬·종료)를 보여주는 상단 버튼 바. 더보기 메뉴의 열림 상태는 이 컴포저블이 보유한다.
 * @param navigateToStorage 저장소 선택 화면으로 이동하는 콜백
 * @param onNewFolderClick 더보기 메뉴의 새 폴더 항목 클릭 콜백. null이면 새 폴더 항목을 두지 않는다
 * @param onSortClick 더보기 메뉴의 정렬 항목 클릭 콜백
 * @param onExit 더보기 메뉴의 종료 항목 클릭 콜백
 * @param onSearchClick 검색 버튼 클릭 콜백. null이면 검색 버튼을 두지 않는다
 */
@Composable
private fun OptionTopBar(
    navigateToStorage: () -> Unit,
    onNewFolderClick: (() -> Unit)?,
    onSortClick: () -> Unit,
    onExit: () -> Unit,
    onSearchClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        TextButton(
            onClick = { navigateToStorage() },
            modifier = Modifier
                .weight(1f)
        ) {
            Text(stringResource(R.string.btn_storage))
        }

        if (onSearchClick != null) {
            TextButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .weight(1f)
            ) {
                Text(stringResource(R.string.btn_search))
            }
        }

        // 키 없음 — 메뉴 열림 여부는 이 바가 화면에 있는 동안만 유지하는 일시 상태라 구성 변경 시 닫혀도 된다
        val moreButtonExpanded = remember { mutableStateOf(false) }
        TextButton(
            modifier = Modifier
                .weight(1f)
                .wrapContentSize(),
            onClick = { moreButtonExpanded.value = !moreButtonExpanded.value }
        ) {
            Text(stringResource(R.string.btn_more))

            val newFolderLabel = stringResource(R.string.btn_create_new_folder)
            val sortLabel = stringResource(R.string.btn_sort)
            val exitLabel = stringResource(R.string.btn_exit)
            // 문구 3개·콜백 3개를 키로 — List는 불안정 타입이라 인스턴스로 비교되므로, 값이 같으면 같은 목록을 넘겨 MyDropdownMenu가 스킵되게 한다
            val menuItems = remember(newFolderLabel, sortLabel, exitLabel, onNewFolderClick, onSortClick, onExit) {
                listOfNotNull(
                    onNewFolderClick?.let { Pair(newFolderLabel, it) },
                    Pair(sortLabel, onSortClick),
                    Pair(exitLabel, onExit)
                )
            }
            MyDropdownMenu(
                contents = menuItems,
                expanded = moreButtonExpanded
            )
        }
    }
}

/**
 * 현재 경로의 디렉터리를 가로로 나열하고, 항목을 누르면 그 디렉터리로 이동하게 하는 경로 표시줄.
 * 항목 사이의 구분은 경로 구분자가 아니라 화면 장식(화살표)으로 보여 준다 — 구분자는 저장소마다 다르기 때문이다.
 * @param dirTree 표시할 디렉터리 목록(루트부터 현재 디렉터리까지)
 * @param onDirClick 항목 클릭 콜백
 */
@Composable
private fun PathBreadcrumb(
    dirTree: List<DirectoryItem>,
    onDirClick: (DirectoryItem) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
    ) {
        itemsIndexed(dirTree, key = { _, item -> item.path }) { index, item ->
            Row(modifier = Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                // 첫 항목(루트) 앞에는 구분 표시를 두지 않는다
                if (index > 0) PathSeparator()
                DirectoryHolder(
                    modifier = Modifier
                        .clickable { onDirClick(item) },
                    pathName = item
                )
            }
        }
    }
}

/** 경로 표시줄의 항목 사이에 두는 장식용 구분 표시(누를 수 없다). */
@Composable
private fun PathSeparator() {
    Icon(
        imageVector = Icons.Filled.KeyboardArrowRight,
        contentDescription = null,
        modifier = Modifier.size(16.dp)
    )
}

/**
 * [dialogState]에 따라 새 폴더·정렬 다이얼로그를 조건부로 렌더링한다.
 * @param dialogState 다이얼로그 열림 상태 홀더
 * @param sortMode 정렬 다이얼로그에서 선택된 것으로 보여줄 현재 정렬 기준
 * @param onSortModeSelected 정렬 기준 선택 콜백
 * @param onNewFolder 대상 폴더와 입력한 이름으로 새 폴더를 만드는 콜백
 * @param suggestFolderName 새 폴더 다이얼로그에 미리 채울 이름을 만드는 함수
 * @param validateFolderName 대상 폴더와 새 폴더 이름을 검증해 위반한 규칙 집합을 반환하는 함수
 */
@Composable
private fun OptionDialogHost(
    dialogState: OptionDialogState,
    sortMode: SortMode,
    onSortModeSelected: (SortMode) -> Unit,
    onNewFolder: (String, String) -> Unit,
    suggestFolderName: () -> String,
    validateFolderName: (String, String) -> Set<FileNameError>,
) {
    dialogState.newFolderTarget?.let { target ->
        // openId를 키로 — 연 회차마다 입력 상태(rememberSaveable)를 분리해, 화면 이동·프로세스 종료 때 저장된 이전 입력값이
        // 새로 연 다이얼로그에 복원되지 않게 한다. 회전 시에는 openId가 그대로라 입력값이 유지된다
        key(dialogState.openId) {
            // key(openId) 안에서 키 없이 remember — 연 회차마다 한 번만 제안한다. 입력란은 첫 구성의 값만 쓰므로 리컴포지션마다 목록을 다시 훑지 않는다
            val suggestedName = remember { suggestFolderName() }
            FileNameDialog(
                title = stringResource(R.string.dialog_new_folder_title),
                hint = stringResource(R.string.dialog_new_folder_hint),
                initialText = suggestedName,
                validate = { name -> validateFolderName(target, name) },
                onConfirm = { name -> onNewFolder(target, name) },
                onDismiss = dialogState::dismissNewFolder,
            )
        }
    }
    if (dialogState.sortDialog) {
        SortDialog(
            selected = sortMode,
            onSelect = onSortModeSelected,
            onDismiss = dialogState::dismissSort
        )
    }
}
