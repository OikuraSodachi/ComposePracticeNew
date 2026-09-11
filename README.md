# ComposePractice

FileManager를 jetpack compose로 재현하는걸 목표로 만드는 중

## 기술 스택

### 아키텍처
- **패턴**: MVVM + Clean Architecture
- **모듈 구성**: `app` / `data` / `domain` / `fileExplorer` 멀티 모듈
- **minSdk**: 30 / **targetSdk**: 35 / **Java**: 17

### UI
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — 선언형 UI
- [Material 3](https://m3.material.io/) — 디자인 시스템
- [Navigation Compose](https://developer.android.com/jetpack/compose/navigation) — 화면 전환
- [ConstraintLayout Compose](https://developer.android.com/reference/kotlin/androidx/constraintlayout/compose/package-summary) — 복잡한 레이아웃

### 의존성 주입
- [Hilt](https://dagger.dev/hilt/) — DI 컨테이너 (KSP + KAPT)

### 비동기
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) — 비동기 처리 및 Flow 기반 상태 관리

### 로컬 저장소
- [Room](https://developer.android.com/training/data-storage/room) — 로컬 데이터베이스
- [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore) — 설정 값 영속화

### 이미지 로딩
- [Coil](https://coil-kt.github.io/coil/) — Compose 전용 이미지 로딩
- [Glide](https://bumptech.github.io/glide/) — 이미지 로딩 (뷰 레이어)

### 파일 처리
- [zip4j](https://github.com/srikanth-lingala/zip4j) — ZIP 압축 / 해제
- [Apache Commons Net](https://commons.apache.org/proper/commons-net/) — FTP 네트워크 파일 접근
