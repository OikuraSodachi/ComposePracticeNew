package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.datasource.LocalFileInfoDataSource
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.SizeMeasurement
import javax.inject.Inject

/**
 * 로컬 파일 시스템 항목의 정보를 읽는 FileInfoRepository 구현체.
 * @param dataSource 로컬 항목의 크기·하위 항목 수를 읽는 LocalFileInfoDataSource
 */
class LocalFileInfoRepositoryImpl @Inject constructor(
    private val dataSource: LocalFileInfoDataSource
) : FileInfoRepository {

    override suspend fun measureSize(paths: List<String>): IoResult<SizeMeasurement> = dataSource.measureSize(paths)

    override suspend fun countChildren(path: String): IoResult<Int> = dataSource.countChildren(path)
}
