package com.todokanai.composepracticenew.data.room

import android.content.Context
import com.todokanai.composepracticenew.di.DatabaseModule
import com.todokanai.composepracticenew.model.RemoteProtocolSettings
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.RemoteStorageRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 버전 3 DB를 앱과 같은 조립([DatabaseModule])으로 열었을 때 저장된 서버 목록이 FTP 항목으로 보존되고, Room의 스키마 검증을 통과하는지 검증하는 테스트. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MyDatabaseMigrationTest {

    /** DB 파일을 만들 테스트용 Context. */
    private val context: Context = RuntimeEnvironment.getApplication()
    /** 테스트가 연 DB. 테스트가 끝나면 닫는다. */
    private var database: MyDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    @Test
    fun `버전 3의 서버 목록을 지우지 않고 FTP 항목으로 옮긴다`() {
        createVersion3Database()

        val items = runBlocking { repository().getAll().first() }

        assertEquals(
            listOf(RemoteStorageItem(id = 7, name = "nas", address = "192.168.0.1", port = 2121, userId = "u", password = "p", settings = RemoteProtocolSettings.Ftp("EUC-KR"))),
            items
        )
    }

    @Test
    fun `저장한 프로토콜 설정을 그대로 읽는다`() {
        val item = RemoteStorageItem(id = 3, name = "nas", address = "a", port = 21, userId = "u", password = "p", settings = RemoteProtocolSettings.Ftp("ISO-8859-1"))
        val repository = repository()

        val saved = runBlocking {
            repository.insert(item)
            repository.getById(3)
        }

        assertEquals(item, saved)
    }

    /**
     * 앱과 같은 조립으로 DB를 열어 저장소를 만든다.
     * @return 열린 DB를 쓰는 저장소
     */
    private fun repository(): RemoteStorageRepositoryImpl {
        val opened = DatabaseModule().provideMyDatabase(context).also { database = it }
        return RemoteStorageRepositoryImpl(opened.remoteStorageInfoDao())
    }

    /** 버전 3 스키마의 DB를 앱의 DB 파일 이름으로 만들고 서버 하나를 넣는다. */
    private fun createVersion3Database() {
        context.openOrCreateDatabase("room_db", Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE room_user (number INTEGER, dummy INTEGER NOT NULL, no INTEGER, PRIMARY KEY(no))")
            db.execSQL(
                "CREATE TABLE remote_storage_info (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, address TEXT NOT NULL, " +
                    "port INTEGER NOT NULL, userId TEXT NOT NULL, password TEXT NOT NULL, encoding TEXT NOT NULL)"
            )
            db.execSQL("INSERT INTO remote_storage_info VALUES (7, 'nas', '192.168.0.1', 2121, 'u', 'p', 'EUC-KR')")
            db.version = 3
        }
    }
}
