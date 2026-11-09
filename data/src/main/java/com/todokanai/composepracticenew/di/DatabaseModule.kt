package com.todokanai.composepracticenew.di

import android.content.Context
import androidx.room.Room
import com.todokanai.composepracticenew.data.room.MIGRATION_3_4
import com.todokanai.composepracticenew.data.room.MyDatabase
import com.todokanai.composepracticenew.data.room.RemoteStorageInfoDao
import com.todokanai.composepracticenew.data.room.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class DatabaseModule {

    @Singleton
    @Provides
    fun provideMyDatabase(@ApplicationContext context: Context): MyDatabase {
        return Room.databaseBuilder(context, MyDatabase::class.java, "room_db")
            .addMigrations(MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideUserDao(myDatabase: MyDatabase) : UserDao {
        return myDatabase.userDao()
    }

    @Provides
    fun provideRemoteStorageInfoDao(myDatabase: MyDatabase) : RemoteStorageInfoDao {
        return myDatabase.remoteStorageInfoDao()
    }
}
