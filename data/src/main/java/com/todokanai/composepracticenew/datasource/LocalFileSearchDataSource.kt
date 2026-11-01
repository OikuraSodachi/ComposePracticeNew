package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.SearchQuery
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 로컬 디렉터리 트리를 훑어 이름이 검색어와 맞는 항목을 찾는 데이터 소스.
 * 링크는 따라가지 않는다(walkFileTree 기본값) — 링크가 가리키는 폴더 안은 훑지 않아 순환이 생기지 않으며, 링크 자신은 이름이 맞으면 결과에 든다.
 * @param localFiles 찾은 항목을 목록 조회와 같은 방식(종류·크기·수정 시각)으로 만드는 LocalFileDataSource
 */
@Singleton
class LocalFileSearchDataSource @Inject constructor(
    private val localFiles: LocalFileDataSource
) {

    /**
     * [roots] 각각의 아래를 차례로 모두 훑어 이름이 [query]와 맞는 항목을 찾는다. 루트 자신은 결과에 넣지 않는다.
     * 읽지 못한 항목(나열하지 못한 폴더, 풀지 못한 루트 링크 등)은 읽지 못한 항목으로 알리고 나머지를 계속 훑는다.
     * @param roots 훑을 루트의 절대 경로 목록
     * @param query 검색어
     * @return 찾은 항목과 읽지 못한 항목을 [REPORT_INTERVAL_NANOS]마다 묶어 내보내는 Flow. 모두 훑으면 남은 것을 내보내고 완료된다
     */
    fun search(roots: List<String>, query: SearchQuery): Flow<LocalSearchBatch> = channelFlow {
        // 버퍼가 무제한이라 trySend는 채널이 닫혔을 때(수집 취소)만 실패한다 — 그때는 다음 방문의 취소 확인이 훑기를 멈춘다
        val reporter = SearchReporter(REPORT_INTERVAL_NANOS, System::nanoTime) { trySend(it) }
        val job = coroutineContext.job
        roots.forEach { root -> searchRoot(Paths.get(root), query, job, reporter) }
        reporter.flush()
    }
        .buffer(Channel.UNLIMITED)
        .flowOn(Dispatchers.IO)

    /**
     * 루트 하나의 아래를 훑는다. 루트 자신의 링크를 풀지 못하면(끊긴 링크 등) 그 루트를 읽지 못한 항목으로 남긴다.
     * @param root 훑을 루트
     * @param query 검색어
     * @param job 검색을 수행하는 코루틴의 Job
     * @param reporter 찾은 항목과 읽지 못한 항목을 모아 보내는 보고자
     */
    private fun searchRoot(root: Path, query: SearchQuery, job: Job, reporter: SearchReporter) {
        val walked = localIo {
            val walkRoot = walkRootOf(root)
            Files.walkFileTree(walkRoot, SearchVisitor(walkRoot, query, job, { path, attrs -> localFiles.entryOf(path.toFile(), attrs) }, reporter))
            IoResult.Done
        }
        if (walked is IoResult.Failure) reporter.unreadable(FailedItem(root.toString(), walked.message))
    }

    /**
     * 루트 하나의 아래에서 이름이 검색어와 맞는 항목을 [reporter]에 넘기고 읽지 못한 항목을 알리는 방문자. 방문할 때마다 [job]의 취소를 확인한다.
     * walkFileTree는 이 방문자가 IOException을 던지지 않는 한 실패를 [visitFileFailed]·[postVisitDirectory]로만 알린다.
     * 테스트에서 검증할 수 있도록 internal로 둔다.
     * @param root 훑는 루트. 루트 자신은 결과에 넣지 않는다
     * @param query 검색어
     * @param job 검색을 수행하는 코루틴의 Job
     * @param entryOf 찾은 경로와 훑으며 받은 그 속성(받지 못했으면 null)으로 목록 항목을 만드는 함수
     * @param reporter 찾은 항목과 읽지 못한 항목을 모아 보내는 보고자
     */
    internal class SearchVisitor(
        private val root: Path,
        private val query: SearchQuery,
        private val job: Job,
        private val entryOf: (Path, BasicFileAttributes?) -> FileEntry,
        private val reporter: SearchReporter
    ) : SimpleFileVisitor<Path>() {

        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            job.ensureActive()
            if (dir != root) check(dir, attrs)
            reporter.reportIfDue()
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            job.ensureActive()
            check(file, attrs)
            reporter.reportIfDue()
            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
            // 열지 못한 폴더도 이름은 알 수 있다 — 이름이 맞으면 결과에 넣고, 그 아래는 훑지 못했음을 함께 알린다
            if (file != root) check(file, null)
            reporter.unreadable(FailedItem(file.toString(), exc.message))
            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            // 디렉터리를 끝까지 나열하지 못했다 — 이미 찾은 항목은 남기고 이 디렉터리를 읽지 못한 항목으로 둔다
            if (exc != null) reporter.unreadable(FailedItem(dir.toString(), exc.message))
            return FileVisitResult.CONTINUE
        }

        /** [path]의 이름이 검색어와 맞으면 [attrs](훑으며 받은 속성, 없으면 null)로 항목을 만들어 찾은 항목으로 넘긴다. 맞지 않는 항목은 항목을 만들지 않는다. */
        private fun check(path: Path, attrs: BasicFileAttributes?) {
            val name = path.fileName?.toString() ?: return
            if (query.matches(name)) reporter.found(entryOf(path, attrs))
        }
    }

    /**
     * 찾은 항목과 읽지 못한 항목을 모았다가 [intervalNanos]가 지날 때마다 한 묶음으로 [report]에 넘긴다.
     * 항목마다 보내면 받는 쪽이 결과 목록을 항목 수만큼 다시 만들게 되므로, 보내는 횟수를 검색 시간에 비례하게 묶는다.
     * 테스트에서 검증할 수 있도록 internal로 둔다.
     * @param intervalNanos 묶음을 보내는 최소 간격(나노초)
     * @param nanoTime 지금 시각(나노초)을 읽는 함수
     * @param report 묶음을 받는 함수
     */
    internal class SearchReporter(
        private val intervalNanos: Long,
        private val nanoTime: () -> Long,
        private val report: (LocalSearchBatch) -> Unit
    ) {
        /** 지난 보고 이후 찾은 항목. */
        private val found = mutableListOf<FileEntry>()
        /** 지난 보고 이후 읽지 못한 항목. */
        private val unreadable = mutableListOf<FailedItem>()
        /** 마지막으로 보낸(또는 시작한) 시각. */
        private var lastReportNanos = nanoTime()

        /** 찾은 항목 [entry]를 모은다. */
        fun found(entry: FileEntry) {
            found += entry
        }

        /** 읽지 못한 항목 [item]을 모은다. */
        fun unreadable(item: FailedItem) {
            unreadable += item
        }

        /** 마지막 보고 뒤로 [intervalNanos]가 지났으면 모은 것을 보낸다. */
        fun reportIfDue() {
            if (nanoTime() - lastReportNanos >= intervalNanos) flush()
        }

        /** 모은 것이 있으면 한 묶음으로 보낸다. */
        fun flush() {
            lastReportNanos = nanoTime()
            if (found.isEmpty() && unreadable.isEmpty()) return
            report(LocalSearchBatch(found.toList(), unreadable.toList()))
            found.clear()
            unreadable.clear()
        }
    }

    private companion object {
        /** 검색 결과 묶음을 보내는 최소 간격 — 결과가 화면에 늦지 않게 보이면서 목록을 지나치게 자주 다시 만들지 않는 값 */
        const val REPORT_INTERVAL_NANOS = 200_000_000L
    }
}

/**
 * 로컬 검색이 지난 보고 이후 새로 찾은 항목과 읽지 못한 항목의 묶음.
 * @param found 이름이 검색어와 맞는 항목. 찾은 순서다
 * @param unreadable 읽지 못한 항목. 이 아래는 훑지 못했다
 */
data class LocalSearchBatch(val found: List<FileEntry>, val unreadable: List<FailedItem>)
