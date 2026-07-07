# Haru-i (하루-이) 👶📸

**Haru-i**는 백엔드 서버 없이 동작하는 안드로이드 네이티브 아기 사진 관리 앱입니다. 기기의 사진첩에서 아기 얼굴을 자동으로 인식하여 분류하고, 감성적인 타임라인 뷰와 즐겨찾기 기능을 제공합니다.

## 🚀 주요 기능

### 1. 스마트 갤러리 (MediaStore & Coil)
*   기기 내 모든 사진을 최신순으로 로드하여 3열 그리드 형태로 표시합니다.
*   **Coil** 라이브러리를 사용하여 고해상도 사진도 메모리 효율적으로 빠르게 렌더링합니다.

### 2. 아기 사진 스마트 필터링 (Google ML Kit)
*   **ML Kit Face Detection**을 연동하여 사진 속 얼굴 유무를 자동으로 분석합니다.
*   백그라운드 코루틴 분석을 통해 UI 멈춤 없이 아기 사진(얼굴 인식된 사진)만 따로 모아볼 수 있는 전용 탭을 제공합니다.

### 3. 감성적인 타임라인 & 헤더
*   사진을 촬영된 **연도 및 월** 기준으로 자동 그룹화합니다.
*   `LazyVerticalGrid`에 **Sticky Header** 스타일의 날짜 구분선을 적용하여 시각적인 흐름을 개선했습니다.

### 4. 즐겨찾기 및 하이라이트 (Room DB)
*   **Room DB**를 사용하여 사용자가 선택한 사진의 즐겨찾기 상태를 로컬에 영구 저장합니다.
*   화면 상단에 즐겨찾기된 사진들만 모아 보여주는 **하이라이트 섹션(가로 스크롤)**을 제공합니다.

## 🛠 기술 스택

*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose (Material Design 3)
*   **Architecture**: MVVM 패턴
*   **Asynchronous**: Kotlin Coroutines & Flow
*   **State Management**: StateFlow
*   **Local Database**: Room DB (KSP 기반)
*   **Image Loading**: Coil
*   **AI/ML**: Google ML Kit Face Detection
*   **Build System**: Gradle (Kotlin DSL)

## 📱 권한 설정
이 앱은 사진첩 접근을 위해 다음과 같은 권한을 사용합니다:
*   `READ_MEDIA_IMAGES` (Android 13 이상)
*   `READ_EXTERNAL_STORAGE` (Android 12 이하)

---
*본 프로젝트는 Kotlin과 Jetpack Compose의 모범 사례를 따르며, XML 레이아웃을 전혀 사용하지 않는 완전한 선언적 UI로 구성되어 있습니다.*
