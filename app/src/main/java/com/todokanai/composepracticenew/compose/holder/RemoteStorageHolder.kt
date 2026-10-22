package com.todokanai.composepracticenew.compose.holder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todokanai.composepracticenew.ui.model.RemoteStorageItem

/**
 * 원격 스토리지 목록의 각 항목을 이름·주소·수정·삭제 버튼과 함께 표시하는 composable.
 * @param modifier 레이아웃 수정자(항목 클릭 처리 포함)
 * @param item 표시할 원격 스토리지
 * @param enabled false이면 수정·삭제 버튼을 비활성화한다 — 항목 자체의 클릭은 [modifier]를 넘기는 쪽이 같은 조건으로 막는다
 * @param onEdit 수정 버튼 클릭 콜백
 * @param onDelete 삭제 버튼 클릭 콜백
 */
@Composable
fun RemoteStorageHolder(
    modifier: Modifier,
    item: RemoteStorageItem,
    enabled: Boolean = true,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .padding(start = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = item.address,
                fontSize = 13.sp
            )
        }
        IconButton(onClick = onEdit, enabled = enabled) {
            Icon(imageVector = Icons.Default.Edit, contentDescription = "수정")
        }
        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "삭제")
        }
    }
}

@Preview
@Composable
private fun RemoteStorageHolderPreview() {
    RemoteStorageHolder(
        modifier = Modifier,
        item = RemoteStorageItem(
            name = "내 NAS",
            address = "smb://192.168.0.1/share",
            port = 445,
            userId = "user",
            password = ""
        ),
        onEdit = {},
        onDelete = {}
    )
}
