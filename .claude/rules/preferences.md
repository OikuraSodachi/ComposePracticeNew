---
paths: "**/datastore/**, **/*Preference*.kt, **/CorruptionReset*.kt, **/AppNotice*.kt"
---

# 설정 저장소 실패

`CLAUDE.md` Data Layer 절의 세부다.

- **설정 저장소 실패**: DataStore 접근의 실행 위치·재시도·실패 정책은 `PreferencesStore` 한 곳에서 정한다.
  - 결과로 돌려줄 수 없는 실패는 `PreferenceFailureNotices`(domain `PreferenceFailureRepository`)에 알림으로 넣는다. 공유 `data`의 읽기 실패는 기본값으로 대신하고 백오프로 다시 읽으며, 저장 실패는 이전 값을 남기고, 손상 파일 초기화는 `CorruptionReset`이 교체가 쓰인 뒤 알린다.
  - 화면 호스트는 화면이 보이는 동안 `PreferenceFailureUseCase.deliver`로 알림을 꺼내 Snackbar로 알린다. 먼저 꺼낸 한 곳만 알리며, 알리기 전에 회복한 읽기 실패는 목록에서 빠진다.
  - 편집은 대기열 하나로 호출 순서대로 반영한다. IOException이 아닌 편집 예외(구현 오류)는 대기열 처리를 끝내 appScope로 전파하고, 대기열을 그 예외로 닫는다(#211): 닫힌 뒤의 편집은 그 예외로 실패한다 — 닫힌 대기열이 받기만 하지 않도록. 이미 대기열에 들어간 편집은 반영되지 않으며, 그 실패는 appScope로 전파된 예외로만 드러난다.
