package com.todokanai.composepracticenew.compose.dialog

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.ConfirmCancelDialog
import com.todokanai.composepracticenew.model.RemoteStorageInputError
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.ui.model.RemoteStorageItem

/**
 * 원격 스토리지 연결 정보(이름, 주소, 포트, 아이디, 비밀번호, 인코딩)를 입력받아 추가하거나 수정하는 다이얼로그.
 *
 * 입력 검증 규칙은 [validate]로 주입받고, 이 다이얼로그는 결과에 따라 확인 버튼 활성 여부와 필드 오류 표시만 담당한다.
 * 필수 입력이 비어 있다는 오류는 사용자가 그 필드를 한 번이라도 편집한 뒤에만 표시해, 빈 추가 폼을 열자마자 오류가 보이지 않게 한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRemoteStorageDialog(
    onConfirm: (name: String, address: String, port: Int, id: String, password: String, encoding: String) -> Unit,
    onDismiss: () -> Unit,
    validate: (name: String, address: String, port: String) -> Set<RemoteStorageInputError>,
    initialValue: RemoteStorageItem? = null
) {
    val encodingOptions = Constants.REMOTE_ENCODING_OPTIONS
    // 입력 필드 6개는 rememberSaveable — 키 없음: 화면 회전 등 구성 변경 후에도 입력 중인 값을 보존하고, initialValue는 최초 진입 시에만 초기값으로 사용
    var name by rememberSaveable { mutableStateOf(initialValue?.name ?: "") }
    var address by rememberSaveable { mutableStateOf(initialValue?.address ?: "") }
    var port by rememberSaveable { mutableStateOf(initialValue?.port?.toString() ?: "21") }
    var id by rememberSaveable { mutableStateOf(initialValue?.userId ?: "") }
    var password by rememberSaveable { mutableStateOf(initialValue?.password ?: "") }
    var selectedEncoding by rememberSaveable { mutableStateOf(initialValue?.encoding ?: Constants.REMOTE_ENCODING_DEFAULT) }
    // 드롭다운 펼침은 일시적 UI 상태라 구성 변경 시 닫혀도 무방 — remember로 충분
    var encodingExpanded by remember { mutableStateOf(false) }
    // 필드별 편집 여부는 rememberSaveable — 키 없음: 구성 변경 후에도 이미 보이던 오류 표시가 사라지지 않도록 보존
    var nameEdited by rememberSaveable { mutableStateOf(false) }
    var addressEdited by rememberSaveable { mutableStateOf(false) }
    var portEdited by rememberSaveable { mutableStateOf(false) }

    // rememberUpdatedState — 아래 derivedStateOf가 재생성 없이 항상 최신 validate를 호출하도록 보장
    val currentValidate by rememberUpdatedState(validate)
    // name·address·port·currentValidate를 추적 — 입력이 바뀔 때만 재검증하고, 결과 집합이 같으면 본문을 무효화하지 않음
    val errors by remember { derivedStateOf { currentValidate(name, address, port) } }
    val nameError = nameEdited && RemoteStorageInputError.NAME_BLANK in errors
    val addressError = addressEdited && RemoteStorageInputError.ADDRESS_BLANK in errors
    val portError = portEdited && RemoteStorageInputError.PORT_OUT_OF_RANGE in errors

    ConfirmCancelDialog(
        modifier = Modifier,
        title = stringResource(R.string.dialog_add_remote_storage_title),
        onConfirm = {
            port.toIntOrNull()?.let { validPort -> onConfirm(name, address, validPort, id, password, selectedEncoding) }
        },
        onDismiss = onDismiss,
        confirmEnabled = errors.isEmpty()
    ) {
        Column {
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = name,
                placeholder = { Text(stringResource(R.string.dialog_add_remote_storage_name_hint)) },
                onValueChange = {
                    name = it
                    nameEdited = true
                },
                singleLine = true,
                isError = nameError,
                supportingText = errorSupportingText(nameError, R.string.error_remote_storage_name_required)
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = address,
                placeholder = { Text(stringResource(R.string.dialog_add_remote_storage_address_hint)) },
                onValueChange = {
                    address = it
                    addressEdited = true
                },
                singleLine = true,
                isError = addressError,
                supportingText = errorSupportingText(addressError, R.string.error_remote_storage_address_required)
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = port,
                placeholder = { Text(stringResource(R.string.dialog_add_remote_storage_port_hint)) },
                onValueChange = {
                    port = it
                    portEdited = true
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = portError,
                supportingText = errorSupportingText(portError, R.string.error_remote_storage_port_range)
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
    }
}

/** 오류일 때만 사유 문구를 보여주는 TextField supportingText 슬롯을 만든다. 오류가 아니면 슬롯을 두지 않는다. */
private fun errorSupportingText(isError: Boolean, @StringRes messageRes: Int): (@Composable () -> Unit)? =
    if (isError) ({ Text(stringResource(messageRes)) }) else null

/** AddRemoteStorageDialog의 빈 추가 폼 모양을 확인하는 프리뷰. */
@Preview
@Composable
private fun AddRemoteStorageDialogPreview() {
    AddRemoteStorageDialog(
        onConfirm = { _, _, _, _, _, _ -> },
        onDismiss = {},
        validate = { _, _, _ -> emptySet() }
    )
}
