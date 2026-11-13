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
- `fileExplorer`: 저장소에서 읽은 항목 모델(`FileEntry`)과 '/' 구분 경로 도우미(`defaultDisplayName`, `defaultParentPath`). data만 의존하는 JVM 모듈이다.
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

읽을 수 없는 경로로의 이동은 현재 경로를 바꾸지 않고 `NavigateResult.Rejected`를 돌려주며, 거부는 탐색 저장소(`FileExplorerRepositoryImpl`)가 `OperationNoticeReporter`로 직접 알린다(#279) — 부르는 곳마다 거부를 확인하지 않아도 알림이 빠지지 않도록. 부르는 곳은 알리지 않고, 이동 뒤에 할 일(저장소 화면에서 목록으로 넘어가기, 검색 닫기)이 있을 때만 `Navigated`인지 본다. 거부할 때마다 알린다 — 같은 거부를 여러 번 하면 알림도 그 수만큼 차례로 보인다(알림함은 같은 내용을 합치지 않는다). 로컬 경로의 부모는 저장소 볼륨 루트에서 끝난다(`LocalPathAwareFileSystem.getParent`) — 볼륨 위 폴더는 앱이 읽을 수 없으므로, 경로 표시줄은 볼륨 루트부터 보이고 볼륨 루트에서 뒤로가기는 아무것도 하지 않는다(의도된 동작, #90 — 뒤로가기로 로컬 목록을 나가지 않는다).

`directoryListing` is a `DirectoryListing` state (`NoPath` / `Loading` / `Loaded` / `Failed`), never a bare list: a listing failure must not appear as an empty directory.

- 목록 상태는 `ListingRequest(path, generation)`에 묶인다. 이동·새로고침·재연결·공유 재시작마다 새 요청이 되며, 같은 경로라도 이전 요청의 `Loaded`는 현재 목록이 아니다. 같은 경로를 다시 조회하는 동안의 직전 목록은 `Loading.stale`(표시 전용)이다.
- 정렬은 조회 결과 위에 적용한다(정렬 변경으로 다시 조회하지 않는다).
- 로컬에 쓰는 작업(복사·이동·압축 해제·다운로드)의 이름 충돌은 목록이 아니라 쓰는 시점의 파일 시스템 결과로 판정한다(#316). 덮어쓰지 않는 쓰기(`CREATE_NEW`·`createDirectory`, 같은 파티션 이동은 빈 파일로 자리를 확보한 뒤 rename으로 대신)를 먼저 시도하고, 이미 있다고 알릴 때만(`FileAlreadyExistsException`) 저장소 계약이 받은 `WriteConflictResolver`로 묻는다(`LocalConflictWrites.kt`). 같은 항목인지도 파일 시스템이 정하므로, 함께 보내는 항목끼리의 겹침도 같은 길로 묻는다.
  - 처리: 파일은 덮어쓰기(지우지 않고 한 번에 비우며 연다), 폴더끼리는 합치기(안쪽 항목을 다시 하나씩 판정), 파일·폴더가 엇갈리면 건너뛰기만. 압축 해제는 폴더를 묻지 않고 합친다. 이동에서 건너뛴 항목은 원래 자리에 남는다.
  - 묻기: `FileOperationOrchestrator.launchResolvingConflicts`가 질문함(`ConflictQuestionAsker`/`ConflictQuestionRepository`, 구현 `ConflictQuestions`)에 맡기고, 화면 호스트의 `ConflictQuestionSection`이 어느 화면에서든 하나씩 묻는다. 보이는 화면이 없으면 질문은 남아 다시 보일 때 묻고, 그동안 진행 알림은 '확인 필요'가 된다. 묻는 동안 작업은 멈춘다.
  - 끝내는 길: 질문에서 취소하면 작업 취소, 질문 하나에 6시간 동안 답이 없으면 실패(`UnansweredConflict`)로 끝난다. 이미 처리한 항목은 그대로 둔다.
  - "나머지에도 적용"은 겹침 종류별로, 그 작업 안에서만 쓴다. 한 번의 확인으로 고른 항목들은 작업 하나로 실행한다(다운로드 포함) — 질문의 취소·진행 다이얼로그의 취소·"나머지에도 적용"이 모두 같은 범위에 걸리도록.
  - 다운로드는 로컬 자리를 FTP 세션 잠금 밖에서 확보한다(답을 기다리는 동안 세션을 붙잡지 않도록). 받기 시작하지 못하면 이 다운로드가 만든 빈 파일만 지운다.
- 업로드와 이름 변경(입력 중 미리 보기·제안 이름 포함)의 충돌은 목록으로 판정하며 `ConflictCheckUseCase`만 한다. 확인된 목록(`FileNavigatorUseCase.verifiedListing()`, domain internal)이 없으면 판정하지 않고 거부를 알린 뒤 null을 반환한다. 읽지 못한 항목이 있는 목록(`DirectoryListing.Loaded.isComplete`가 false — FTP 목록에서 해석하지 못한 줄, #255)도 빠진 항목이 있을 수 있어 확인된 목록이 아니다. ViewModel은 UI 상태로 충돌을 판정하지 않는다. 업로드는 FTP에 "지정한 이름으로, 없을 때만 만들기"가 없어 확인할 때의 목록으로 판정하며, 그 뒤 서버에 생긴 같은 이름의 파일을 덮어쓰는 틈과, 열지 못한 폴더를 2xx 빈 목록으로 답하는 서버(응답 코드로 구분할 수 없음)에서 목록에 드러나지 않은 항목을 덮어쓰는 것(#266)은 지원 범위 밖이다.
- 업로드는 확인할 때 함께 올리는 항목끼리(폴더 안쪽 형제 항목 포함) 목적지 규칙으로 같은 항목이 될 수 있는 이름도 찾아 같은 확인 창에서 알린다(`UploadConflictCheckUseCase`, #323). 폴더 안쪽 이름은 업로드와 같은 순회로 읽는다(`FileInfoRepository.folderEntryNames`). 이 겹침은 건너뛰기로 뺄 수 없어 계속·취소만 고르며, 계속하면 모두 올린다. 서버가 실제로 두 이름을 합치는지(대소문자·정규형을 구분하지 않는 서버)는 판정하지 않고, 합쳐서 생기는 덮어쓰기는 앱이 책임지지 않는다(지원 범위 밖).
- 목록으로 판정할 때 두 이름이 같은 항목인지는 저장소의 성질이므로 `StorageNameMatching`(구현 `FileSystemNameMatching` → 경로별 `PathAwareFileSystem.nameMatchKeyOf`)의 비교 값으로만 비교한다. 로컬(기기·볼륨마다 비교 방식이 다름)과 FTP(서버 규칙을 물을 표준 명령이 없음)는 대소문자·Unicode 정규형만 다른 이름을 같은 항목으로 묶는다 — 다른 이름으로 잘못 보면 확인 없이 덮어쓰므로.

### Navigation (`navigation/NavController.kt`)

`STORAGE` 밖에 `FILE_BROWSER_GRAPH`(`FILE_LIST`, `REMOTE_FILE_LIST`)가 있고, `FileTransferCoordinatorViewModel`은 이 그래프 항목에 스코프된다 — 그래프를 벗어나면(STORAGE 복귀) 전송·선택 상태가 초기화된다.

그래프 안의 백스택 모양은 들어온 경로에 따라 다르며, 이는 의도된 동작이다(하나의 규칙으로 통일하지 않는다):
- 탭 전환(`popUpTo(FILE_BROWSER_GRAPH)`)·저장소에서 원격 연결로 들어온 원격 화면은 그래프에 혼자 있어, 루트에서 뒤로가면 저장소로 나간다.
- 업로드 진입·원격 진행 알림으로 들어온 원격 화면은 `FILE_LIST` 위에 쌓여, 루트에서 뒤로가면 로컬 목록으로 돌아온다(업로드 대기 상태 유지).
- 다운로드 진입은 원격 화면을 꺼내고 로컬 목록으로 간다.

### UI Modes (`ui/model/SelectMode`)

선택 모드는 sealed 타입이다. 화면별로 가능한 모드는 `LocalSelectMode` / `RemoteSelectMode`로, 확인 버튼을 갖는 모드는 `ConfirmMode`(라벨 `labelRes`)로 타입을 좁혀 불가능한 조합을 컴파일 단계에서 배제한다.

```
SelectMode.Default / MultiSelect          — 두 화면 공통 (일반 탐색 / 다중 선택)
SelectMode.LocalOperation.Copy/Move/Unzip/UnzipHere — 로컬 화면, 목적지 확인 후 실행 (같은 이름은 실행 중에 묻는다)
SelectMode.Download                       — 로컬 화면, 다운로드 목적지 확인
SelectMode.Upload                         — 원격 화면, 업로드 목적지 확인
```

화면마다 선택 상태는 `SelectionState<Op, T>` 하나다(`FileTransferCoordinatorViewModel.localSelection` / `remoteSelection`, 전환은 `SelectionStateHolder`). 모드와 항목 목록을 한 값으로 묶어, 목록이 의미 있는 상태에만 둔다.

- `MultiSelect(selected)`, `Operation(operation, selected)` — 이 화면의 선택이 작업 대상(같은 저장소 작업, `SameStorageOperation`).
- `Receiving(transfer, pending)` — 다른 화면에서 고른 항목을 받는 전송 목적지(`TransferMode`: Download/Upload). 이 화면의 선택은 없고 대기 목록만 있다. 전송을 시작하면 받는 화면에 남아 있던 다중 선택·작업 목적지 확인은 묻지 않고 버린다(의도된 동작 — 새 전송이 우선).
- `Default` — 선택도 대기 목록도 없다. 취소·뒤로가기·실행 후 모두 여기로 돌아가며 목록은 함께 사라진다.

선택 초기화 규칙(#226): 선택을 대상으로 하는 작업 함수(삭제·압축·복사·이동·압축 해제·이름 변경)는 목록 대신 `SubmittedSelection`만 받고, 이 값은 선택을 꺼내며 같은 갱신에서 기본 모드로 되돌리는 `SelectionStateHolder.submit()`(coordinator의 `submitLocalSelection` / `submitRemoteSelection`)만 만든다 — 선택을 비우지 않고 작업을 부르는 코드는 컴파일되지 않는다. `reset`은 취소·뒤로가기용이다. 비우는 시점은 작업 종류로 정한다:
- 같은 저장소 작업은 확인을 누를 때 비운다. 실행 중 충돌 질문에서 취소해도 선택은 돌아오지 않는다(작업은 확인으로 확정이다).
- 이름 변경은 이름 입력 창에서 확인할 때 비운다(창을 취소하면 선택 유지).
- 전송 진입(업로드·다운로드)은 보내는 화면의 선택을 꺼내 받는 화면의 대기 목록으로 넘긴다. 다운로드 대기 목록은 확인할 때 비운다(충돌은 실행 중에 묻는다). 업로드 대기 목록은 확인할 때 원격 화면에서 꺼내 그 확인이 들고 간다(`SelectionStateHolder.takeReceiving`) — 확인(폴더 안쪽 이름 읽기)하는 동안 다음 업로드가 원격 화면에 들어와도 앞의 업로드가 사라지지 않도록. 여러 업로드의 충돌 확인 창은 `ConflictGate`가 차례로 띄운다. 확인이 0.5초를 넘기면 진행 표시를 띄워(`UploadCheckingDialog`) 가장 먼저 시작한 확인을 멈출 수 있게 한다(`cancelUploadCheck`). 확인을 취소·멈췄거나 목록을 확인하지 못했으면 원격 화면이 기본 모드일 때만 대기 목록을 돌려놓아 다시 확인할 수 있게 하고(`restoreReceiving`), 그사이 다른 선택·대기가 자리를 차지했으면 그 업로드는 끝난다.

UI는 `localMode()` / `remoteMode()`로 모드만 꺼내 분기하고(`derivedStateOf`로 모드 변경 시에만 리컴포지션), 행 강조는 `selected`만 본다. 원격 같은 저장소 작업(복사·이동)을 추가할 때는 원격의 `Op`(지금 `Nothing`)에 해당 타입을 넣는다.

### File Operations

- 로컬: `LocalFileOperationUseCase` → `FileActionRepository`(`FileActionRepositoryImpl`). 구현은 작업 단위 클래스(`LocalTreeTransfer`, `LocalArchiveCompression` + 형식별 `ArchiveCompressor`(@IntoMap, 키는 domain `ArchiveFormat`), `LocalArchiveExtraction` + 형식별 `ArchiveExtractor`(@IntoSet, 항목 생성·건너뜀 집계는 공통 `ArchiveEntryWriter`로만), `LocalEntryMutator`)로 나뉘고, `FileActionRepositoryImpl`이 이들에 직접 맡긴다(사이에 data source 인터페이스를 두지 않는다 — 교체 지점은 `FileActionRepository` 바인딩).
- 원격: `RemoteFileOperationUseCase` → `RemoteFileTransferRepository` / `RemoteFileMutationRepository` (FTP 구현: `FtpRepositoryImpl`).
- 실행·진행 보고·완료/실패 처리는 `FileOperationOrchestrator`가 담당하며, 작업 인스턴스 ID도 여기서만 발급한다.
- 진행 상태는 `ProgressRepository`(`ProgressTracker`), 사용자에게 알릴 실패·실행 전 거부·완료 메시지는 `OperationNotices`가 맡는다 — 넣는 쪽은 `OperationNoticeReporter`, 꺼내는 쪽은 `OperationNoticeRepository`로만 의존한다. 거부를 알리는 UseCase는 오케스트레이터를 거치지 않고 직접 넣는다. 취소 뒤 정리하지 못한 것(FTP 업로드의 받다 만 파일)도 취소를 결과로 돌려줄 수 없으므로 원격 구현이 직접 넣는다(`.claude/rules/ftp.md`의 전송 중단). FTP 화면 목록에서 해석하지 못한 줄도 목록 결과가 항목만 담으므로 `FtpPathAwareFileSystem`이 직접 넣는다(`.claude/rules/ftp.md`의 목록 해석). 메시지는 흘려보내지 않고 화면이 꺼낼 때까지 보관하며, `AppNavHost`가 화면이 보이는 동안 `OperationNoticeUseCase.deliver`로 꺼내 Snackbar로 알린다(보이는 화면이 없을 때 끝난 작업의 실패도 잃지 않도록).
- 알림함(`NoticeInbox`, 구현 `NoticeBox`, 작업 메시지·설정 실패 공용)은 한 번에 하나씩 꺼내 다 보여 준 뒤에 지우고(`complete`), 보여 주는 도중 화면이 사라지면 돌려놓는다(`release`). 표시는 다 보여 줄 때까지 돌아오지 않는 수단(Snackbar)으로 한다 — Toast는 패키지당 대기 한도를 넘으면 말없이 버리므로, 꺼낸 알림을 Toast로 넘기면 잃을 수 있다.
- 작업 종류는 `FileOperationType` enum이며, 레이어별 표시 문자열은 enum에 대한 exhaustive `when`으로 매핑한다.
- 파일 입출력(로컬·원격 작업, 목록 조회)의 실패는 예외가 아니라 domain `IoResult`(`Success` / `ItemFailed` / `PartiallyFailed` / `ConnectionLost`)로 돌려준다. 받는 쪽은 exhaustive `when`으로 처리한다. 예외는 취소·구현 오류에만 쓰며, 앱 수명 흐름을 지키기 위해 결과로 바꾸는 곳은 `launchOperation`과 `FileListFlowProducer.load` 두 경계뿐이다.
- 여러 항목 작업은 `forEachCollectingFailures`로 실패를 모은다. `ConnectionLost`면 남은 항목을 시도하지 않는다.
- FTP 프로토콜 규칙(응답 해석, 연결·목록 범위, 현재 디렉터리, 트리 수집, 종료)은 `.claude/rules/ftp.md`에 있다 — FTP 코드·테스트를 다룰 때 자동으로 로드된다.
- 원격 연결 상태: `RemoteConnectionRepository.connectionState`(sealed `RemoteConnectionState`: `Disconnected` / `Connected(serverName)` / `Lost`). 연결된 상태는 연결한 서버 이름을 함께 담는다(연결 여부와 서버가 어긋나지 않도록 한 값). 예기치 않은 끊김(`FtpSession.markConnectionDropped`, 연결된 세션에서만)은 사건이 아니라 `Lost` 상태로 다음 연결 시도(`beginConnecting`)나 종료 요청까지 남는다 — 끊긴 뒤에 관찰을 시작한 쪽도 알 수 있도록. 앱이 요청한 종료·서버 전환은 `Disconnected`다. 연결 시도는 시작할 때(`beginConnecting`) 이전 세션이 연결돼 있었어도 `Disconnected`로 둔다 — 연결 작업이 ioMutex를 기다리는 동안 이전 세션의 명령이 실패해도 `Lost`가 되지 않도록. 끊김 처리는 화면 수명과 무관하게 각자 상태를 보고 한다: `FileTransferCoordinatorViewModel`은 끊긴 세션에 기대는 전송 상태(다운로드 대기, 원격 선택·업로드 대기)를 버리고, 원격 화면은 `Lost`면(들어올 때 이미 끊겨 있어도) 저장소 선택으로 나간다. 끊김 Toast는 `MainViewModel.connectionLost`가 끊긴 순간에만 낸다.
- 연결 유지 서비스(`FtpForegroundService`)는 연결 상태에서 파생한다(규칙은 `ConnectionServicePolicy` 한 곳): `MyApplication.observeRemoteConnection`이 `Connected`가 되거나 서버가 바뀔 때 시작하고(알림에 서버 이름), 서비스는 그 밖의 상태가 되면 `startForeground` 뒤에 스스로 멈춘다(`stopSelf(startId)`) — 밖에서 멈추면 `startForeground` 전에 멈춰 앱이 종료될 수 있다. 연결하는 쪽(ViewModel·UseCase)은 서비스를 부르지 않는다. 서비스는 프로세스 보호 전담이며 연결을 맺거나 끊지 않는다(보호를 줄 수 없게 됐을 때의 종료만 예외, 아래 항목) — 서버 전환 중 잠깐 `Disconnected`가 되어 서비스가 멈춰도 새 연결이 끊기지 않도록. 앱 종료(`AppExitHandler`)는 연결 종료를 요청하고, 서비스는 그 상태 변화로 멈춘다.
- 포그라운드 서비스(연결 유지·파일 작업 보호)는 `SelfStoppingForegroundService`를 따른다: 시작은 밖(연결 유지는 MyApplication, 파일 작업 보호는 `FileOperationProtection`)에서 받고, 멈춤은 `startForeground` 뒤에 스스로 한다(규칙은 `ConnectionServicePolicy` / `FileOperationServicePolicy`). 두 서비스는 dataSync라 Android 15의 실행 시간 한도(백그라운드 24시간 중 6시간, 같은 유형끼리 공유)를 받으며, `onTimeout`에서 바로 멈춘다. 연결 유지 서비스는 그때 연결된 세션을 끊김(`RemoteConnectionRepository.drop` → `Lost`)으로 종료하고, 끝낸 연결이 있을 때만 시스템 알림으로 알린다. 멈춤은 `onTimeout`이 받은 startId로 한다(아직 전달되지 않은 시작 요청을 무시하지 않도록) — 보호 없이 연결을 남기지 않도록. 이미 시작한 로컬 파일 작업은 보호 없이 계속되고, 끝나지 않은 원격 작업은 그 연결 종료로 남은 명령이 끊김 실패(`ConnectionLost`)로 끝난다(두 서비스가 한도를 함께 쓰므로). 한도 이후 남은 작업의 보호는 앱이 보장하지 않는다(지원 범위 밖). 정상적으로 마무리되지 않은 작업이 남긴 파일의 결함도 보장하지 않는다.
- 진행 표시가 있는 파일 작업은 보호를 받은 뒤에만 시작한다: `FileOperationOrchestrator.launch`가 블록 실행 전에 domain `OperationProtection.acquire`를 기다리고, 거부되면 블록을 실행하지 않고 실패로 끝낸다(순간 작업은 요청하지 않음). 구현 `FileOperationProtection`은 작업마다 시작 요청(요청 id를 Intent extra로)을 보내고, 서비스가 그 요청으로 포그라운드가 됐는지 거부됐는지를 `report`로 받는다 — 실행 중인 서비스를 믿지 않는 것은 그 서비스가 이전 요청 기준으로 멈추는 중일 수 있어서다.
- 시스템의 시작 거부(`ForegroundServiceStartNotAllowedException` — `startForegroundService`의 백그라운드 시작 제한, `startForeground`의 같은 제한·한도 소진)는 `isForegroundStartRefusal` 하나로 판별하고 그 밖의 예외는 잡지 않는다. 서비스 안의 거부는 `SelfStoppingForegroundService`가 그 startId로 멈추고(거부된 `startForeground`도 `startForegroundService` 계약을 채운다) `onStartRefused`로 넘긴다. 연결 유지 쪽은 한도·거부 모두 `ConnectionProtectionLoss`(끊김 종료·알림) 하나를 따르며, 서비스와 `FtpServiceController`가 함께 쓴다.
- 로컬 작업의 트리 순회는 `localTreeTopDown`/`localTreeBottomUp`(`LocalTree.kt`)로만 하고, 항목 종류는 `entryKind`(링크를 따라가지 않음)로 정한다 — `File.walk*`·`File.isDirectory`는 링크를 따라가 선택하지 않은 데이터를 지우거나 옮기고 순환 링크에서 끝나지 않는다(#320). 심볼릭 링크는 지원 범위 밖이다: 삭제는 링크 자신만 지우고, 항목을 하나씩 다루는 작업(복사·이동·압축·업로드)은 링크(선택한 항목 자신 포함)를 건너뛰어 실패로 알린다(`UNSUPPORTED_LINK_REASON`). 이름 변경은 링크를 건너뛰지 않고 바꾸기 전에 실패한다 — 원본·대상이 링크이거나 합칠 폴더 안(겹치는 자리의 대상 포함)에 링크가 있으면 아무것도 옮기지 않는다(합치기에서 링크만 남기면 원래 폴더를 지울 수 없어 이름 변경이 절반만 된다). 목적지 쪽도 같다 — 복사·이동·압축 해제·다운로드가 쓰려는 자리(합칠 폴더 안쪽 포함)에 같은 이름의 링크가 있으면 따라가지 않고 묻지도 않고 그 항목을 건너뛰어 실패로 알린다(`LocalConflictWrites.kt`의 `WriteSlot.Link`, #338). 링크를 따라가면 덮어쓰기가 링크가 가리키는 파일(원본 자신일 수 있다)을 비우고, 합치기가 고른 목적지 밖에 쓰기 때문이다. 사용자가 고른 목적지 폴더 자신은 링크여도 그대로 쓴다(링크로 들어온 폴더). 폴더를 통째로 rename하는 이동·이름 변경은 안쪽을 훑지 않으므로 안쪽 링크도 링크 그대로 함께 옮겨진다. 읽기 전용인 검색·크기 측정은 `walkFileTree`로 훑고 루트 링크만 푼다(`walkRootOf`).
- 원격(FTP)의 심볼릭 링크도 지원 범위 밖이다(#255). 링크를 알아보는 처리는 두지 않는다 — MLSD가 링크로 알리는 줄(`type=OS.unix=symlink`·`slink:<대상>`)은 해석하지 못한 줄로 다뤄 명시적으로 실패하고(`.claude/rules/ftp.md`의 목록 해석), LIST 해석기가 링크로 읽은 항목은 파일로 다룬다(내려받으면 서버가 따라간 대상을 받고, 지우면 링크만 지운다).
- 로컬: java.io의 IOException을 결과로 바꾸는 곳은 `localIo` 하나다. 작업이 정한 실패(디스크 공간 부족 등)는 결과로 돌려준다.
- 쓰다 만 파일: 취소로 멈춘 쓰기는 쓰던 파일을 지운다(복사·파티션이 다른 이동·압축은 `deletingOnCancel`, 압축 해제는 `ArchiveEntryWriter.discardFile`, 다운로드는 `DataCommandRunner.run`의 `onCancel`). 입출력 실패로 멈춘 쓰기(복사, 파티션이 다른 이동, 압축, 압축 해제, 받기 시작한 뒤의 다운로드)가 남긴 파일은 지우지 않는다 — 실패는 결과로 알리며, 남은 파일의 정리는 앱이 보장하지 않는다(지원 범위 밖, #319). 받기 시작하기 전에 실패한 다운로드의 빈 파일은 지운다(위 충돌 판정 항목).

### Data Layer

- **DataStore** (`data/datastore/DataStoreRepository`): 소비자는 `SortPreferenceRepository` / `LastConnectionRepository` 인터페이스로만 의존한다.
- **설정 저장소 실패**: DataStore 접근의 실행 위치·재시도·실패 정책은 `PreferencesStore` 한 곳에서 정한다.
  - 결과로 돌려줄 수 없는 실패는 `PreferenceFailureNotices`(domain `PreferenceFailureRepository`)에 알림으로 넣는다. 공유 `data`의 읽기 실패는 기본값으로 대신하고 백오프로 다시 읽으며, 저장 실패는 이전 값을 남기고, 손상 파일 초기화는 `CorruptionReset`이 교체가 쓰인 뒤 알린다.
  - 화면 호스트는 화면이 보이는 동안 `PreferenceFailureUseCase.deliver`로 알림을 꺼내 Snackbar로 알린다. 먼저 꺼낸 한 곳만 알리며, 알리기 전에 회복한 읽기 실패는 목록에서 빠진다.
  - 결과를 받는 읽기(`readOnce` → `SavedRemoteId.Unreadable`)의 실패는 알림에 넣지 않고 결과로 돌려주며, 받은 화면이 알린다.
  - 편집은 대기열 하나로 호출 순서대로 반영하고, `readOnce`는 앞서 호출한 편집이 끝난 뒤 읽는다. IOException이 아닌 편집 예외(구현 오류)는 대기열 처리를 끝내 appScope로 전파하고, 대기열을 그 예외로 닫는다(#211): 닫힌 뒤의 편집·`readOnce`와 대기열에 남은 `readOnce`는 그 예외로 실패한다 — 닫힌 대기열이 받기만 하거나 끝없이 기다리게 하지 않도록. 이미 대기열에 들어간 편집은 반영되지 않으며, 그 실패는 appScope로 전파된 예외로만 드러난다.
- **`DataConverter`**: Sorts raw `FileEntry` lists and maps them to domain `FileHolderItem` (원시값만 보유). 크기·날짜 표시 문자열 포맷은 app 레이어 UI 모델(`ui/model/FileHolderItem.from`, `StorageHolderItem.from`)이 담당한다.

### `MyApplication`

static `appContext`는 제거됨 — 모든 Context 의존은 Hilt `@ApplicationContext` 주입으로 처리.

### Independent Utility Functions (`tools/independent/`)

Top-level `_td`-suffixed functions are pure/standalone helpers: 로컬 파일 도우미(`dirTree_td`, `getPhysicalStorage_td` 등)는 data `FileActionModel.kt`, 크기 표시 형식(`readableFileSize_td`)은 app `FileSizeFormat.kt`에 있다. The `_td` suffix marks them as authored by Todokanai and free of framework dependencies.

## Domain Module Design Principles

- domain 모듈의 Repository 인터페이스와 UseCase는 로컬 파일시스템뿐 아니라 NAS 등 원격 스토리지도 구현체로 교체할 수 있도록 Android/로컬 I/O에 직접 의존하지 않는다.
- 파일 경로는 `String` (절대 경로 또는 URI 문자열)으로 추상화하여 원격 경로도 동일하게 표현할 수 있도록 한다.
- domain과 app은 경로를 합성·분해하지 않는다(구분자를 가정하지 않는다). 저장소 계약은 목적지를 "디렉터리 + 이름"으로 받아 경로를 구현이 만들고, 항목 이름은 목록이 준 `FileHolderItem.name`을, 부모·표시 이름은 `StoragePathRules.getParentPath` / `displayNameOf`(탐색 상태와 분리된 경로 규칙, 구현은 저장소별 `PathAwareFileSystem`)를 쓴다. 예외는 실패 메시지용 표시 경로(`FailedItem.path`)뿐이다.
- 사용자가 입력한 이름의 규칙은 둘로 나뉜다: 앱이 모든 저장소에 강제하는 공통 규칙은 `FileNameUseCase`, 저장소의 파일 시스템이 정하는 제약(길이 한도 등)은 이름이 놓일 위치로 고르는 `StorageNameRules`(구현 `FileSystemNameRules` → 경로별 `PathAwareFileSystem.nameViolationsOf`, 로컬·원격 화면별 바인딩)가 판정한다. 위치는 새 항목이면 만들 폴더, 이름 변경이면 그 항목 자신이다.
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
