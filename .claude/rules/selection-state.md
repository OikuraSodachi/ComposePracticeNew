---
paths: "**/*Selection*.kt, **/SelectMode*.kt, **/FileTransferCoordinator*.kt, **/*Upload*.kt, **/ConflictGate*.kt"
---

# 선택 상태

CLAUDE.md의 UI Modes 절에서 나눈 규칙이다.

화면마다 선택 상태는 `SelectionState<Op, T>` 하나다(`FileTransferCoordinatorViewModel.localSelection` / `remoteSelection`, 전환은 `SelectionStateHolder`). 모드와 항목 목록을 한 값으로 묶어, 목록이 의미 있는 상태에만 둔다.

- `MultiSelect(selected)`, `Operation(operation, selected)` — 이 화면의 선택이 작업 대상(같은 저장소 작업, `SameStorageOperation`).
- `Receiving(transfer, pending)` — 다른 화면에서 고른 항목을 받는 전송 목적지(`TransferMode`: Download/Upload). 이 화면의 선택은 없고 대기 목록만 있다. 전송을 시작하면 받는 화면에 남아 있던 다중 선택·작업 목적지 확인은 묻지 않고 버린다(의도된 동작 — 새 전송이 우선).
- `Default` — 선택도 대기 목록도 없다. 취소·뒤로가기·실행 후 모두 여기로 돌아가며 목록은 함께 사라진다.

선택 초기화 규칙(#226): 선택을 대상으로 하는 작업 함수(삭제·압축·복사·이동·압축 해제)는 목록 대신 `SubmittedSelection`만 받고, 이 값은 선택을 꺼내며 같은 갱신에서 기본 모드로 되돌리는 `SelectionStateHolder.submit()`(coordinator의 `submitLocalSelection` / `submitRemoteSelection`)만 만든다 — 선택을 비우지 않고 작업을 부르는 코드는 컴파일되지 않는다. 한 항목만 받는 작업(이름 변경)은 `SubmittedItem`만 받고, 이 값은 선택이 정확히 한 항목일 때만 꺼내는 `submitSingle()`(`submitLocalItem` / `submitRemoteItem`)만 만든다(#446) — 한 항목이라는 전제를 화면이 아니라 타입이 지킨다. 한 항목이 아니면 선택을 그대로 두고 null이다. `reset`은 취소·뒤로가기용이다. 비우는 시점은 작업 종류로 정한다:
- 같은 저장소 작업은 확인을 누를 때 비운다. 실행 중 충돌 질문에서 취소해도 선택은 돌아오지 않는다(작업은 확인으로 확정이다).
- 이름 변경은 이름 입력 창에서 확인할 때 비운다(창을 취소하면 선택 유지).
- 전송 진입(업로드·다운로드)은 보내는 화면의 선택을 꺼내 받는 화면의 대기 목록으로 넘긴다. 다운로드 대기 목록은 확인할 때 비운다(충돌은 실행 중에 묻는다). 업로드 대기 목록은 확인할 때 원격 화면에서 꺼내 그 확인이 들고 간다(`SelectionStateHolder.takeReceiving`) — 확인(폴더 안쪽 이름 읽기)하는 동안 다음 업로드가 원격 화면에 들어와도 앞의 업로드가 사라지지 않도록. 여러 업로드의 충돌 확인 창은 `ConflictGate`가 차례로 띄운다. 확인이 0.5초를 넘기면 진행 표시를 띄워(`UploadCheckingDialog`) 가장 먼저 시작한 확인을 멈출 수 있게 한다(`cancelUploadCheck`). 확인을 취소·멈췄거나 목록을 확인하지 못했으면 원격 화면이 기본 모드일 때만 대기 목록을 돌려놓아 다시 확인할 수 있게 하고(`restoreReceiving`), 그사이 다른 선택·대기가 자리를 차지했으면 그 업로드는 끝난다.

다중 선택 편집: 길게 누른 뒤 끌기는 목록(`dragSelectGesture`)만 받는다 — 행이 길게 누르기를 감지하면 그 뒤 이벤트를 모두 소비해 목록의 드래그가 취소되므로 행(`FileHolder`)은 `clickable`만 쓴다. 목록은 드래그를 시작하면 손을 뗄 때까지 자식보다 먼저(Initial 패스) 이벤트를 소비해 입력을 독점한다 — 그러지 않으면 손을 뗄 때 행이 탭으로, 움직일 때 목록 스크롤이 드래그로 해석한다. 기본 모드에서 시작하면 다중 선택으로 들어가 범위를 선택하고, 다중 선택 중에 시작하면 시작 항목이 선택돼 있었는지로 범위를 해제하거나 선택한다(`DragSelection` — 범위 밖은 시작 때 상태를 유지). 다중 선택 중 목록 위 `SelectAllBar`는 지금 목록 전체 선택과 모두 해제를 오간다(모두 해제해도 다중 선택 모드는 유지). 모두 `replaceSelection`으로 반영한다.

UI는 `localMode()` / `remoteMode()`로 모드만 꺼내 분기하고(`derivedStateOf`로 모드 변경 시에만 리컴포지션), 행 강조는 `selected`만 본다. 원격 같은 저장소 작업(복사·이동)을 추가할 때는 원격의 `Op`(지금 `Nothing`)에 해당 타입을 넣는다.
