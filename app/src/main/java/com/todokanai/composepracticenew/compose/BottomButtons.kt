package com.todokanai.composepracticenew.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dropdownmenu.MyDropdownMenu
import com.todokanai.composepracticenew.ui.model.FileHolderItem

private fun buildDropdownItems(
    selectedList: List<FileHolderItem>,
    canUnzip: (FileHolderItem) -> Boolean,
    strZip: String,
    strInfo: String,
    strUpload: String,
    strRename: String,
    strUnzip: String,
    strUnzipHere: String,
    zip: () -> Unit,
    info: () -> Unit,
    upload: () -> Unit,
    rename: () -> Unit,
    unzip: () -> Unit,
    unzipHere: () -> Unit,
): List<Pair<String, () -> Unit>> =
    mutableListOf(Pair(strZip, zip), Pair(strInfo, info), Pair(strUpload, upload))
        .apply {
            if (selectedList.size == 1) {
                add(Pair(strRename, rename))
                if (canUnzip(selectedList.first())) {
                    add(Pair(strUnzip, unzip))
                    add(Pair(strUnzipHere, unzipHere))
                }
            }
        }

/**
 * 멀티셀렉트 모드에서 이동·복사·삭제·더보기(압축·정보·업로드·이름변경·압축해제) 버튼을 제공한다.
 * @param canUnzip 항목을 압축 해제할 수 있는지 판별하는 함수 — 하나만 선택했을 때 압축 해제 버튼 표시 여부를 정한다
 */
@Composable
fun BottomButtons(
    modifier: Modifier,
    move:()->Unit,
    copy:()->Unit,
    delete:()->Unit,
    zip:()->Unit,
    unzip:()->Unit,
    unzipHere:()->Unit,
    rename:()->Unit,
    info:()->Unit,
    upload:()->Unit,
    selectedList: List<FileHolderItem>,
    canUnzip: (FileHolderItem) -> Boolean
) {
    val strMove = stringResource(R.string.btn_move)
    val strCopy = stringResource(R.string.btn_copy)
    val strDelete = stringResource(R.string.btn_delete)
    val strMore = stringResource(R.string.btn_more)
    val strZip = stringResource(R.string.btn_zip)
    val strInfo = stringResource(R.string.btn_info)
    val strUpload = stringResource(R.string.btn_upload)
    val strRename = stringResource(R.string.btn_rename)
    val strUnzip = stringResource(R.string.btn_unzip)
    val strUnzipHere = stringResource(R.string.btn_unzip_here)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        TextButton(
            onClick = move,
            Modifier.weight(1f)
        ) {
            Text(strMove)
        }
        TextButton(
            onClick = copy,
            Modifier.weight(1f)
        ) {
            Text(strCopy)
        }
        TextButton(
            onClick = delete,
            Modifier.weight(1f)
        ) {
            Text(strDelete)
        }
        // selectedList·canUnzip을 키로 — 선택이 바뀌거나 판별 출처가 바뀔 때만 메뉴 항목을 다시 만든다
        val dropdownContents = remember(selectedList, canUnzip) {
            buildDropdownItems(selectedList, canUnzip, strZip, strInfo, strUpload, strRename, strUnzip, strUnzipHere, zip, info, upload, rename, unzip, unzipHere)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .wrapContentSize()
        ) {
            val expanded = remember { mutableStateOf(false) }
            TextButton(
                modifier = Modifier,
                onClick = { expanded.value = !expanded.value }
            ) {
                Text(strMore)
                MyDropdownMenu(
                    contents = dropdownContents,
                    expanded = expanded
                )
            }
        }
    }
}