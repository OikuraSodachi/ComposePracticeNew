package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.SizeMeasurement
import com.todokanai.composepracticenew.model.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import javax.inject.Inject

/**
 * 로컬 항목의 정보(하위 항목을 포함한 크기, 하위 항목 수)를 읽는다.
 *
 * 크기는 일반 파일의 크기만 더한다. 트리 안의 심볼릭 링크는 따라가지 않고 크기에도 넣지 않는다 — 링크 대상은 이 폴더의 내용이 아니며,
 * 따라가지 않으므로 링크 순환이 있어도 측정이 끝난다. 측정 대상 자신이 링크면 그 대상을 잰다.
 * @param localFiles 디렉터리 목록 읽기를 담당하는 LocalFileDataSource
 */
class LocalFileInfoDataSource @Inject constructor(
    private val localFiles: LocalFileDataSource
) {

    /**
     * [paths]의 크기를 하위 항목까지 더해 측정한다. 읽지 못한 항목은 건너뛰고 모으며, 코루틴이 취소되면 측정을 멈춘다.
     * @param paths 측정할 항목의 절대 경로 목록
     * @return 측정 결과. 읽지 못한 항목이 있으면 [SizeMeasurement.LowerBound]
     */
    suspend fun measureSize(paths: List<String>): IoResult<SizeMeasurement> = withContext(Dispatchers.IO) {
        val visitor = SizeVisitor(coroutineContext.job)
        paths.forEach { measureRoot(Paths.get(it), visitor) }
        IoResult.Success(visitor.result())
    }

    /**
     * 디렉터리 [path] 바로 아래의 항목 수를 센다.
     * @param path 디렉터리의 절대 경로
     * @return 바로 아래 항목 수, 또는 디렉터리를 읽지 못한 실패
     */
    suspend fun countChildren(path: String): IoResult<Int> = withContext(Dispatchers.IO) {
        localFiles.listFiles(path).map { it.size }
    }

    /**
     * 측정 대상 하나를 [visitor]로 훑는다. 대상 자신의 링크를 풀지 못하면(끊긴 링크 등) 그 대상을 읽지 못한 항목으로 남긴다.
     * @param root 측정 대상 경로
     * @param visitor 크기와 실패를 모으는 방문자
     */
    private fun measureRoot(root: Path, visitor: SizeVisitor) {
        val walked = localIo {
            Files.walkFileTree(resolvedRoot(root), visitor)
            IoResult.Done
        }
        if (walked is IoResult.Failure) visitor.addUnreadable(root, walked.message)
    }

    /** 측정 대상 자신이 심볼릭 링크면 링크가 가리키는 경로를, 아니면 그대로 돌려준다. 링크를 풀지 못하면 IOException을 던진다. */
    private fun resolvedRoot(root: Path): Path = if (Files.isSymbolicLink(root)) root.toRealPath() else root

    /**
     * 일반 파일의 크기를 더하고, 읽지 못한 항목을 모으는 방문자. 방문할 때마다 [job]의 취소를 확인한다.
     * walkFileTree는 이 방문자가 IOException을 던지지 않는 한 실패를 [visitFileFailed]로만 알린다.
     * 취소 확인을 테스트에서 검증할 수 있도록 internal로 둔다.
     * @param job 측정을 수행하는 코루틴의 Job
     */
    internal class SizeVisitor(private val job: Job) : SimpleFileVisitor<Path>() {
        /** 지금까지 더한 일반 파일 크기 합. */
        private var bytes = 0L
        /** 읽지 못한 항목 목록. */
        private val unreadable = mutableListOf<FailedItem>()

        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            job.ensureActive()
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            job.ensureActive()
            if (attrs.isRegularFile) bytes += attrs.size()
            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
            addUnreadable(file, exc.message)
            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            // 디렉터리를 끝까지 나열하지 못했다 — 이미 더한 항목은 남기고 이 디렉터리를 읽지 못한 항목으로 둔다
            if (exc != null) addUnreadable(dir, exc.message)
            return FileVisitResult.CONTINUE
        }

        /**
         * 읽지 못한 항목을 남긴다.
         * @param path 읽지 못한 항목 경로
         * @param reason 실패 원인 메시지. 모르면 null
         */
        fun addUnreadable(path: Path, reason: String?) {
            unreadable += FailedItem(path.toString(), reason)
        }

        /** 모은 크기와 실패로 측정 결과를 만든다. */
        fun result(): SizeMeasurement =
            if (unreadable.isEmpty()) SizeMeasurement.Complete(bytes)
            else SizeMeasurement.LowerBound(bytes, unreadable.toList())
    }
}
