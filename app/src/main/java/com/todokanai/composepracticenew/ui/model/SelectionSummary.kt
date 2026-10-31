package com.todokanai.composepracticenew.ui.model

import com.todokanai.composepracticenew.model.FileNameParts
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.SizeMeasurement
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td

/**
 * 정보 다이얼로그에 표시할 선택 항목 요약. 항목이 하나면 [Single]로 그 항목의 상세를, 아니면 [Multiple]로 개수와 크기 합을 담는다.
 */
sealed interface SelectionSummary {

    /** 선택 항목의 크기(하위 항목 포함) 표시 상태. */
    val size: SizeInfo

    /**
     * 항목 하나의 상세.
     * @param name 항목 이름
     * @param location 항목이 들어 있는 폴더 경로
     * @param kind 항목 종류(폴더 / 확장자별 파일)
     * @param lastModified 마지막 수정 시각 표시 문자열
     * @param size 크기(폴더는 하위 항목 포함) 표시 상태
     * @param childCount 폴더 바로 아래 항목 수 표시 상태. 파일이면 null
     */
    data class Single(
        val name: String,
        val location: String,
        val kind: ItemKind,
        val lastModified: String,
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
         * 크기·하위 항목 수를 아직 계산하지 않은 요약을 만든다.
         * @param items 선택 항목 목록
         * @return 항목이 하나면 [Single], 아니면 [Multiple]. 크기·하위 항목 수는 계산 중 상태다
         */
        fun loading(items: List<FileHolderItem>): SelectionSummary =
            items.singleOrNull()?.let(::loadingSingle) ?: Multiple(count = items.size, size = SizeInfo.Loading)

        /** 항목 하나의 상세를 크기·하위 항목 수 계산 중 상태로 만든다. */
        private fun loadingSingle(item: FileHolderItem) = Single(
            name = item.name,
            location = locationOf(item),
            kind = kindOf(item),
            lastModified = item.lastModified,
            size = SizeInfo.Loading,
            childCount = if (item.isDirectory) ChildCountInfo.Loading else null
        )

        /** 항목이 들어 있는 폴더 경로('/' 구분 문자열). 루트 바로 아래면 "/", 구분자가 없으면 경로 자신이다. */
        private fun locationOf(item: FileHolderItem): String {
            val end = item.path.lastIndexOf('/')
            return when {
                end > 0 -> item.path.substring(0, end)
                end == 0 -> "/"
                else -> item.path
            }
        }

        /** 항목 종류. 파일이면 이름에서 확장자를 나눈다. */
        private fun kindOf(item: FileHolderItem): ItemKind =
            if (item.isDirectory) ItemKind.Folder else ItemKind.File(extensionOf(item.name))

        /** 이름의 확장자(점 제외). 확장자가 없으면(숨김 파일의 첫 점 포함) null이다. */
        private fun extensionOf(name: String): String? =
            name.substring(FileNameParts.baseNameEnd(name)).removePrefix(".").takeIf { it.isNotEmpty() }
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
