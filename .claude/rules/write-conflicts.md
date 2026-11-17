---
paths: "**/*Conflict*.kt, **/*Question*.kt, **/*Upload*.kt, **/*Rename*.kt, **/*NameMatching*.kt, **/LocalTreeTransfer.kt, **/LocalArchiveExtraction.kt, **/FileOperationOrchestrator.kt, **/*Download*.kt"
---

# 이름 충돌 판정

CLAUDE.md의 Global State 절에서 나눈 규칙이다.

- 로컬에 쓰는 작업(복사·이동·압축 해제·다운로드)의 이름 충돌은 목록이 아니라 쓰는 시점의 파일 시스템 결과로 판정한다(#316). 덮어쓰지 않는 쓰기(`CREATE_NEW`·`createDirectory`, 같은 파티션 이동은 빈 파일로 자리를 확보한 뒤 rename으로 대신)를 먼저 시도하고, 이미 있다고 알릴 때만(`FileAlreadyExistsException`) 저장소 계약이 받은 `WriteConflictResolver`로 묻는다(`LocalConflictWrites.kt`). 같은 항목인지도 파일 시스템이 정하므로, 함께 보내는 항목끼리의 겹침도 같은 길로 묻는다.
  - 처리: 파일은 덮어쓰기(지우지 않고 한 번에 비우며 연다), 폴더끼리는 합치기(안쪽 항목을 다시 하나씩 판정), 파일·폴더가 엇갈리면 건너뛰기만. 압축 해제는 폴더를 묻지 않고 합친다. 이동에서 건너뛴 항목은 원래 자리에 남는다.
  - 묻기: `FileOperationOrchestrator.launchAsking`이 작업 질문함(`OperationQuestionAsker`/`OperationQuestionRepository`, 구현 `OperationQuestions`)에 맡기고, 화면 호스트의 `OperationQuestionSection`이 어느 화면에서든 하나씩 묻는다. 질문함은 충돌과 압축 파일 비밀번호(아래 압축 해제 항목) 질문을 종류와 무관하게 맡긴 순서대로 내보낸다 — 두 다이얼로그가 동시에 뜨지 않도록. 보이는 화면이 없으면 질문은 남아 다시 보일 때 묻고, 그동안 진행 알림은 질문 종류에 맞는 '확인 필요'가 된다. 묻는 동안 작업은 멈춘다.
  - 끝내는 길: 질문에서 취소하면 작업 취소, 질문 하나에 6시간 동안 답이 없으면 실패(`NoticeMessage.UnansweredQuestion`)로 끝난다. 이미 처리한 항목은 그대로 둔다.
  - "나머지에도 적용"은 겹침 종류별로, 그 작업 안에서만 쓴다. 한 번의 확인으로 고른 항목들은 작업 하나로 실행한다(다운로드 포함) — 질문의 취소·진행 다이얼로그의 취소·"나머지에도 적용"이 모두 같은 범위에 걸리도록.
  - 다운로드는 로컬 자리를 FTP 세션 잠금 밖에서 확보한다(답을 기다리는 동안 세션을 붙잡지 않도록). 받기 시작하지 못하면 이 다운로드가 만든 빈 파일만 지운다.
- 업로드와 이름 변경(입력 중 미리 보기·제안 이름 포함)의 충돌은 목록으로 판정하며 `ConflictCheckUseCase`만 한다. 확인된 목록(`FileNavigatorUseCase.verifiedListing()`, domain internal)이 없으면 판정하지 않고 거부를 알린 뒤 null을 반환한다. 읽지 못한 항목이 있는 목록(`DirectoryListing.Loaded.isComplete`가 false — FTP 목록에서 해석하지 못한 줄, #255)도 빠진 항목이 있을 수 있어 확인된 목록이 아니다. ViewModel은 UI 상태로 충돌을 판정하지 않는다. 업로드는 FTP에 "지정한 이름으로, 없을 때만 만들기"가 없어 확인할 때의 목록으로 판정하며, 그 뒤 서버에 생긴 같은 이름의 파일을 덮어쓰는 틈과, 열지 못한 폴더를 2xx 빈 목록으로 답하는 서버(응답 코드로 구분할 수 없음)에서 목록에 드러나지 않은 항목을 덮어쓰는 것(#266)은 지원 범위 밖이다.
- 업로드는 확인할 때 함께 올리는 항목끼리(폴더 안쪽 형제 항목 포함) 목적지 규칙으로 같은 항목이 될 수 있는 이름도 찾아 같은 확인 창에서 알린다(`UploadConflictCheckUseCase`, #323). 폴더 안쪽 이름은 업로드와 같은 순회로 읽는다(`FolderEntryNamesRepository.folderEntryNames`). 이 겹침은 건너뛰기로 뺄 수 없어 계속·취소만 고르며, 계속하면 모두 올린다. 서버가 실제로 두 이름을 합치는지(대소문자·정규형을 구분하지 않는 서버)는 판정하지 않고, 합쳐서 생기는 덮어쓰기는 앱이 책임지지 않는다(지원 범위 밖).
- 목록으로 판정할 때 두 이름이 같은 항목인지는 저장소의 성질이므로 `StorageNameMatching`(구현 `FileSystemNameMatching` → 경로별 `PathAwareFileSystem.nameMatchKeyOf`)의 비교 값으로만 비교한다. 로컬(기기·볼륨마다 비교 방식이 다름)과 FTP(서버 규칙을 물을 표준 명령이 없음)는 대소문자·Unicode 정규형만 다른 이름을 같은 항목으로 묶는다 — 다른 이름으로 잘못 보면 확인 없이 덮어쓰므로.
