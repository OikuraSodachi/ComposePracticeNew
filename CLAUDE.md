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

`MainActivity` owns six ViewModels and renders `HomeScreen`, which switches between `StorageFrag` (storage picker) and the main file browser (`OptionFrag` + `DirectoryFrag` + `FileListFrag`).

### Global State — `Variables` companion object

All cross-ViewModel reactive state lives as `MutableStateFlow` properties inside `Variables`'s `companion object`. ViewModels read these flows directly and mutate them through `Variables` instance methods:

| State | Type | Purpose |
|---|---|---|
| `currentPath` | `StateFlow<File>` | Currently displayed directory |
| `fileHolderItemList` | `StateFlow<List<FileHolderItem>>` | Files shown in the list |
| `selectedList` | `StateFlow<List<File>>` | Multi-selected files |
| `selectMode` | `StateFlow<Int>` | Current UI mode (see `Constants`) |
| `myProgressState` | `StateFlow<ProgressState>` | Ongoing operation progress |
| `dirTree` | `StateFlow<List<File>>` | Breadcrumb path segments |

Calling `vars.setCurrentPath(file)` is the canonical way to navigate — it updates `currentPath`, `dirTree`, and `fileHolderItemList` atomically.

### UI Modes (`Constants`)

```
DEFAULT_MODE (10)          — normal browsing
MULTI_SELECT_MODE (11)     — checkbox selection active
CONFIRM_MODE_COPY (12)     — navigate to paste destination, then confirm
CONFIRM_MODE_MOVE (13)     — same for move
CONFIRM_MODE_UNZIP (14/15) — navigate to extract destination
```

### File Operations — `FileAction`

`FileAction` is the single point of entry for all file mutations. ViewModels instantiate it and call its methods:

- Each method wraps its body in `CoroutineScope(Dispatchers.IO).launch { ... }.invokeOnCompletion { onComplete(...) }`
- `onComplete` refreshes the current directory listing and fires a completion notification via `CompletedNotiSorter`
- Progress is reported via a `(ProgressState) -> Unit` callback that flows to `Variables.myProgressState`

Individual operation classes (`CopyAction`, `MoveAction`, `DeleteAction`, `RenameAction`, `NewFolderAction`, `ZipAction`, `UnzipAction`, `OpenAction`) live in `tools/fileaction/` and contain the raw I/O logic.

### Data Layer

- **Room** (`data/room/`): `MyDatabase` + `UserDao` / `User` entity. Provided as a singleton via `di/DatabaseModule`.
- **DataStore** (`data/datastore/DataStoreRepository`): Persists `sortBy` (string) and `copyOverwrite` (boolean) preferences. Accessed directly (not injected) using `MyApplication.appContext`.
- **`DataConverter`**: Transforms raw `File` arrays into `FileHolderItem` / `StorageHolderItem` display models, applying the current sort order.

### `MyApplication`

Exposes a static `appContext: Context` used throughout the app where a `Context` isn't otherwise injectable (e.g., `DataStoreRepository`, `Variables`).

### Independent Utility Functions (`tools/independent/FileActionModel.kt`)

Top-level `_td`-suffixed functions (e.g., `dirTree_td`, `readableFileSize_td`, `getTotalSize_td`) are pure/standalone helpers. The `_td` suffix marks them as authored by Todokanai and free of framework dependencies.

## Key Conventions

- Sort mode constants are strings defined in `Constants` (e.g., `BY_NAME_ASCENDING`).
- Action key constants (`ACTION_KEY_COPY` etc.) are integers used to route progress notifications in `FileListViewModel.progressNoti`.
- Compose "fragments" (`*Frag`) are stateless composables that receive a ViewModel; they are not Android `Fragment` classes.
- Compose "holders" (`*Holder`) are item-level composables for list rows.
