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
    selectedList: List<FileHolderItem>
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
            onClick = { move() },
            Modifier.weight(1f)
        ) {
            Text(strMove)
        }
        TextButton(
            onClick = { copy() },
            Modifier.weight(1f)
        ) {
            Text(strCopy)
        }
        TextButton(
            onClick = { delete() },
            Modifier.weight(1f)
        ) {
            Text(strDelete)
        }
        val dropdownContents = remember(selectedList) {
            mutableListOf(Pair(strZip) { zip() }, Pair(strInfo) { info() }, Pair(strUpload) { upload() })
                .apply {
                    if (selectedList.size == 1) {
                        val selected = selectedList.first()
                        add(Pair(strRename) { rename() })
                        if (selected.name.substringAfterLast('.', "") == "zip") {
                            add(Pair(strUnzip) { unzip() })
                            add(Pair(strUnzipHere) { unzipHere() })
                        }
                    }
                }
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