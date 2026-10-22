package com.todokanai.composepracticenew.compose.presets.dropdownmenu

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * 문구와 동작 쌍의 목록을 항목으로 보여주는 드롭다운 메뉴.
 *
 * 항목을 눌러도 메뉴는 닫히지 않는다 — 의도된 동작이다. 항목 클릭은 그 항목의 동작만 실행하며,
 * 메뉴는 바깥 터치·뒤로가기([DropdownMenu]의 onDismissRequest)로만 닫힌다.
 * 항목 클릭 시 메뉴를 닫아야 하는 호출부는 그 항목의 동작에서 [expanded]를 직접 false로 바꾼다.
 *
 * @param contents 표시할 항목 목록. 각 쌍은 (항목 문구, 클릭 시 실행할 동작)
 * @param expanded 메뉴 열림 상태. 바깥 터치·뒤로가기 시 이 컴포저블이 false로 바꾼다
 */
@Composable
fun MyDropdownMenu(
    contents:List<Pair<String,()->Unit>>,
    expanded : MutableState<Boolean>
) {
    DropdownMenu(
        modifier = Modifier.wrapContentSize(),
        expanded = expanded.value,
        onDismissRequest = { expanded.value = false }
    ) {
        contents.forEach {
            DropdownMenuItem(
                text = { Text(it.first) },
                onClick = { it.second() }
            )
        }
    }
}

@Preview
@Composable
private fun DropdownMenuPreview(){
    MyDropdownMenu(
        contents = listOf(Pair("1", {}), Pair("2", {}),Pair("3",{})),
        expanded = remember {mutableStateOf(true)}
    )
}

