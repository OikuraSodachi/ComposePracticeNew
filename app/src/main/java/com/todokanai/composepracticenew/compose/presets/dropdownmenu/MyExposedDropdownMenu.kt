package com.todokanai.composepracticenew.compose.presets.dropdownmenu

import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.tooling.preview.Preview

/** [contents] 중 하나를 고르는 펼침 메뉴. 고른 항목을 입력란에 보이고 [onItemSelect]로 알린다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyExposedDropdownMenu(
    modifier: Modifier,
    contents: List<String>,
    onItemSelect: (item:String)->Unit
) {
    // 키 없이 remember — 메뉴 열림 여부는 이 컴포지션 동안만 유지한다(구성 변경 시 닫힌다)
    var expanded by remember { mutableStateOf(false) }
    // 키 없이 remember — 처음에는 contents[0]이고 이후 고른 항목을 유지한다(contents가 바뀌어도 다시 정하지 않는다)
    var selectedText by remember { mutableStateOf(contents[0]) }

        ExposedDropdownMenuBox(
            modifier = modifier
                .wrapContentSize(),
            expanded = expanded,
            onExpandedChange = {
                expanded = !expanded
            }
        ) {
            TextField(
                value = selectedText,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                contents.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(text = item) },
                        onClick = {
                            selectedText = item
                            expanded = false
                            onItemSelect(item)

                        }
                    )
                }
            }
        }

}

/** MyExposedDropdownMenu 모양을 확인하는 프리뷰. */
@Preview
@Composable
private fun MyExposedDropdownMenuPreview(){
    MyExposedDropdownMenu(
        modifier = Modifier,
        contents = listOf("1","2","3"),
        onItemSelect = {}
    )
}
