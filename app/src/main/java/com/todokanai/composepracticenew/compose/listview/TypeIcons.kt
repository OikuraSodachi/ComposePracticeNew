package com.todokanai.composepracticenew.compose.listview

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.ui.model.FileType

/** 항목 종류별 placeholder 아이콘 리소스. */
@DrawableRes
private fun FileType.iconRes(): Int = when (this) {
    FileType.Folder -> R.drawable.ic_baseline_folder_24
    FileType.Image -> R.drawable.ic_file_image
    FileType.Video -> R.drawable.ic_file_video
    FileType.Audio -> R.drawable.ic_file_audio
    FileType.Document -> R.drawable.ic_file_document
    FileType.Pdf -> R.drawable.ic_pdf
    FileType.Archive -> R.drawable.ic_file_archive
    FileType.Apk -> R.drawable.ic_file_apk
    FileType.Other -> R.drawable.ic_baseline_insert_drive_file_24
}

/**
 * 항목 종류별 placeholder 아이콘 묶음을 만든다. 목록 밖에서 한 번 불러 모든 행이 같은 인스턴스를 공유하게 한다.
 * @return 종류별 아이콘. 테마·밀도가 바뀌지 않는 한 같은 인스턴스다
 */
@Composable
fun rememberTypeIcons(): Map<FileType, Painter> {
    // 행마다가 아니라 여기서 종류별로 한 번씩 호출 — 행마다 Painter를 만들지 않고 모든 행이 공유한다
    val typePainters = FileType.entries.map { painterResource(it.iconRes()) }
    // Painter 인스턴스들을 키로 — painterResource는 리컴포지션마다 같은 Painter를 돌려주므로 묶음도 같은 인스턴스로 유지되어,
    // 행이 Strong Skipping의 인스턴스 비교로 생략된다. 테마·밀도 변경으로 Painter가 바뀔 때만 다시 묶는다
    return remember(*typePainters.toTypedArray()) { FileType.entries.zip(typePainters).toMap() }
}
