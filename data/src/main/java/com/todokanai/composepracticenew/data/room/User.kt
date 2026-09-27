package com.todokanai.composepracticenew.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room 데이터베이스의 사용자 엔티티.
 * @param number 사용자 번호
 * @param dummy 예비 필드
 */
@Entity(tableName = "room_user")
data class User(
    @ColumnInfo val number : Long?,
    @ColumnInfo val dummy : Int = 0
) {
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo
    var no : Long? = null

}
