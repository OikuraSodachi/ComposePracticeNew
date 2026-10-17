package com.todokanai.composepracticenew.compose.holder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.ui.model.StorageHolderItem

@Composable
fun StorageHolder(
    modifier : Modifier,
    storage : StorageHolderItem
){

    val progress = storage.progress
    val total = storage.total
    val used = storage.used

    // 단일 Column으로 구성 — 제약 없는 ConstraintLayout 래퍼는 레이아웃 패스만 추가하므로 제거
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(22.dp)
    ) {
        Row(
            modifier = Modifier
                .height(30.dp)
        ) {
            Text(
                modifier = Modifier
                    .weight(1f),
                text = storage.absolutePath
            )

            Text(
                modifier = Modifier
                    .weight(1f),
                text = "${used}/${total}",
                textAlign = TextAlign.End
            )
        }
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            progress = progress,
            trackColor = Color.LightGray,
            color = Color.Red
        )
    }

}
