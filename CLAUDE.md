# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A File Manager app built with Jetpack Compose (Android).

## Architecture

**Single Activity + Compose, MVVM + Hilt DI.**

SOLID 원칙을 준수한다.

### Modules

- `domain`: 모델·Repository 인터페이스·UseCase (순수 Kotlin).
- `data`: domain 인터페이스의 구현과 그 Hilt 모듈.
- `fileExplorer`: '/' 구분 경로 도우미(`defaultDisplayName`, `defaultParentPath`). data만 의존하는 JVM 모듈이다. 저장소에서 읽은 항목 모델(`FileEntry`)은 그 모델을 만들고 쓰는 data에 있다.
- `core`: 여러 모듈이 함께 쓰는 DI 한정자(`@ApplicationScope`, `@RemoteNavigator`). javax.inject만 쓰는 JVM 모듈이다.
- `app`: 화면·서비스·DI 조립. data는 `runtimeOnly`로만 의존한다. Hilt는 집계 태스크로 data의 모듈을 찾으므로, app 코드는 data의 클래스·리소스를 컴파일 단계에서 참조할 수 없다. app과 data가 함께 써야 하는 것은 `domain`이나 `core`에 둔다.

### Global State — `@Singleton` Repositories

Cross-ViewModel reactive state lives as `MutableStateFlow` properties inside `@Singleton` repository implementations in the data module:

| Repository | Key State | Purpose |
|---|---|---|
| `FileNavigator` | `currentPath`, `directoryListing`, `dirTree` | Navigation and directory listing |
| `ProgressTracker` | `progressState` | Ongoing operation progress |
| `StorageRepositoryImpl` | `storageList` | Available storage volumes |

`navigate(path)` is the canonical way to navigate — it sets `currentPath` (and thus `dirTree`) and a new `ListingRequest`; `directoryListing` follows asynchronously as the result of that request.

읽을 수 없는 경로로의 이동은 현재 경로를 바꾸지 않고 `NavigateResult.Rejected`를 돌려주며, 거부는 탐색 저장소(`FileExplorerRepositoryImpl`)가 `OperationNoticeReporter`로 직접 알린다(#279) — 부르는 곳마다 거부를 확인하지 않아도 알림이 빠지지 않도록. 부르는 곳은 알리지 않고, 이동 뒤에 할 일(저장소 화면에서 목록으로 넘어가기, 검색 닫기)이 있을 때만 `Navigated`인지 본다. 거부할 때마다 알린다 — 같은 거부를 여러 번 하면 알림도 그 수만큼 차례로 보인다(알림함은 같은 내용을 합치지 않는다). 로컬 경로의 부모는 저장소 볼륨 루트에서 끝난다(`LocalFileSystem.getParent`) — 볼륨 위 폴더는 앱이 읽을 수 없으므로, 경로 표시줄은 볼륨 루트부터 보이고 볼륨 루트에서 뒤로가기는 아무것도 하지 않는다(의도된 동작, #90 — 뒤로가기로 로컬 목록을 나가지 않는다).

`directoryListing` is a `DirectoryListing` state (`NoPath` / `Loading` / `Loaded` / `Failed`), never a bare list: a listing failure must not appear as an empty directory.

- 목록 상태는 `ListingRequest(path, generation)`에 묶인다. 이동·새로고침·재연결·공유 재시작마다 새 요청이 되며, 같은 경로라도 이전 요청의 `Loaded`는 현재 목록이 아니다. 같은 경로를 다시 조회하는 동안의 직전 목록은 `Loading.stale`(표시 전용)이다.
- 정렬은 조회 결과 위에 적용한다(정렬 변경으로 다시 조회하지 않는다).
- 이름 충돌 판정(로컬 쓰기는 쓰는 시점의 파일 시스템, 업로드·이름 변경은 목록, 작업 질문함)은 `.claude/rules/write-conflicts.md`에 있다 — 해당 코드를 다룰 때 자동으로 로드된다.

### Navigation (`navigation/NavController.kt`)

`STORAGE` 밖에 `FILE_BROWSER_GRAPH`(`FILE_LIST`, `REMOTE_FILE_LIST`)가 있고, `FileTransferCoordinatorViewModel`은 이 그래프 항목에 스코프된다 — 그래프를 벗어나면(STORAGE 복귀) 전송·선택 상태가 초기화된다.

그래프 안의 백스택 모양은 들어온 경로에 따라 다르며, 이는 의도된 동작이다(하나의 규칙으로 통일하지 않는다):
- 탭 전환(`popUpTo(FILE_BROWSER_GRAPH)`)·저장소에서 원격 연결로 들어온 원격 화면은 그래프에 혼자 있어, 루트에서 뒤로가면 저장소로 나간다.
- 업로드 진입·원격 진행 알림으로 들어온 원격 화면은 `FILE_LIST` 위에 쌓여, 루트에서 뒤로가면 로컬 목록으로 돌아온다(업로드 대기 상태 유지).
- 압축 파일 내용 보기(`ARCHIVE_VIEW`)에서는 원격 진행 알림을 눌러도 원격 화면으로 가지 않는다(진행 다이얼로그만 뜬다) — 내용 보기 위에 원격 화면을 쌓지 않는다.
- 다운로드 진입은 원격 화면을 꺼내고 로컬 목록으로 간다.
- 압축 파일 내용 보기로의 이동은 `FILE_LIST`의 back stack entry가 RESUMED일 때만 실행한다(#537) — 연타로 `ARCHIVE_VIEW`가 두 번 쌓이지 않도록. 화면 전환 애니메이션 동안(entry가 STARTED)에 연 요청도 버려진다(다시 누르면 열린다).
- 연결된 원격 세션이 없어 원격 화면이 나갈 때도 루트 뒤로가기와 같이 원격 화면만 닫는다(#492) — 나가는 곳은 위 백스택 모양이 정한다.

### UI Modes (`ui/model/SelectMode`)

선택 모드는 sealed 타입이다. 화면별로 가능한 모드는 `LocalSelectMode` / `RemoteSelectMode`로, 확인 버튼을 갖는 모드는 `ConfirmMode`(라벨 `labelRes`)로 타입을 좁혀 불가능한 조합을 컴파일 단계에서 배제한다.

```
SelectMode.Default / MultiSelect          — 두 화면 공통 (일반 탐색 / 다중 선택)
SelectMode.LocalOperation.Copy/Move/Unzip/UnzipHere — 로컬 화면, 목적지 확인 후 실행 (같은 이름은 실행 중에 묻는다)
SelectMode.Download                       — 로컬 화면, 다운로드 목적지 확인
SelectMode.Upload                         — 원격 화면, 업로드 목적지 확인
```

선택 상태(`SelectionState`, 전송 대기 목록, 선택 초기화 규칙 #226, `localMode()`/`remoteMode()`)는 `.claude/rules/selection-state.md`에 있다 — 해당 코드를 다룰 때 자동으로 로드된다.

### Dialog State

다이얼로그의 열림 상태는 그 다이얼로그의 대상과 수명이 같은 곳에 둔다(#273) — 수명이 다르면 회전 때 닫히거나, 복원 뒤 대상 없이 열린다.
- 대상이 ViewModel 상태(선택 목록·폴더)인 다이얼로그(목록 하단 버튼의 압축·이름 변경·삭제, 메뉴의 새 폴더·정렬)는 화면 단위 ViewModel이 보관하는 `ScreenDialogState`에 둔다. 회전에는 유지되고, 다른 화면으로 갔다 돌아오거나 프로세스가 종료되면 닫힌다(`rememberScreenDialogState`). 열 때마다 새 `openId`를 발급하고 호스트가 `key(openId)`로 감싸, 저장된 이전 입력값이 새로 연 다이얼로그에 복원되지 않게 한다.
- 대상이 저장된 데이터(원격 서버 id)인 다이얼로그(저장소 화면의 추가·편집·삭제)는 `rememberSaveable`(`StorageDialogState`)로 프로세스 종료 뒤에도 복원한다.
- 계산·작업 상태가 대상인 다이얼로그(정보, 충돌 확인, 진행, 업로드 확인)는 그 상태를 가진 ViewModel이 보관한다.

### File Operations

- 로컬: `LocalFileOperationUseCase` → `FileActionRepository`(`FileActionRepositoryImpl`). 구현은 작업 단위 클래스(`LocalTreeTransfer`, `LocalArchiveCompression` + 형식별 `ArchiveCompressor`(@IntoMap, 키는 domain `ArchiveFormat`), `LocalArchiveExtraction` + 형식별 `ArchiveExtractor`(@IntoSet, 항목 생성·건너뜀 집계는 공통 `ArchiveEntryWriter`로만. ZIP은 zip4j, RAR·7z·ISO는 7-Zip-JBinding `SevenZipArchiveExtractor`의 형식별 인스턴스 — 이름 규칙과 받는 형식만 다르다. 형식은 자동 판별하지 않고 받는 형식을 순서대로 지정해 연다 — ISO는 UDF를 먼저 연다), `LocalEntryMutator`)로 나뉘고, `FileActionRepositoryImpl`이 이들에 직접 맡긴다(사이에 data source 인터페이스를 두지 않는다 — 교체 지점은 `FileActionRepository` 바인딩).
- 압축 비밀번호(AES-256 압축, 압축 해제의 비밀번호 질문·확인)와 분할 압축 해제(시작 볼륨, 볼륨 묶기, 빠진 볼륨), 압축 파일 내용 보기(`ArchiveViewUseCase` → `ArchiveContentRepository`, 화면 `ARCHIVE_VIEW`)는 `.claude/rules/archive.md`에 있다 — 해당 코드를 다룰 때 자동으로 로드된다.
- 원격: `RemoteFileOperationUseCase` → `RemoteFileTransferRepository` / `RemoteFileMutationRepository` (FTP 구현: `FtpRepositoryImpl`). 원격 작업은 실행을 요청할 때(보호를 기다리기 전에) 지금의 원격 세션(`RemoteSession`, `RemoteSessionProvider`)에 묶이며, 그 뒤 다른 서버에 접속해도 고른 세션에서만 실행된다(#436). 진행 표시가 있는 원격 작업은 작업마다 자기 연결을 연다(`.claude/rules/ftp.md`의 세션 단위).
- 실행·진행 보고·완료/실패 처리는 `FileOperationOrchestrator`가 담당하며, 작업 인스턴스 ID도 여기서만 발급한다.
- 진행 표시가 있는 작업의 저장소 계약은 그 작업의 대상 전체를 한 번에 받는다. 보고하는 진행 단계(`ProgressPhase`)는 구현이 계산한 퍼센트·전체 수라 부르는 쪽이 대상별 보고를 합칠 수 없고, 폴더 안쪽 수는 구현만 알기 때문이다. 대상 하나를 받는 계약을 UseCase가 반복해 부르면 진행이 대상마다 0%로 돌아간다(로컬 삭제 #355). 기존 계약에 진행 표시를 붙일 때도 받는 범위를 함께 바꾼다.
- 진행 상태는 `ProgressTracker`가 맡는다 — 쓰는 쪽은 `ProgressReporter`(작업 인스턴스 ID를 발급하는 오케스트레이터만), 읽는 쪽은 `ProgressRepository`로만 의존한다. 사용자에게 알릴 실패·실행 전 거부·완료 메시지는 `OperationNotices`가 맡는다 — 넣는 쪽은 `OperationNoticeReporter`, 꺼내는 쪽은 `OperationNoticeRepository`로만 의존한다. 거부를 알리는 UseCase는 오케스트레이터를 거치지 않고 직접 넣는다. 취소 뒤 정리하지 못한 것(쓰다 만 파일, `NoticeMessage.Leftover`)도 취소를 결과로 돌려줄 수 없으므로 구현이 직접 넣는다 — 로컬 파일은 `LeftoverDiscarder`, FTP 업로드의 받다 만 파일은 `FtpUploader`(`.claude/rules/ftp.md`의 전송 중단, `.claude/rules/local-tree.md`의 쓰다 만 파일). 메시지는 흘려보내지 않고 화면이 꺼낼 때까지 보관하며, `AppNoticeHost`가 화면이 보이는 동안 `AppNoticeViewModel.showNotices`(앱 알림함 모두를 한곳에서 꺼낸다, #449)로 받아 Snackbar로 알린다(보이는 화면이 없을 때 끝난 작업의 실패도 잃지 않도록). 넣는 쪽은 문장이 아니라 domain sealed `NoticeMessage`(무엇을 알릴지와 번역하지 않는 값)를 넣고, 표시 문구는 app의 `noticeTextOf`가 exhaustive `when`으로 문자열 리소스에 대응시킨다(#218). 작업의 완료·취소·실패 메시지는 실행한 작업(domain sealed `OperationKind` — 진행 표시·보호가 있는 `Tracked(FileOperationType)` / 바로 끝나는 `Instant`(이름 변경·새 폴더, 이름))을 담는다(#460). 작업 실패(`NoticeMessage.OperationFailed`)는 작업과 `IoResult` 실패를 그대로 담고, 문구는 app의 `failureTextOf`가 만든다. 실패의 머리말("이름 변경 실패: 이름" 등)은 app이 작업으로 정하므로, 저장소와 UseCase는 실패 원인에 작업 맥락을 붙이지 않는다. 진행 표시·보호 여부는 오케스트레이터의 진입점(`launch` / `launchInstant`)으로 정해진다.
- 알림함(`NoticeInbox`)의 꺼내기·완료·돌려놓기와 표시 수단은 `.claude/rules/notice-inbox.md`에 있다 — 해당 코드를 다룰 때 자동으로 로드된다.
- 작업 종류는 `FileOperationType` enum이며, 레이어별 표시 문자열은 enum에 대한 exhaustive `when`으로 매핑한다.
- 파일 입출력(로컬·원격 작업, 목록 조회)의 실패는 예외가 아니라 domain `IoResult`(`Success` / `ItemFailed` / `PartiallyFailed` / `ConnectionLost`)로 돌려준다. 받는 쪽은 exhaustive `when`으로 처리한다. 예외는 취소·구현 오류에만 쓰며, 앱 수명 흐름을 지키기 위해 결과로 바꾸는 곳은 `launchOperation`과 `FileListFlowProducer.load` 두 경계뿐이다. 두 경계는 예외를 기록하고(작업은 domain `UnexpectedFailureRecorder`) 내부 예외 메시지 없이 일반 실패 문구로 알린다.
- 실패는 문장이 아니라 원인(domain sealed `FailureReason`)을 담는다(#218). 서버 응답·예외 메시지처럼 번역하지 않는 값은 원인의 필드로 담고, 문구는 app의 `failureTextOf`가 원인마다 문자열 리소스로 만든다. 원격 원인은 프로토콜 명령 대신 작업 단계(`RemoteStep`)로 나눈다.
- 여러 항목 작업은 `forEachCollectingFailures`로 실패를 모은다. `ConnectionLost`면 남은 항목을 시도하지 않는다. 부분 실패(`PartiallyFailed`)는 머리말을 담지 않으며, 머리말은 알리는 쪽(app)이 작업 종류로 정한다 — 안쪽 수집의 머리말은 평탄화되며 버려지므로.
- FTP 프로토콜 규칙(응답 해석, 연결·목록 범위, 현재 디렉터리, 트리 수집, 종료)은 `.claude/rules/ftp.md`에 있다 — FTP 코드·테스트를 다룰 때 자동으로 로드된다.
- 원격 연결 상태(`RemoteConnectionState`)·끊김 알림·연결 실패 알림은 `.claude/rules/remote-connection.md`에 있다 — 해당 코드를 다룰 때 자동으로 로드된다.
- 포그라운드 서비스(연결 유지·파일 작업 보호)의 시작·멈춤·실행 시간 한도·시작 거부와 작업 보호는 `.claude/rules/foreground-service.md`에 있다 — 해당 코드를 다룰 때 자동으로 로드된다.
- 작업은 진행을 sealed `ProgressPhase`(`Starting` 셀 것이 없는 시작 / `Counted` 항목 수 / `Bytes` 바이트, 전체 크기를 알면 `ByteAmount.Known`, 모르는 파일이 섞이면 `PartlyUnknown` / `IndeterminatePhase`)로 보고하고, 작업 종류는 오케스트레이터가 붙여 `ProgressState(operationType, phase)`를 만든다(#388) — 모든 값을 nullable로 담은 한 상태는 있을 수 없는 조합도 표현하므로. 받는 쪽(진행 다이얼로그·알림·다이얼로그 표시 판정)은 단계에 대한 exhaustive `when`으로 가른다.
- 퍼센트를 낼 수 없는 단계는 `IndeterminatePhase`로 보고하며, 진행 다이얼로그·알림은 퍼센트 대신 움직이는 막대와 단계별 문구(exhaustive `when`)를 보인다: 처리 전에 대상을 훑는 준비 단계(`Preparing`, 찾은 항목 수)와 압축 해제의 비밀번호 확인(`CheckingPassword`, 압축 파일 이름 — `.claude/rules/archive.md`). 시간 간격 보고는 `IndeterminateProgressReporter` 하나가 맡는다. 진행 보고가 곧 취소를 확인하는 자리다(코루틴 `Mutex`는 잠금을 바로 얻으면 취소를 확인하지 않는다). 그래서 걸리는 시간이 서버 목록에 달린 FTP 트리 수집(폴더 다운로드·재귀 삭제)은 받은 항목마다 `PreparationProgressReporter`에 알리고(#284), 출력 없이 내용을 푸는 동안(비밀번호 확인, solid RAR이 건너뛴 항목을 푸는 동안)도 라이브러리가 진행을 알릴 때마다 보고한다(#418). 원격 삭제는 진행 표시·보호·취소가 있는 삭제 작업(`FileOperationType.REMOTE_DELETE` — 문구는 로컬 삭제와 같고, 진행 알림을 누르면 원격 화면으로 가도록 로컬 `DELETE`와 나눈다, #466)이며(#334), 진행을 작업 전체 기준으로 보고하도록 고른 대상을 한 번에 저장소에 맡긴다(`RemoteFileMutationRepository.delete` — 폴더 안쪽 수는 구현만 안다). 로컬 작업(복사·이동·삭제·압축·압축 해제)과 폴더 업로드의 사전 순회는 준비 단계를 보고하지 않는다 — 링크를 따라가지 않아 실제 트리 크기만큼만 걸리므로, 그동안 진행 표시가 시작 상태에 머물고 취소가 순회가 끝난 뒤에 듣는 것은 지원 범위 밖이다.
- 로컬 트리 순회·심볼릭 링크(로컬·FTP)·쓰다 만 파일은 `.claude/rules/local-tree.md`에 있다 — 해당 코드를 다룰 때 자동으로 로드된다.
- 로컬: java.io의 IOException을 결과로 바꾸는 곳은 `localIo` 하나다. 작업이 정한 실패(디스크 공간 부족 등)는 결과로 돌려준다. 결과를 돌려줄 수 없는 곳(라이브러리 콜백 안, IOException으로 실패를 알리는 단계)에서 앱이 정한 실패는 문장을 담은 IOException이 아니라 원인을 담은 `LocalFailureException`으로 던지며, `localIo`(문맥을 붙이는 `describe`를 줘도)와 `ioFailureReasonOf`가 그 원인을 쓴다 — 앱이 쓴 문장이 `IoFailed`로 화면에 새지 않도록. `IoFailed`의 문구는 시스템·라이브러리가 정한 메시지뿐이다.
- 로컬: rename으로 기존 파일을 교체하는 곳은 `replaceFileAtomically`(`ATOMIC_MOVE` rename) 하나다(#432) — `REPLACE_EXISTING`은 Unix에서 대상을 먼저 지운 뒤 rename해, 실패하면 대상만 사라진다.
- 로컬: 원본을 지우는 교차 파티션 이동은 복사본과 그 이름(부모 폴더)을 장치에 기록(`forceToDisk`, fsync)한 뒤에만 원본을 지운다(#430) — 닫은 출력은 OS 캐시에만 있을 수 있어, 지운 직후 전원이 끊기거나 매체가 빠지면 둘 다 잃는다. 기록하지 못하면 원본을 남기고 실패한다. 원본이 남는 쓰기(복사·압축·압축 해제·다운로드)는 기록을 기다리지 않는다.

### Data Layer

- **DataStore** (`data/datastore/DataStoreRepository`): 소비자는 `SortPreferenceRepository` 인터페이스로만 의존한다. 원격 연결은 저장하지 않는다 — 연결은 사용자가 저장소 화면에서 서버를 고를 때만 시작한다(`.claude/rules/remote-connection.md`).
- **설정 저장소 실패**: DataStore 접근의 실행 위치·재시도·실패 정책은 `PreferencesStore` 한 곳에서 정한다.
  - 결과로 돌려줄 수 없는 실패는 `PreferenceFailureNotices`(domain `PreferenceFailureRepository`)에 알림으로 넣는다. 공유 `data`의 읽기 실패는 기본값으로 대신하고 백오프로 다시 읽으며, 저장 실패는 이전 값을 남기고, 손상 파일 초기화는 `CorruptionReset`이 교체가 쓰인 뒤 알린다.
  - 화면 호스트는 화면이 보이는 동안 `PreferenceFailureUseCase.deliver`로 알림을 꺼내 Snackbar로 알린다. 먼저 꺼낸 한 곳만 알리며, 알리기 전에 회복한 읽기 실패는 목록에서 빠진다.
  - 편집은 대기열 하나로 호출 순서대로 반영한다. IOException이 아닌 편집 예외(구현 오류)는 대기열 처리를 끝내 appScope로 전파하고, 대기열을 그 예외로 닫는다(#211): 닫힌 뒤의 편집은 그 예외로 실패한다 — 닫힌 대기열이 받기만 하지 않도록. 이미 대기열에 들어간 편집은 반영되지 않으며, 그 실패는 appScope로 전파된 예외로만 드러난다.
- **`DataConverter`**: Sorts raw `FileEntry` lists and maps them to domain `FileHolderItem` (원시값만 보유). 크기·날짜 표시 문자열 포맷은 app 레이어 UI 모델(`ui/model/FileHolderItem.from`, `StorageHolderItem.from`)이 담당한다.

### `MyApplication`

static `appContext`는 제거됨 — 모든 Context 의존은 Hilt `@ApplicationContext` 주입으로 처리.

### Independent Utility Functions (`tools/independent/`)

Top-level `_td`-suffixed functions are pure/standalone helpers: 크기 표시 형식(`readableFileSize_td`)은 app `FileSizeFormat.kt`에 있다. The `_td` suffix marks them as authored by Todokanai and free of framework dependencies.

## Domain Module Design Principles

- domain 모듈의 Repository 인터페이스와 UseCase는 로컬 파일시스템뿐 아니라 NAS 등 원격 스토리지도 구현체로 교체할 수 있도록 Android/로컬 I/O에 직접 의존하지 않는다.
- 파일 경로는 `String` (절대 경로 또는 URI 문자열)으로 추상화하여 원격 경로도 동일하게 표현할 수 있도록 한다.
- domain과 app은 경로를 합성·분해하지 않는다(구분자를 가정하지 않는다). 저장소 계약은 목적지를 "디렉터리 + 이름"으로 받아 경로를 구현이 만들고, 항목 이름은 목록이 준 `FileHolderItem.name`을, 부모·표시 이름은 `StoragePathRules.getParentPath` / `displayNameOf`(탐색 상태와 분리된 경로 규칙, 구현은 저장소별 `PathRuleFileSystem`)를 쓴다. 예외는 실패 메시지용 표시 경로(`FailedItem.path`)뿐이다.
- 사용자가 입력한 이름의 규칙은 둘로 나뉜다: 앱이 모든 저장소에 강제하는 공통 규칙은 `FileNameUseCase`, 저장소의 파일 시스템이 정하는 제약(길이 한도 등)은 이름이 놓일 위치로 고르는 `StorageNameRules`(구현 `FileSystemNameRules` → 경로별 `NameRuleFileSystem.nameViolationsOf`, 로컬·원격 화면별 바인딩)가 판정한다. 위치는 새 항목이면 만들 폴더, 이름 변경이면 그 항목 자신이다.
- 원격 스토리지 구현체는 data 모듈에 별도 클래스로 작성하고, Hilt 바인딩만 교체하여 전환 가능하게 유지한다.
- 저장된 원격 서버는 `RemoteStorageItem.settings`(sealed `RemoteProtocolSettings`)로 프로토콜과 그 프로토콜의 설정을 함께 갖는다. 프로토콜을 추가하면 exhaustive `when`이 처리할 곳을 컴파일 오류로 알려 준다. 여러 프로토콜의 동시 지원은 범위 밖이며, 두 번째 프로토콜을 구현할 때 저장 항목의 프로토콜로 구현을 고르는 라우터를 함께 추가한다.

## Feature Roadmap

See [ROADMAP.md](ROADMAP.md).

## Compose Configuration

- **Strong Skipping** is enabled. All `@Composable` functions benefit from strong-skipping optimization — unstable parameters no longer unconditionally force recomposition. Avoid adding `@Stable`/`@Immutable` annotations solely to enable skipping; strong skipping already covers the common case.

## Key Conventions

- 정렬 모드는 domain `SortMode` enum이다. `SortMode.id`는 DataStore 저장 전용(변경 금지)이며, 표시 문구는 app 레이어 exhaustive `when` → 문자열 리소스로 매핑한다.
- 작업 종류는 `FileOperationType` enum. 진행 알림 Intent extra `EXTRA_ACTION_KEY`에는 `FileOperationType.id`를, `EXTRA_INSTANCE_ID`에는 작업 인스턴스 ID를, 알림 id와 PendingIntent requestCode에는 `OperationNotificationIds`가 붙인 알림 id를 사용한다(알림마다 자기 작업을 가리키고, 인스턴스 ID가 프로세스마다 다시 발급돼도 이전 프로세스가 남긴 알림과 겹치지 않도록).
- 진행 알림은 그 작업을 실행한 프로세스만 지울 수 있다(#300). 그래서 작업을 가리키는 클릭 intent에는 만든 프로세스의 값(`NotificationProcessToken`, `EXTRA_PROCESS_TOKEN`)을 담고, `MainActivity`는 다른 프로세스의 intent를 무시한다(앱만 연다). 프로세스가 시작될 때(`MyApplication.onCreate`) 남아 있는 진행 알림은 죽은 프로세스의 작업이므로 `MyNotification.interruptLeftoverProgress`가 같은 id의 중단 알림(밀어서 지울 수 있음)으로 바꾼다. 프로세스가 죽은 뒤 앱이 다시 시작되기 전까지 남은 진행 알림은 정리하지 못한다(지원 범위 밖 — 실행 중인 코드가 없다).
- 진행률 다이얼로그는 한 번에 한 작업만 띄운다(`ProgressDialogSelection`). 새 작업이 시작되면 가장 나중에 시작한 작업을 띄우고, 닫거나 작업이 끝나도 다른 작업으로 넘어가지 않는다. 앱을 열 때(ViewModel 생성 시) 이미 진행 중인 작업은 새 작업으로 보지 않는다. 알림을 누르면 그 알림의 작업을 띄운다.
- Compose "fragments" (`*Frag`) are stateless composables that receive a ViewModel; they are not Android `Fragment` classes.
- Compose "holders" (`*Holder`) are item-level composables for list rows.
