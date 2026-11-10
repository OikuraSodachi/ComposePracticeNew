package com.todokanai.composepracticenew.compose

import androidx.annotation.StringRes
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

/**
 * 하단 버튼이나 더보기 메뉴의 항목 하나.
 * @param labelRes 표시할 문자열 리소스
 * @param onClick 눌렀을 때 실행할 동작
 */
class BottomButtonAction(@StringRes val labelRes: Int, val onClick: () -> Unit)

/**
 * 주어진 항목을 하단 버튼과 더보기 메뉴로 그린다. 어떤 항목을 언제 보일지는 호출부가 정한다.
 * @param primaryActions 하단에 바로 보이는 버튼 항목
 * @param moreActions 더보기 메뉴의 항목
 */
@Composable
fun BottomButtons(
    modifier: Modifier,
    primaryActions: List<BottomButtonAction>,
    moreActions: List<BottomButtonAction>,
) {
    val strMore = stringResource(R.string.btn_more)
    val dropdownContents = moreActions.map { stringResource(it.labelRes) to it.onClick }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        primaryActions.forEach { action ->
            TextButton(
                onClick = action.onClick,
                Modifier.weight(1f)
            ) {
                Text(stringResource(action.labelRes))
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