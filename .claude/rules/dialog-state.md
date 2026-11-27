---
paths: "**/*Dialog*.kt, **/*ViewModel*.kt, **/*Frag.kt, **/*ListView.kt"
---

# 다이얼로그 열림 상태

다이얼로그의 열림 상태는 그 다이얼로그의 대상과 수명이 같은 곳에 둔다(#273) — 수명이 다르면 회전 때 닫히거나, 복원 뒤 대상 없이 열린다.
- 대상이 ViewModel 상태(선택 목록·폴더)인 다이얼로그(목록 하단 버튼의 압축·이름 변경·삭제, 메뉴의 새 폴더·정렬)는 화면 단위 ViewModel이 보관하는 `ScreenDialogState`에 둔다. 회전에는 유지되고, 다른 화면으로 갔다 돌아오거나 프로세스가 종료되면 닫힌다(`rememberScreenDialogState`). 열 때마다 새 `openId`를 발급하고 호스트가 `key(openId)`로 감싸, 저장된 이전 입력값이 새로 연 다이얼로그에 복원되지 않게 한다.
- 대상이 저장된 데이터(원격 서버 id)인 다이얼로그(저장소 화면의 추가·편집·삭제)는 `rememberSaveable`(`StorageDialogState`)로 프로세스 종료 뒤에도 복원한다.
- 계산·작업 상태가 대상인 다이얼로그(정보, 충돌 확인, 진행, 업로드 확인)는 그 상태를 가진 ViewModel이 보관한다.
