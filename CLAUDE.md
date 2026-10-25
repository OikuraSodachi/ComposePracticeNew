# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A File Manager app built with Jetpack Compose (Android). Package: `com.todokanai.composepracticenew`. minSdk 30, targetSdk 35, compileSdk 34, Java 17 toolchain.

## Build & Test Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Unit tests
./gradlew test

# Instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Lint
./gradlew lint

# Run a single unit test class
./gradlew test --tests "com.todokanai.composepracticenew.ExampleUnitTest"
```

## Architecture

**Single Activity + Compose, MVVM + Hilt DI.**

SOLID 원칙을 준수한다.

`MainActivity` owns six ViewModels and renders `HomeScreen`, which switches between `StorageFrag` (storage picker) and the main file browser (`OptionFrag` + `DirectoryFrag` + `FileListFrag`).

### Global State — `@Singleton` Repositories

Cross-ViewModel reactive state lives as `MutableStateFlow` properties inside `@Singleton` repository implementations in the data module:

| Repository | Key State | Purpose |
|---|---|---|
| `FileNavigator` | `currentPath`, `directoryListing`, `dirTree` | Navigation and directory listing |
| `ProgressTracker` | `progressState` | Ongoing operation progress |
| `StorageRepositoryImpl` | `storageList` | Available storage volumes |

`navigate(path)` is the canonical way to navigate — it sets `currentPath` (and thus `dirTree`) and a new `ListingRequest`; `directoryListing` follows asynchronously as the result of that request.

`directoryListing` is a `DirectoryListing` state (`NoPath` / `Loading` / `Loaded` / `Failed`), never a bare list: a listing failure must not appear as an empty directory.

- 목록 상태는 `ListingRequest(path, generation)`에 묶인다. 이동·새로고침·재연결·공유 재시작마다 새 요청이 되며, 같은 경로라도 이전 요청의 `Loaded`는 현재 목록이 아니다. 같은 경로를 다시 조회하는 동안의 직전 목록은 `Loading.stale`(표시 전용)이다.
- 정렬은 조회 결과 위에 적용한다(정렬 변경으로 다시 조회하지 않는다).
- 이름 충돌 판정은 `ConflictCheckUseCase`만 한다. 확인된 목록(`FileNavigatorUseCase.verifiedItems()`, domain internal)이 없으면 판정하지 않고 거부를 알린 뒤 null을 반환한다. ViewModel은 UI 상태로 충돌을 판정하지 않는다.
- `Loaded.completeness`가 `ListingCompleteness.UNCONFIRMED`인 목록(예: FTP LIST의 450을 빈 폴더로 본 목록)은 화면에는 빈 폴더로 보이지만 항목이 있을 수 있다. 판정 결과(`DestinationConflicts.destinationCompleteness`)에 이 사실을 담고, `ConflictGate`가 충돌이 없어 보여도 덮어쓸 수 있음을 확인받은 뒤에만 실행한다.

### UI Modes (`ui/model/SelectMode`)

선택 모드는 sealed 타입이다. 화면별로 가능한 모드는 `LocalSelectMode` / `RemoteSelectMode`로, 확인 버튼을 갖는 모드는 `ConfirmMode`(라벨 `labelRes`)로 타입을 좁혀 불가능한 조합을 컴파일 단계에서 배제한다.

```
SelectMode.Default / MultiSelect          — 두 화면 공통 (일반 탐색 / 다중 선택)
SelectMode.LocalOperation.Copy/Move/Unzip/UnzipHere — 로컬 화면, 목적지 확인 후 실행 (충돌 검사)
SelectMode.Download                       — 로컬 화면, 다운로드 목적지 확인
SelectMode.Upload                         — 원격 화면, 업로드 목적지 확인
```

화면마다 선택 상태는 `SelectionState<Op, T>` 하나다(`FileTransferCoordinatorViewModel.localSelection` / `remoteSelection`, 전환은 `SelectionStateHolder`). 모드와 항목 목록을 한 값으로 묶어, 목록이 의미 있는 상태에만 둔다.

- `MultiSelect(selected)`, `Operation(operation, selected)` — 이 화면의 선택이 작업 대상(같은 저장소 작업, `SameStorageOperation`).
- `Receiving(transfer, pending)` — 다른 화면에서 고른 항목을 받는 전송 목적지(`TransferMode`: Download/Upload). 이 화면의 선택은 없고 대기 목록만 있다.
- `Default` — 선택도 대기 목록도 없다. 취소·뒤로가기·실행 후 모두 여기로 돌아가며 목록은 함께 사라진다.

UI는 `localMode()` / `remoteMode()`로 모드만 꺼내 분기하고(`derivedStateOf`로 모드 변경 시에만 리컴포지션), 행 강조는 `selected`만 본다. 원격 같은 저장소 작업(복사·이동)을 추가할 때는 원격의 `Op`(지금 `Nothing`)에 해당 타입을 넣는다.

### File Operations

- 로컬: `LocalFileOperationUseCase` → `FileActionRepository` → `LocalFileOperationDataSource`. 구현은 작업 단위 클래스(`LocalTreeTransfer`, `LocalZipCompressor`, `LocalArchiveExtraction` + 형식별 `ArchiveExtractor`(@IntoSet), `LocalEntryMutator`)로 나뉜다.
- 원격: `RemoteFileOperationUseCase` → `RemoteFileTransferRepository` / `RemoteFileMutationRepository` (FTP 구현: `FtpRepositoryImpl`).
- 실행·진행 보고·완료/실패 처리는 `FileOperationOrchestrator`가 담당하며, 작업 인스턴스 ID도 여기서만 발급한다.
- 작업 종류는 `FileOperationType` enum이며, 레이어별 표시 문자열은 enum에 대한 exhaustive `when`으로 매핑한다.
- 파일 입출력(로컬·원격 작업, 목록 조회)의 실패는 예외가 아니라 domain `IoResult`(`Success` / `ItemFailed` / `PartiallyFailed` / `ConnectionLost`)로 돌려준다. 받는 쪽은 exhaustive `when`으로 처리한다. 예외는 취소·구현 오류에만 쓰며, 앱 수명 흐름을 지키기 위해 결과로 바꾸는 곳은 `launchOperation`과 `FileListFlowProducer.load` 두 경계뿐이다.
- 여러 항목 작업은 `forEachCollectingFailures`로 실패를 모은다. `ConnectionLost`면 남은 항목을 시도하지 않는다.
- FTP: 서버 응답 해석(명령·조회 목적별 예외 규칙 포함, 예: 화면 목록의 LIST가 데이터 전송 전에 받은 450 = 빈 목록, 트리 수집에서는 실패)은 `FtpReplyRules`에만 둔다. `FtpSession.withConnectedClient`가 명령 실패의 의미를 정하는 유일한 경계다. 블록이 돌려준 결과는 세션 유지, 던진 IOException은 연결 끊김(세션 종료), 그 밖의 예외는 세션 종료 후 전파한다. 세션을 유지할 실패는 블록 안에서 던지지 말고 결과로 돌려준다. 명령·서버 동작 조합은 `FtpCommandContractTest` 행렬로 검증한다.
- FTP 연결: `FtpConnector.connect`는 로그인 후 `FtpListingFormat.isReadable`로 목록을 해석할 수 있는지 확인한다(MLSD 미지원 서버가 SYST를 거부하거나 해석기 없는 종류를 알려주면 불가). 불가면 형식을 추측하지 않고 연결을 종료해 `ConnectResult.Failed(LISTING_FORMAT_UNKNOWN)`을 돌려주며, app은 `ConnectFailureNotice`로 다이얼로그를 띄운다. 그래서 연결된 세션의 목록 조회는 해석기를 고르지 못해 실패하지 않는다.
- FTP 숨김 항목: LIST 서버의 목록은 디렉터리로 이동(CWD)한 뒤 `FtpListingFormat.listCurrentDirectory`로 `LIST` 또는 `LIST -a .`을 보낸다 — 옵션과 임의의 경로를 함께 보내면 서버마다 해석이 갈리고, 인자가 `-a` 하나뿐이면 그 이름의 항목이 있을 때 경로로 읽는 서버(ProFTPD)가 있다. 연결할 때 `FtpListingFormat.probeHiddenListing`이 현재 디렉터리를 같은 두 명령으로 조회해, `-a` 목록이 숨김 항목만 더한 것일 때만 그 세션의 LIST에 `-a`를 붙인다(`-a`는 RFC 959에 없어 경로로 읽는 서버가 있다). 판정은 연결을 실패로 만들지 않는다 — 판정이 제어 연결을 쓸 수 없게 만들면(`HiddenListingProbe.ConnectionBroken`, 데이터 연결을 열지 못한 실패는 해당 없음) `FtpConnector`가 다시 판정하지 않고 다시 연결해 `-a` 없이 쓰며, 그 끊김이 앱의 종료 요청(`FtpSession.closeBypassingIoLock`) 때문이면 다시 연결하지 않는다.
- FTP 현재 디렉터리: LIST 조회는 세션을 조회한 폴더 안에 남긴다(`FtpSession.workingDirectory`). 로그인 직후의 현재 디렉터리(PWD)는 `FtpSession.homeDirectory`로 기록한다. 폴더를 지우거나 이름을 바꾸는 명령은 세션이 그 대상 안에 있으면 같은 잠금 안에서 먼저 대상 밖(상위 폴더, 거부되면 대상 밖의 홈)으로 CWD한 뒤 보낸다(`FtpCommandExecutor.leavingTarget`) — 현재 디렉터리의 삭제를 거부하는 서버(Apache FTP Server, Windows)를 위한 사전 조치이므로, 나가지 못해도 명령은 보내고 서버의 응답을 결과로 삼는다. 새 변경 명령도 이 규칙을 따른다. MLSD·`-a` 없는 LIST의 목록은 숨김 항목을 빠뜨릴 수 있어(RFC 3659 §7.1) 클라이언트가 누락을 알 수 없다 — 그런 세션(`FtpListingFormat.mayOmitHiddenEntries`)의 RMD 거부 메시지에만 그 가능성을 덧붙인다.
- FTP 종료: FTPClient는 스레드에 안전하지 않으므로 ioMutex를 쥔 쪽만 명령을 보내고 `disconnect()`한다. 진행 중 I/O를 기다리지 않는 종료(`FtpSession.closeBypassingIoLock`)는 잠금 밖에서 소켓(`AbortableSocketFactory`가 만든 제어·데이터 소켓)만 닫는다 — 막힌 명령은 IOException을 받아 자기 잠금 안에서 정리하고, 진행 중인 명령이 없을 때만 종료 쪽이 잠금을 얻어 QUIT을 보낸다. 연결 작업과 겹친 종료 요청은 `FtpSession.markLoggedIn(closeMark)`가 종료 요청과 같은 잠금 안에서 확인해, 연결 작업이 어느 단계에서 끝났든 연결 성공으로 뒤집히지 않는다.
- 원격 연결 상태: `RemoteConnectionRepository.connectionState`(`RemoteConnectionState`: `DISCONNECTED` / `CONNECTED` / `LOST`). 예기치 않은 끊김(`FtpSession.markConnectionDropped`, 연결된 세션에서만)은 사건이 아니라 `LOST` 상태로 다음 연결 시도(`beginConnecting`)나 종료 요청까지 남는다 — 끊긴 뒤에 관찰을 시작한 쪽도 알 수 있도록. 앱이 요청한 종료·서버 전환은 `DISCONNECTED`다. 끊김 처리는 화면 수명과 무관하게 각자 상태를 보고 한다: `FtpForegroundService`는 스스로 멈추고, `FileTransferCoordinatorViewModel`은 끊긴 세션에 기대는 전송 상태(다운로드 대기, 원격 선택·업로드 대기)를 버리고, 원격 화면은 `LOST`면(들어올 때 이미 끊겨 있어도) 저장소 선택으로 나간다. 끊김 Toast는 `MainViewModel.connectionLost`가 끊긴 순간에만 낸다.
- 로컬: java.io의 IOException을 결과로 바꾸는 곳은 `localIo` 하나다. 작업이 정한 실패(디스크 공간 부족 등)는 결과로 돌려준다.

### Data Layer

- **Room** (`data/room/`): `MyDatabase` + `UserDao` / `User` entity. Provided as a singleton via `di/DatabaseModule`.
- **DataStore** (`data/datastore/DataStoreRepository`): Persists `sortBy` (string) and `lastRemoteId` (long) preferences. 소비자는 `SortPreferenceRepository` / `LastConnectionRepository` 인터페이스로만 의존한다.
- **`DataConverter`**: Sorts raw `FileEntry` lists and maps them to domain `FileHolderItem` (원시값만 보유). 크기·날짜 표시 문자열 포맷은 app 레이어 UI 모델(`ui/model/FileHolderItem.from`, `StorageHolderItem.from`)이 담당한다.

### `MyApplication`

Hilt 진입점(`@HiltAndroidApp`). static `appContext`는 제거됨 — 모든 Context 의존은 Hilt `@ApplicationContext` 주입으로 처리.

### Independent Utility Functions (`tools/independent/FileActionModel.kt`)

Top-level `_td`-suffixed functions (e.g., `dirTree_td`, `readableFileSize_td`, `getTotalSize_td`) are pure/standalone helpers. The `_td` suffix marks them as authored by Todokanai and free of framework dependencies.

## Domain Module Design Principles

- domain 모듈의 Repository 인터페이스와 UseCase는 로컬 파일시스템뿐 아니라 NAS 등 원격 스토리지도 구현체로 교체할 수 있도록 Android/로컬 I/O에 직접 의존하지 않는다.
- 파일 경로는 `String` (절대 경로 또는 URI 문자열)으로 추상화하여 원격 경로도 동일하게 표현할 수 있도록 한다.
- 원격 스토리지 구현체는 data 모듈에 별도 클래스로 작성하고, Hilt 바인딩만 교체하여 전환 가능하게 유지한다.

## Feature Roadmap

See [ROADMAP.md](ROADMAP.md).

## Compose Configuration

- **Strong Skipping** is enabled. All `@Composable` functions benefit from strong-skipping optimization — unstable parameters no longer unconditionally force recomposition. Avoid adding `@Stable`/`@Immutable` annotations solely to enable skipping; strong skipping already covers the common case.

## Key Conventions

- 정렬 모드는 domain `SortMode` enum이다. `SortMode.id`는 DataStore 저장 전용(변경 금지)이며, 표시 문구는 app 레이어 exhaustive `when` → 문자열 리소스로 매핑한다.
- 작업 종류는 `FileOperationType` enum. 진행 알림 Intent extra `EXTRA_ACTION_KEY`에는 `FileOperationType.id`를, `EXTRA_INSTANCE_ID`와 PendingIntent requestCode에는 작업 인스턴스 ID를 사용한다(알림마다 자기 작업을 가리키도록).
- 진행률 다이얼로그는 한 번에 한 작업만 띄운다(`ProgressDialogSelection`). 새 작업이 시작되면 가장 나중에 시작한 작업을 띄우고, 닫거나 작업이 끝나도 다른 작업으로 넘어가지 않는다. 앱을 열 때(ViewModel 생성 시) 이미 진행 중인 작업은 새 작업으로 보지 않는다. 알림을 누르면 그 알림의 작업을 띄운다.
- Compose "fragments" (`*Frag`) are stateless composables that receive a ViewModel; they are not Android `Fragment` classes.
- Compose "holders" (`*Holder`) are item-level composables for list rows.
