package com.todokanai.composepracticenew.data.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 프로토콜 구분 컬럼을 추가하는 마이그레이션. 이전 버전의 항목은 모두 FTP이므로 저장된 서버 목록을 그대로 두고 FTP로 표시한다. */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE remote_storage_info ADD COLUMN protocol TEXT NOT NULL DEFAULT '$PROTOCOL_FTP'")
    }
}
