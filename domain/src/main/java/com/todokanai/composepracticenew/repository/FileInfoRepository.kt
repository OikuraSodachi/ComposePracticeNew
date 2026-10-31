package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.SizeMeasurement

/** 저장소 항목의 정보(하위 항목을 포함한 크기, 하위 항목 수)를 읽는다. */
interface FileInfoRepository {

    /**
     * [paths]의 크기를 하위 항목까지 더해 측정한다. 읽지 못한 하위 항목은 건너뛰고 결과에 모은다.
     * @param paths 측정할 항목 경로 목록
     * @return 측정 결과. 읽지 못한 하위 항목이 있으면 [SizeMeasurement.LowerBound]
     */
    suspend fun measureSize(paths: List<String>): IoResult<SizeMeasurement>

    /**
     * 디렉터리 [path] 바로 아래의 항목 수를 센다.
     * @param path 디렉터리 경로
     * @return 바로 아래 항목 수, 또는 디렉터리를 읽지 못한 실패
     */
    suspend fun countChildren(path: String): IoResult<Int>
}
