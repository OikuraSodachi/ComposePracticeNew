package com.todokanai.composepracticenew.compose.holder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.net.toUri
import com.todokanai.composepracticenew.compose.presets.image.ImageHolder
import com.todokanai.composepracticenew.data.dataclass.FileHolderItem


/** modifier.background 처리를 FileListView로 옮길것 ( FileHolder에서 myobjects.Constants 의존성 제거하기 ) */
@Composable
fun FileHolder(
    modifier: Modifier,
    file: FileHolderItem,
    isSelected:Boolean
) {



    ConstraintLayout(
        modifier = modifier
//            .background(if (isSelected && selectMode.value == Constants.MULTI_SELECT_MODE) Color.LightGray else Color.Transparent)
            .background(if (isSelected) Color.LightGray else Color.Transparent)
            .fillMaxWidth()
            .height(60.dp)
    ) {
        val (fileImage) = createRefs()
        val (fileName) = createRefs()
        val (fileDate) = createRefs()
        val (fileSize) = createRefs()

        ImageHolder(
            modifier = Modifier
                .constrainAs(fileImage) {
                    start.linkTo(parent.start)
                }
                .width(50.dp)
                .fillMaxHeight()
                .padding(5.dp),
            isAsyncImage = (file.file.extension == "jpg"),
            data = file.file.toUri(),
            icon = file.thumbnail
        )

        Text(
            file.size,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier
                .constrainAs(fileSize) {
                    end.linkTo(parent.end)
                    bottom.linkTo(parent.bottom)
                }
                .wrapContentWidth()
                .padding(4.dp)
        )

        Text(
            file.name,
            fontSize = 18.sp,
            maxLines = 1,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .constrainAs(fileName) {
                    start.linkTo(fileImage.end)
                    end.linkTo(fileSize.start)
                    top.linkTo(parent.top)
                    width = Dimension.fillToConstraints
                }
                .height(30.dp)
                .padding(4.dp)
        )

        Text(
            file.lastModified,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier
                .constrainAs(fileDate) {
                    start.linkTo(fileImage.end)
                    end.linkTo(fileSize.start)
                    bottom.linkTo(parent.bottom)
                    top.linkTo(fileName.bottom)
                    width = Dimension.fillToConstraints
                    height = Dimension.fillToConstraints
                }
                .padding(4.dp)
        )
    }


    println("Recomposition: FileHolder - ${file.name}")
}