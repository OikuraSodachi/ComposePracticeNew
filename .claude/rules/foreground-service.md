---
paths: "**/*Service*.kt, **/*Protection*.kt, **/*ServicePolicy*.kt, **/MyApplication.kt, **/AppExitHandler.kt, **/FileOperationOrchestrator.kt"
---

# 포그라운드 서비스

CLAUDE.md의 File Operations 절에서 나눈 규칙이다.

- 연결 유지 서비스(`FtpForegroundService`)는 연결 상태에서 파생한다(규칙은 `ConnectionServicePolicy` 한 곳): `MyApplication.observeRemoteConnection`이 `Connected`가 되거나 서버가 바뀔 때 시작하고(알림에 서버 이름), 서비스는 그 밖의 상태가 되면 `startForeground` 뒤에 스스로 멈춘다(`stopSelf(startId)`) — 밖에서 멈추면 `startForeground` 전에 멈춰 앱이 종료될 수 있다. 연결하는 쪽(ViewModel·UseCase)은 서비스를 부르지 않는다. 서비스는 프로세스 보호 전담이며 연결을 맺거나 끊지 않는다(보호를 줄 수 없게 됐을 때의 종료만 예외, 아래 항목) — 서버 전환 중 잠깐 `Disconnected`가 되어 서비스가 멈춰도 새 연결이 끊기지 않도록. 앱 종료(`AppExitHandler`)는 연결 종료를 요청하고, 서비스는 그 상태 변화로 멈춘다.
- 포그라운드 서비스(연결 유지·파일 작업 보호)는 `SelfStoppingForegroundService`를 따른다: 시작은 밖(연결 유지는 MyApplication, 파일 작업 보호는 `FileOperationProtection`)에서 받고, 멈춤은 `startForeground` 뒤에 스스로 한다(규칙은 `ConnectionServicePolicy` / `FileOperationServicePolicy`). 두 서비스는 dataSync라 Android 15의 실행 시간 한도(백그라운드 24시간 중 6시간, 같은 유형끼리 공유)를 받으며, `onTimeout`에서 바로 멈춘다. 연결 유지 서비스는 그때 연결된 탐색 세션을 끊김(`RemoteConnectionRepository.drop` → `Lost`)으로 종료하고, 끝낸 연결이 있을 때만 시스템 알림으로 알린다. 멈춤은 `onTimeout`이 받은 startId로 한다(아직 전달되지 않은 시작 요청을 무시하지 않도록) — 보호 없이 연결을 남기지 않도록. 이미 시작한 파일 작업은 로컬·원격 모두 보호 없이 계속된다 — 원격 작업은 작업마다 자기 연결에서 실행되고 `drop`은 탐색 세션만 닫으므로(#436, `.claude/rules/ftp.md`의 세션 단위). 한도 이후 남은 작업의 보호는 앱이 보장하지 않는다(지원 범위 밖). 정상적으로 마무리되지 않은 작업이 남긴 파일의 결함도 보장하지 않는다.
- 진행 표시가 있는 파일 작업은 보호를 받은 뒤에만 시작한다: `FileOperationOrchestrator.launch`가 블록 실행 전에 domain `OperationProtection.acquire`를 기다리고, 거부되면 블록을 실행하지 않고 실패로 끝낸다(순간 작업은 `launchInstant`로 실행해 요청하지 않음). 구현 `FileOperationProtection`은 작업마다 시작 요청(요청 id를 Intent extra로)을 보내고, 서비스가 그 요청으로 포그라운드가 됐는지 거부됐는지를 `report`로 받는다 — 실행 중인 서비스를 믿지 않는 것은 그 서비스가 이전 요청 기준으로 멈추는 중일 수 있어서다.
- 시스템의 시작 거부(`ForegroundServiceStartNotAllowedException` — `startForegroundService`의 백그라운드 시작 제한, `startForeground`의 같은 제한·한도 소진)는 `isForegroundStartRefusal` 하나로 판별하고 그 밖의 예외는 잡지 않는다. 서비스 안의 거부는 `SelfStoppingForegroundService`가 그 startId로 멈추고(거부된 `startForeground`도 `startForegroundService` 계약을 채운다) `onStartRefused`로 넘긴다. 연결 유지 쪽은 한도·거부 모두 `ConnectionProtectionLoss`(끊김 종료·알림) 하나를 따르며, 서비스와 `FtpServiceController`가 함께 쓴다.
