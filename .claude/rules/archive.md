---
paths: "**/*Archive*.kt, **/*Zip*.kt, **/*Password*.kt"
---

# 압축 비밀번호

CLAUDE.md의 File Operations 절에서 나눈 규칙이다.

- 압축 비밀번호: 압축은 비밀번호를 받으면 파일 항목을 AES-256으로 암호화한다(ZIP은 이름을 암호화하지 않는다). ZIP 읽기·쓰기는 zip4j 하나로 한다. 압축 해제는 암호화된 항목을 만나면 압축 파일마다 따로 묻고(`ArchivePasswordProvider` → 작업 질문함, 아카이브마다 `ArchivePasswordSession`), 비밀번호는 출력을 만들기 전에 내용이 있는 가장 작은 암호화 항목(`passwordCheckEntryOf`, ZIP·RAR 공통 — 0바이트 항목은 풀 내용이 없어 비밀번호를 가리지 못한다)을 끝까지 풀어 보아 확인한다 — 검증값(AES 2바이트, ZipCrypto 1바이트)만으로는 틀린 비밀번호가 통과할 수 있어서다. 확인하는 동안은 `IndeterminatePhase.CheckingPassword`로 진행을 보고하고 그 보고에서 취소를 확인한다(`ArchivePasswordSession.reportChecking` — ZIP은 읽기마다, RAR은 기록·`setCompleted`마다, #418) — solid RAR은 확인할 항목 앞의 항목을 출력 없이 끝까지 풀어 오래 걸릴 수 있어서다. 같은 이유로 solid RAR 추출에서 건너뛴 항목을 푸는 동안에도 `setCompleted`마다 지금 진행을 다시 보고한다(`ByteProgressTracker.reportCurrent`). 목록 암호화 RAR을 열 때의 재시도는 헤더만 읽으므로 보고하지 않는다. 틀리면 '틀렸습니다'(검증값 불일치)나 '틀렸거나 손상'(내용 검증 실패 — RAR4·ZipCrypto는 틀린 비밀번호와 손상을 구분할 수 없다)으로 다시 묻고, 끝내는 길은 취소·무응답뿐이다. 확인한 비밀번호는 그 압축 파일 안에서만 다시 쓴다. 한 압축 파일 안에서 항목마다 다른 비밀번호는 ZIP·RAR 모두 지원 범위 밖이다 — 확인한 비밀번호가 맞지 않는 항목은 다시 묻지 않고 풀 수 없는 항목으로 실패한다. RAR의 목록 암호화는 열 때 묻는다. ZIP 항목 이름은 UTF-8로만 읽으며, 해석하지 못한 이름(U+FFFD)이 있으면 아무것도 풀지 않고 실패한다.
