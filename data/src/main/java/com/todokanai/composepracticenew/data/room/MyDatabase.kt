package com.todokanai.composepracticenew.data.room

import androidx.room.Database
import androidx.room.RoomDatabase

/** 앱의 Room 데이터베이스. 인스턴스는 DatabaseModule이 앱에 하나만 만든다. */
@Database(entities = [User::class, RemoteStorageInfo::class], version = 4, exportSchema = false)
abstract class MyDatabase : RoomDatabase(){

    abstract fun userDao() : UserDao

    abstract fun remoteStorageInfoDao() : RemoteStorageInfoDao
}
