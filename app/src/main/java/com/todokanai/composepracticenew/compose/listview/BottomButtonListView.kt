package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.compose.BottomButtons
import com.todokanai.composepracticenew.compose.ConfirmButtons
import com.todokanai.composepracticenew.myobjects.Constants
import kotlinx.coroutines.flow.StateFlow
import java.io.File

@Composable
fun BottomButtonListView(
    modifier: Modifier,
    selectModeFlow: StateFlow<Int>,
    zipDialog:()->Unit,
    renameDialog:()->Unit,
    infoDialog:()->Unit,
    moveMode:()->Unit,
    copyMode:()->Unit,
    unzipMode:()->Unit,
    unzipHereMode:()->Unit,
    delete:()->Unit,
    cancel:()->Unit,
    confirm: ()->Unit,
    selectedList:List<File>
){

    val selectMode = selectModeFlow.collectAsStateWithLifecycle()

    when (selectMode.value) {
        Constants.MULTI_SELECT_MODE -> {
            BottomButtons(
                modifier = modifier,
                move = { moveMode() },
                copy = { copyMode() },
                delete = { delete() },
                zip = { zipDialog() },
                unzip = { unzipMode() },
                unzipHere = {unzipHereMode()},
                rename = { renameDialog() },
                info = {infoDialog()},
                selectedList = selectedList
            )
        }
        Constants.CONFIRM_MODE_MOVE -> {
            ConfirmButtons(
                modifier = modifier,
                confirm = { confirm() },
                cancel = { cancel() },
                mode = Constants.CONFIRM_MODE_MOVE
            )
        }
        Constants.CONFIRM_MODE_COPY -> {
            ConfirmButtons(
                modifier = modifier,
                confirm = { confirm() },
                cancel = { cancel() },
                mode = Constants.CONFIRM_MODE_COPY
            )
        }
        Constants.CONFIRM_MODE_UNZIP, Constants.CONFIRM_MODE_UNZIP_HERE -> {
            ConfirmButtons(
                modifier = modifier,
                confirm = { confirm() },
                cancel = { cancel() },
                mode = Constants.CONFIRM_MODE_UNZIP
            )
        }
    }
}