package com.todokanai.composepracticenew.compose.holder

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todokanai.composepracticenew.ui.model.DirectoryItem

@Composable
fun DirectoryHolder(
    modifier: Modifier,
    pathName: DirectoryItem
) {
    Row(
        modifier = modifier
            .wrapContentWidth()
            .fillMaxHeight()
    ) {
        Text(
            text = "/${pathName.name}",
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .align(Alignment.CenterVertically),
            fontSize = 14.sp
        )
    }


}
