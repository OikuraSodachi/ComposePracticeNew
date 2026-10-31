package com.todokanai.composepracticenew.ui.model

import com.todokanai.composepracticenew.model.FileNameParts
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ItemAttributes
import com.todokanai.composepracticenew.model.SizeMeasurement
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td
import java.text.DateFormat

/**
 * 정보 다이얼로그에 표시할 선택 항목 요약. 항목이 하나면 [Single]로 그 항목의 상세를, 아니면 [Multiple]로 개수와 크기 합을 담는다.
 */
sealed interface SelectionSummary {

    /** 선택 항목의 크기(하위 항목 포함) 표시 상태. */
    val size: SizeInfo

    /**
     * 항목 하나의 상세. 이름은 목록의 값, 위치는 저장소의 경로 규칙으로 정하고, 그 밖의 정보는 다이얼로그를 열 때 저장소에서 다시 읽는다.
     * @param name 항목 이름
     * @param location 항목이 들어 있는 폴더 경로. 경로에 상위가 없으면 null
     * @param attributes 종류·수정 시각 표시 상태
     * @param size 크기(폴더는 하위 항목 포함) 표시 상태
     * @param childCount 폴더 바로 아래 항목 수 표시 상태. 폴더임을 확인하기 전(속성을 읽는 중이거나 읽지 못함)과 파일이면 null
     */
    data class Single(
        val name: String,
        val location: String?,
        val attributes: AttributesInfo,
        override val size: SizeInfo,
        val childCount: ChildCountInfo?
    ) : SelectionSummary

    /**
     * 여러 항목(또는 빈 선택)의 요약.
     * @param count 선택 항목 수
     * @param size 크기 합(폴더는 하위 항목 포함) 표시 상태
     */
    data class Multiple(val count: Int, override val size: SizeInfo) : SelectionSummary

    /**
     * 크기 표시 상태만 바꾼 같은 요약을 만든다.
     * @param size 새 크기 표시 상태
     * @return 크기만 바뀐 요약
     */
    fun withSize(size: SizeInfo): SelectionSummary = when (this) {
        is Single -> copy(size = size)
        is Multiple -> copy(size = size)
    }

    companion object {
        /**
         * 저장소에서 읽을 정보를 아직 읽지 않은 요약을 만든다.
         * @param items 선택 항목 목록
         * @param locationOf 항목이 들어 있는 폴더 경로를 구하는 함수. 상위가 없으면 null
         * @return 항목이 하나면 [Single], 아니면 [Multiple]. 속성·크기는 읽는 중 상태다
         */
        fun loading(items: List<FileHolderItem>, locationOf: (FileHolderItem) -> String?): SelectionSummary =
            items.singleOrNull()?.let { loadingSingle(it, locationOf(it)) } ?: Multiple(count = items.size, size = SizeInfo.Loading)

        /** 항목 하나의 상세를 속성·크기를 읽는 중 상태로 만든다. 하위 항목 수는 폴더임을 확인한 뒤에 센다. */
        private fun loadingSingle(item: FileHolderItem, location: String?) = Single(
            name = item.name,
            location = location,
            attributes = AttributesInfo.Loading,
            size = SizeInfo.Loading,
            childCount = null
        )
    }
}

/** 단일 항목의 종류·수정 시각 표시 상태. 읽지 못한 항목이 목록 조회 때의 값으로 보이지 않도록 [Failed]로 구분한다. */
sealed interface AttributesInfo {
    /** 읽는 중. */
    data object Loading : AttributesInfo

    /**
     * 저장소에서 읽은 속성.
     * @param kind 항목 종류(폴더 / 확장자별 파일)
     * @param lastModified 마지막 수정 시각 표시 문자열
     */
    data class Known(val kind: ItemKind, val lastModified: String) : AttributesInfo

    /**
     * 항목을 읽지 못했다(그사이 지워진 항목 등).
     * @param message 표시할 실패 메시지. 원인을 모르면 null
     */
    data class Failed(val message: String?) : AttributesInfo

    companion object {
        /**
         * 속성 읽기 결과를 표시 상태로 바꾼다.
         * @param result 속성 읽기 결과
         * @param name 항목 이름 — 파일이면 확장자를 나눈다
         * @return 종류·수정 시각 또는 실패 표시 상태
         */
        fun from(result: IoResult<ItemAttributes>, name: String): AttributesInfo = when (result) {
            is IoResult.Success -> Known(
                kind = kindOf(result.value, name),
                lastModified = DateFormat.getDateTimeInstance().format(result.value.lastModifiedMillis)
            )
            is IoResult.Failure -> Failed(result.message)
        }

        /** 항목 종류. 파일이면 이름에서 확장자를 나눈다. */
        private fun kindOf(attributes: ItemAttributes, name: String): ItemKind =
            if (attributes.isDirectory) ItemKind.Folder else ItemKind.File(FileNameParts.extensionOf(name))
    }
}

/** 정보 다이얼로그에 표시할 항목 종류. 표시 문구는 View가 문자열 리소스로 매핑한다. */
sealed interface ItemKind {
    /** 폴더. */
    data object Folder : ItemKind

    /**
     * 파일.
     * @param extension 점을 뺀 확장자. 확장자가 없으면 null
     */
    data class File(val extension: String?) : ItemKind
}

/** 크기(하위 항목 포함) 표시 상태. 일부를 읽지 못한 합계가 정확한 합계로 보이지 않도록 [AtLeast]로 구분한다. */
sealed interface SizeInfo {
    /** 계산 중. */
    data object Loading : SizeInfo

    /**
     * 모든 하위 항목을 읽은 정확한 크기.
     * @param text 사람이 읽기 쉬운 단위로 포맷한 크기
     */
    data class Exact(val text: String) : SizeInfo

    /**
     * 일부 하위 항목을 읽지 못해 읽은 항목만 더한 하한.
     * @param text 사람이 읽기 쉬운 단위로 포맷한 하한
     * @param unreadableCount 읽지 못한 항목 수
     */
    data class AtLeast(val text: String, val unreadableCount: Int) : SizeInfo

    /**
     * 크기를 구하지 못했다.
     * @param message 표시할 실패 메시지. 원인을 모르면 null
     */
    data class Failed(val message: String?) : SizeInfo

    companion object {
        /**
         * 크기 측정 결과를 표시 상태로 바꾼다.
         * @param result 크기 측정 결과
         * @return 정확한 크기, 하한, 또는 실패 표시 상태
         */
        fun from(result: IoResult<SizeMeasurement>): SizeInfo = when (result) {
            is IoResult.Success -> when (val measurement = result.value) {
                is SizeMeasurement.Complete -> Exact(readableFileSize_td(measurement.bytes))
                is SizeMeasurement.LowerBound -> AtLeast(readableFileSize_td(measurement.bytes), measurement.unreadable.size)
            }
            is IoResult.Failure -> Failed(result.message)
        }
    }
}

/** 폴더 바로 아래 항목 수 표시 상태. 읽지 못한 폴더가 0개로 보이지 않도록 [Failed]로 구분한다. */
sealed interface ChildCountInfo {
    /** 계산 중. */
    data object Loading : ChildCountInfo

    /**
     * 바로 아래 항목 수.
     * @param count 항목 수
     */
    data class Known(val count: Int) : ChildCountInfo

    /**
     * 폴더를 읽지 못했다.
     * @param message 표시할 실패 메시지. 원인을 모르면 null
     */
    data class Failed(val message: String?) : ChildCountInfo

    companion object {
        /**
         * 하위 항목 수 결과를 표시 상태로 바꾼다.
         * @param result 하위 항목 수 결과
         * @return 항목 수 또는 실패 표시 상태
         */
        fun from(result: IoResult<Int>): ChildCountInfo = when (result) {
            is IoResult.Success -> Known(result.value)
            is IoResult.Failure -> Failed(result.message)
        }
    }
}
