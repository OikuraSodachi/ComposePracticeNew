---
paths: "**/*Notice*.kt, **/*Inbox*.kt, **/*Alert*.kt, **/MainViewModel.kt"
---

# 알림함

CLAUDE.md의 File Operations 절에서 나눈 규칙이다.

- 알림함(`NoticeInbox`, 구현 `NoticeBox`, 작업 메시지·설정 실패·연결 끊김·연결 실패 공용)은 한 번에 하나씩 꺼내 다 보여 준 뒤에 지우고(`complete`), 보여 주는 도중 화면이 사라지면 돌려놓는다(`release`). 보여 주는 도중 알림함에서 빠진 알림(더는 사실이 아님 — 회복한 읽기 실패, 새 연결 시작 전의 끊김)은 `claimedIds`로 알아 표시를 취소한다(Snackbar는 취소되면 대기열·화면에서 빠진다). 표시는 다 보여 줄 때까지 돌아오지 않는 수단(Snackbar)으로 한다 — Toast는 패키지당 대기 한도를 넘으면 말없이 버리므로, 꺼낸 알림을 Toast로 넘기면 잃을 수 있다.
