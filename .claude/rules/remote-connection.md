---
paths: "**/*Connection*.kt, **/*Connect*.kt, **/ftp/**, **/*Ftp*.kt, **/RemoteFileList*.kt"
---

# 원격 연결 상태

CLAUDE.md의 File Operations 절에서 나눈 규칙이다.

- 원격 연결 상태: `RemoteConnectionRepository.connectionState`(sealed `RemoteConnectionState`: `Disconnected` / `Connected(serverName)` / `Lost`). 연결된 상태는 연결한 서버 이름을 함께 담는다(연결 여부와 서버가 어긋나지 않도록 한 값). 예기치 않은 끊김(`FtpSession.markConnectionDropped`, 연결된 세션에서만)은 사건이 아니라 `Lost` 상태로 다음 연결 시도(`beginConnecting`)나 종료 요청까지 남는다 — 끊긴 뒤에 관찰을 시작한 쪽도 알 수 있도록. 앱이 요청한 종료·서버 전환은 `Disconnected`다. 연결 시도는 시작할 때(`beginConnecting`) 이전 세션이 연결돼 있었어도 `Disconnected`로 둔다 — 연결 작업이 ioMutex를 기다리는 동안 이전 세션의 명령이 실패해도 `Lost`가 되지 않도록. 끊김 처리는 화면 수명과 무관하게 각자 상태를 보고 한다: `FileTransferCoordinatorViewModel`은 끊긴 세션에 기대는 전송 상태(다운로드 대기, 원격 선택·업로드 대기)를 버리고, 원격 화면은 `Lost`면(들어올 때 이미 끊겨 있어도) 저장소 선택으로 나간다. 끊김 알림은 세션이 연결된 상태를 `Lost`로 바꾸는 곳(`FtpSession`)이 같은 잠금 안에서 알림함(꺼내는 쪽 `ConnectionLossNoticeRepository`, 넣는 쪽 `ConnectionLossNoticeReporter`(data — 넣는 곳이 원격 구현뿐이므로), 구현 `ConnectionLossNotices`)에 넣고, 화면 호스트가 작업 메시지와 같이 Snackbar로 알린다(#281) — 상태 흐름을 관찰해 넣으면 빠르게 이어진 변화에서 끊김을 건너뛸 수 있으므로. 보이는 화면이 없을 때 끊긴 것은 다시 보일 때 알리고, 알리기 전에 새 연결을 시작하면(`beginConnecting`) 빼서 새 연결이 끊긴 것으로 보이지 않게 한다.
- 연결·재연결 실패는 결과를 받은 화면이 이유로 나눠 알린다(`ConnectFailureNotice`): 다시 시도하면 될 수 있는 실패(연결 실패, 재연결할 서버 없음, 설정을 읽지 못한 재연결)는 알림함(넣는 쪽 `ConnectFailureAlertReporter`, 꺼내는 쪽 `ConnectFailureAlertRepository`, 구현 `ConnectFailureAlerts`)에 넣어 화면 호스트가 Snackbar로 알리고(#371 — 실패를 받은 화면이 그사이 사라지거나 보이지 않아도 잃지 않도록), 새 연결 시도를 시작하면 `RemoteConnectionUseCase.connectAndNavigate`가 아직 알리지 않은 실패를 빼며, 서버나 포트를 쓸 수 없는 실패는 그 화면 ViewModel의 다이얼로그 상태로 닫을 때까지 남긴다.
