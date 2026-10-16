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
| `FileNavigator` | `currentPath`, `fileHolderItemList`, `dirTree` | Navigation and directory listing |
| `ProgressTracker` | `progressState` | Ongoing operation progress |
| `StorageRepositoryImpl` | `storageList` | Available storage volumes |

Calling `nav.setCurrentPath(file)` is the canonical way to navigate — it updates `currentPath`, `dirTree`, and `fileHolderItemList` atomically.

### UI Modes (`ui/model/SelectMode`)

선택 모드는 sealed 타입이다. 모드별 속성(확인 버튼 라벨 `labelRes`)은 하위 타입이 보유하고, 모드별 동작은 exhaustive `when`으로 분기한다.

```
SelectMode.Default            — normal browsing
SelectMode.MultiSelect        — checkbox selection active
SelectMode.Confirm.Copy/Move  — navigate to paste destination, then confirm
SelectMode.Confirm.Unzip/UnzipHere — navigate to extract destination
SelectMode.Confirm.Download/Upload — remote transfer destination
```

### File Operations

- 로컬: `LocalFileOperationUseCase` → `FileActionRepository` → `LocalFileOperationDataSource`. 구현은 작업 단위 클래스(`LocalTreeTransfer`, `LocalZipCompressor`, `LocalArchiveExtraction` + 형식별 `ArchiveExtractor`(@IntoSet), `LocalEntryMutator`)로 나뉜다.
- 원격: `RemoteFileOperationUseCase` → `RemoteFileTransferRepository` / `RemoteFileMutationRepository` (FTP 구현: `FtpRepositoryImpl`).
- 실행·진행 보고·완료/실패 처리는 `FileOperationOrchestrator`가 담당하며, 작업 인스턴스 ID도 여기서만 발급한다.
- 작업 종류는 `FileOperationType` enum이며, 레이어별 표시 문자열은 enum에 대한 exhaustive `when`으로 매핑한다.

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

- Sort mode constants are strings defined in `Constants` (e.g., `BY_NAME_ASCENDING`).
- 작업 종류는 `FileOperationType` enum. 알림 Intent extra(`EXTRA_ACTION_KEY`)와 PendingIntent requestCode에는 `FileOperationType.id`를 사용한다.
- Compose "fragments" (`*Frag`) are stateless composables that receive a ViewModel; they are not Android `Fragment` classes.
- Compose "holders" (`*Holder`) are item-level composables for list rows.
