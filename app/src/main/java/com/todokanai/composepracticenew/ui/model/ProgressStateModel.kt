package com.todokanai.composepracticenew.ui.model

import androidx.compose.runtime.Immutable
import com.todokanai.composepracticenew.model.ByteAmount
import com.todokanai.composepracticenew.model.CurrentFile
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IndeterminatePhase
import com.todokanai.composepracticenew.model.KnownSizeProgress
import com.todokanai.composepracticenew.model.ProgressPhase
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td

/**
 * app 모듈에서 사용하는 Progress UI 상태. ProgressState를 app 레이어에서 매핑해 사용한다.
 * @param operationType 작업 종류
 * @param phase 지금 단계의 UI 상태
 */
@Immutable
data class ProgressStateModel(
    val operationType: FileOperationType,
    val phase: ProgressPhaseModel
)

/** 진행 단계의 UI 상태. 단계마다 보일 내용이 다르므로 exhaustive `when`으로 가른다. */
sealed interface ProgressPhaseModel {

    /** 아직 진행을 셀 수 없는 시작 상태. 퍼센트를 0%로 지어내지 않고 알 수 없음으로 보인다. */
    data object Starting : ProgressPhaseModel

    /**
     * 항목 수로 세는 진행의 UI 상태.
     * @param percent 처리한 항목 수의 비율(0~100 퍼센트)
     * @param processed 처리한 항목 수
     * @param total 전체 항목 수
     * @param currentName 지금 처리하는 항목의 이름
     */
    data class Counted(
        val percent: Int,
        val processed: Int,
        val total: Int,
        val currentName: String
    ) : ProgressPhaseModel

    /**
     * 바이트로 세는 진행의 UI 상태.
     * @param amount 작업 전체에 대한 바이트 진행
     * @param writtenSize 처리한 크기(사람이 읽을 수 있는 문자열)
     * @param fileIndex 지금 처리하는 파일의 순번
     * @param fileCount 작업 대상 파일 개수
     * @param currentFile 지금 처리하는 파일. 없으면 null
     */
    data class Bytes(
        val amount: ByteAmountModel,
        val writtenSize: String,
        val fileIndex: Int,
        val fileCount: Int,
        val currentFile: CurrentFileModel?
    ) : ProgressPhaseModel
}

/** 퍼센트 없이 움직이는 막대로 보이는 진행 단계의 UI 상태. 단계마다 보일 문구가 다르므로 exhaustive `when`으로 가른다. */
sealed interface IndeterminatePhaseModel : ProgressPhaseModel {

    /**
     * 처리 전에 대상을 훑는 준비 단계의 UI 상태.
     * @param foundEntries 지금까지 찾은 항목 수
     * @param currentName 마지막으로 찾은 항목의 이름
     */
    data class Preparing(
        val foundEntries: Int,
        val currentName: String
    ) : IndeterminatePhaseModel

    /**
     * 압축 해제 전에 비밀번호를 내용으로 확인하는 단계의 UI 상태.
     * @param archiveName 비밀번호를 확인하는 압축 파일의 표시 이름
     */
    data class CheckingPassword(
        val archiveName: String
    ) : IndeterminatePhaseModel
}

/** 작업 전체에 대한 바이트 진행의 UI 상태. */
sealed interface ByteAmountModel {

    /**
     * 퍼센트를 낼 수 있는 진행의 UI 상태.
     * @param percent 진행도(0~100 퍼센트)
     * @param totalSize 작업 전체 크기(사람이 읽을 수 있는 문자열)
     */
    data class Known(
        val percent: Int,
        val totalSize: String
    ) : ByteAmountModel

    /**
     * 크기를 모르는 파일이 섞여 퍼센트를 낼 수 없는 진행의 UI 상태.
     * @param knownSize 크기를 아는 부분과 모르는 부분의 진행
     */
    data class PartlyUnknown(
        val knownSize: KnownSizeProgressModel
    ) : ByteAmountModel
}

/**
 * 지금 처리하는 파일의 UI 상태.
 * @param name 파일 이름
 * @param size 파일의 전체 크기(사람이 읽을 수 있는 문자열). 모르면 null
 */
data class CurrentFileModel(
    val name: String,
    val size: String?
)

/**
 * 크기를 아는 파일과 모르는 파일을 나눈 진행의 UI 상태.
 * @param progress 크기를 아는 파일들에 대한 진행도(0~100 퍼센트). 모두 처리 완료되기 전에는 99까지다. 크기를 아는 부분의 합이 0이면(그런 파일이 없거나 모두 빈 파일) null
 * @param writtenSize 크기를 아는 파일들에서 처리한 크기(사람이 읽을 수 있는 문자열)
 * @param totalSize 크기를 아는 파일들의 크기 합(사람이 읽을 수 있는 문자열)
 * @param unknownFileCount 크기를 모르는 파일 수
 * @param unknownWrittenSize 크기를 모르는 파일들에서 처리한 크기(사람이 읽을 수 있는 문자열)
 */
@Immutable
data class KnownSizeProgressModel(
    val progress: Int?,
    val writtenSize: String,
    val totalSize: String,
    val unknownFileCount: Int,
    val unknownWrittenSize: String
)

/** ProgressState → ProgressStateModel 변환. bytes 값을 사람이 읽을 수 있는 문자열로 변환한다. */
fun ProgressState.toModel(): ProgressStateModel = ProgressStateModel(operationType, phase.toModel())

/** ProgressPhase → ProgressPhaseModel 변환. */
private fun ProgressPhase.toModel(): ProgressPhaseModel = when (this) {
    ProgressPhase.Starting -> ProgressPhaseModel.Starting
    is IndeterminatePhase -> toIndeterminateModel()
    is ProgressPhase.Counted -> ProgressPhaseModel.Counted(percent, processed, total, currentName)
    is ProgressPhase.Bytes -> ProgressPhaseModel.Bytes(
        amount = amount.toModel(),
        writtenSize = readableFileSize_td(writtenBytes),
        fileIndex = fileIndex,
        fileCount = fileCount,
        currentFile = currentFile?.toModel()
    )
}

/** IndeterminatePhase → IndeterminatePhaseModel 변환. */
private fun IndeterminatePhase.toIndeterminateModel(): IndeterminatePhaseModel = when (this) {
    is IndeterminatePhase.Preparing -> IndeterminatePhaseModel.Preparing(foundEntries, currentName)
    is IndeterminatePhase.CheckingPassword -> IndeterminatePhaseModel.CheckingPassword(archiveName)
}

/** ByteAmount → ByteAmountModel 변환. */
private fun ByteAmount.toModel(): ByteAmountModel = when (this) {
    is ByteAmount.Known -> ByteAmountModel.Known(percent, readableFileSize_td(totalBytes))
    is ByteAmount.PartlyUnknown -> ByteAmountModel.PartlyUnknown(knownSize.toModel())
}

/** CurrentFile → CurrentFileModel 변환. */
private fun CurrentFile.toModel(): CurrentFileModel = CurrentFileModel(name, bytes?.let { readableFileSize_td(it) })

/** KnownSizeProgress → KnownSizeProgressModel 변환. */
private fun KnownSizeProgress.toModel(): KnownSizeProgressModel = KnownSizeProgressModel(
    progress = knownPercentOf(knownWrittenBytes, knownCompletedBytes, knownTotalBytes),
    writtenSize = readableFileSize_td(knownWrittenBytes),
    totalSize = readableFileSize_td(knownTotalBytes),
    unknownFileCount = unknownFileCount,
    unknownWrittenSize = readableFileSize_td(unknownWrittenBytes)
)

/**
 * 크기를 아는 부분의 퍼센트를 구한다. 100%는 끝났다는 뜻이므로, 모든 파일이 처리 완료되기 전에는 99까지만 낸다.
 * @param written 진행 바이트
 * @param completed 처리 완료된 파일들의 크기 합
 * @param total 크기 합
 * @return 0~100 퍼센트. [total]이 0이면 나눌 기준이 없어 null
 */
private fun knownPercentOf(written: Long, completed: Long, total: Long): Int? = when {
    total <= 0 -> null
    completed >= total -> 100
    else -> (written * 100 / total).toInt().coerceIn(0, 99)
}

/** 처리한 크기의 표시 문구. 전체 크기를 알면 "처리 / 전체", 모르면 처리한 크기만 보인다. */
fun ProgressPhaseModel.Bytes.sizeText(): String = when (amount) {
    is ByteAmountModel.Known -> "$writtenSize / ${amount.totalSize}"
    is ByteAmountModel.PartlyUnknown -> writtenSize
}

/** 바이트 진행의 퍼센트. 크기를 모르는 파일이 섞였으면 null이다. */
fun ProgressPhaseModel.Bytes.percent(): Int? = when (amount) {
    is ByteAmountModel.Known -> amount.percent
    is ByteAmountModel.PartlyUnknown -> null
}
