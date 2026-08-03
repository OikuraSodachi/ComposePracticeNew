package com.todokanai.composepracticenew.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R

@Composable
fun StorageMenuButtons(
    modifier: Modifier,
    onAddRemoteStorage: () -> Unit,
    exit: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { expanded = true }
            ) {
                Text(stringResource(R.string.btn_button1))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_add_remote_storage)) },
                    onClick = {
                        expanded = false
                        onAddRemoteStorage()
                    }
                )
            }
        }

        TextButton(
            modifier = Modifier.weight(1f),
            onClick = { exit() }
        ) {
            Text(stringResource(R.string.btn_exit))
        }
    }
}

@Preview
@Composable
private fun StorageMenuButtonsPreview() {
    StorageMenuButtons(
        modifier = Modifier,
        onAddRemoteStorage = {},
        exit = {}
    )
}
