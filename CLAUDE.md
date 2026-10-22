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

### UI Modes (`ui/model/SelectMode`)

선택 모드는 sealed 타입이다. 화면별로 가능한 모드는 `LocalSelectMode` / `RemoteSelectMode`로, 확인 버튼을 갖는 모드는 `ConfirmMode`(라벨 `labelRes`)로 타입을 좁혀 불가능한 조합을 컴파일 단계에서 배제한다.

```
SelectMode.Default / MultiSelect          — 두 화면 공통 (일반 탐색 / 다중 선택)
SelectMode.LocalOperation.Copy/Move/Unzip/UnzipHere — 로컬 화면, 목적지 확인 후 실행 (충돌 검사)
SelectMode.Download                       — 로컬 화면, 다운로드 목적지 확인
SelectMode.Upload                         — 원격 화면, 업로드 목적지 확인
```

### File Operations

- 로컬: `LocalFileOperationUseCase` → `FileActionRepository` → `LocalFileOperationDataSource`. 구현은 작업 단위 클래스(`LocalTreeTransfer`, `LocalZipCompressor`, `LocalArchiveExtraction` + 형식별 `ArchiveExtractor`(@IntoSet), `LocalEntryMutator`)로 나뉜다.
- 원격: `RemoteFileOperationUseCase` → `RemoteFileTransferRepository` / `RemoteFileMutationRepository` (FTP 구현: `FtpRepositoryImpl`).
- 실행·진행 보고·완료/실패 처리는 `FileOperationOrchestrator`가 담당하며, 작업 인스턴스 ID도 여기서만 발급한다.
- 작업 종류는 `FileOperationType` enum이며, 레이어별 표시 문자열은 enum에 대한 exhaustive `when`으로 매핑한다.
- 파일 입출력(로컬·원격 작업, 목록 조회)의 실패는 예외가 아니라 domain `IoResult`(`Success` / `ItemFailed` / `PartiallyFailed` / `ConnectionLost`)로 돌려준다. 받는 쪽은 exhaustive `when`으로 처리한다. 예외는 취소·구현 오류에만 쓰며, 앱 수명 흐름을 지키기 위해 결과로 바꾸는 곳은 `launchOperation`과 `FileListFlowProducer.load` 두 경계뿐이다.
- 여러 항목 작업은 `forEachCollectingFailures`로 실패를 모은다. `ConnectionLost`면 남은 항목을 시도하지 않는다.
- FTP: 서버 응답 해석(명령·조회 목적별 예외 규칙 포함, 예: 화면 목록의 LIST가 데이터 전송 전에 받은 450 = 빈 목록, 트리 수집에서는 실패)은 `FtpReplyRules`에만 둔다. `FtpSession.withConnectedClient`가 명령 실패의 의미를 정하는 유일한 경계다. 블록이 돌려준 결과는 세션 유지, 던진 IOException은 연결 끊김(세션 종료), 그 밖의 예외는 세션 종료 후 전파한다. 세션을 유지할 실패는 블록 안에서 던지지 말고 결과로 돌려준다. 명령·서버 동작 조합은 `FtpCommandContractTest` 행렬로 검증한다.
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
- 작업 종류는 `FileOperationType` enum. 알림 Intent extra(`EXTRA_ACTION_KEY`)와 PendingIntent requestCode에는 `FileOperationType.id`를 사용한다.
- Compose "fragments" (`*Frag`) are stateless composables that receive a ViewModel; they are not Android `Fragment` classes.
- Compose "holders" (`*Holder`) are item-level composables for list rows.
