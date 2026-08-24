package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.ui.model.RemoteStorageItem

/** 원격 스토리지 연결 정보(이름, 주소, 포트, 아이디, 비밀번호, 인코딩)를 입력받아 추가하거나 수정하는 다이얼로그. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRemoteStorageDialog(
    onConfirm: (name: String, address: String, port: Int, id: String, password: String, encoding: String) -> Unit,
    onCancel: () -> Unit,
    initialValue: RemoteStorageItem? = null
) {
    val encodingOptions = listOf("UTF-8", "EUC-KR", "ISO-8859-1")
    var name by remember { mutableStateOf(initialValue?.name ?: "") }
    var address by remember { mutableStateOf(initialValue?.address ?: "") }
    var port by remember { mutableStateOf(initialValue?.port?.toString() ?: "") }
    var id by remember { mutableStateOf(initialValue?.userId ?: "") }
    var password by remember { mutableStateOf(initialValue?.password ?: "") }
    var selectedEncoding by remember { mutableStateOf(initialValue?.encoding ?: "UTF-8") }
    var encodingExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.dialog_add_remote_storage_title)) },
        text = {
            Column {
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    placeholder = { Text(stringResource(R.string.dialog_add_remote_storage_name_hint)) },
                    onValueChange = { name = it },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = address,
                    placeholder = { Text(stringResource(R.string.dialog_add_remote_storage_address_hint)) },
                    onValueChange = { address = it },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = port,
                    placeholder = { Text(stringResource(R.string.dialog_add_remote_storage_port_hint)) },
                    onValueChange = { port = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = id,
                    placeholder = { Text(stringResource(R.string.dialog_add_remote_storage_id_hint)) },
                    onValueChange = { id = it },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = password,
                    placeholder = { Text(stringResource(R.string.dialog_add_remote_storage_pw_hint)) },
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = encodingExpanded,
                    onExpandedChange = { encodingExpanded = it }
                ) {
                    TextField(
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        value = selectedEncoding,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.dialog_add_remote_storage_encoding_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = encodingExpanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = encodingExpanded,
                        onDismissRequest = { encodingExpanded = false }
                    ) {
                        encodingOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedEncoding = option
                                    encodingExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name, address, port.toIntOrNull() ?: 0, id, password, selectedEncoding)
                    onCancel()
                }
            ) {
                Text(stringResource(R.string.btn_confirm))
            }
        },
        dismissButton = {
            Button(onClick = onCancel) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Preview
@Composable
private fun AddRemoteStorageDialogPreview() {
    AddRemoteStorageDialog(
        onConfirm = { _, _, _, _, _, _ -> },
        onCancel = {}
    )
}
