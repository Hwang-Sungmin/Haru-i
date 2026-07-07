# Haru-i (하루-아이) 👶📸

**Haru-i**는 백엔드 서버 없이 동작하는 안드로이드 네이티브 아기 사진 관리 앱입니다. 기기 내 수많은 사진 중 아기 사진만 정교하게 찾아내고, 시간 순서에 따른 감성적인 타임라인 뷰를 제공합니다.

## 🚀 주요 기능

### 1. 스마트 갤러리 & 타임라인 (MediaStore & Compose)
*   **월별 그룹화**: 촬영 날짜를 분석하여 '연도-월'별로 사진을 자동 분류합니다.
*   **Sticky Header**: 스크롤 시 현재 보고 있는 월 정보가 상단에 고정되어 탐색이 편리합니다.
*   **스크롤 유지**: 탭 전환 시에도 이전에 탐색하던 위치를 정확히 기억하여 최상의 UX를 제공합니다.

### 2. 고도화된 아기 사진 판별 (AI Smart Filter)
*   **2단계 정밀 분석**: Google ML Kit의 **Face Detection**과 **Image Labeling**을 결합했습니다.
    *   1단계: 사진 내 인물 얼굴 유무 감지
    *   2단계: AI가 사진 내용을 분석하여 `Baby`, `Infant`, `Toddler` 등 아기 관련 라벨을 확인
*   **선택적 분석 (성능 최적화)**: 수만 장의 사진을 한꺼번에 분석하는 대신, 사용자가 원하는 월만 선택하여 분석할 수 있어 배터리와 메모리 소모를 획기적으로 줄였습니다.

### 3. 즐겨찾기 및 하이라이트 (Room DB)
*   **로컬 영구 저장**: Room DB를 연동하여 즐겨찾기 상태를 앱 종료 후에도 유지합니다.
*   **하이라이트 섹션**: 즐겨찾기한 소중한 사진들만 모아서 상단 가로 스크롤 섹션에서 감상할 수 있습니다.

### 4. 감성적인 디자인 (Material 3)
*   **적응형 아이콘**: 귀여운 아기 얼굴을 형상화한 전용 적응형 아이콘(Adaptive Icon)을 적용했습니다.
*   **M3 가이드라인**: Material Design 3 기반의 컴포넌트와 파스텔 톤 색감을 사용하여 부드럽고 직관적인 UI를 구현했습니다.

## 🛠 기술 스택

*   **Language**: Kotlin (2.0.21)
*   **UI Framework**: Jetpack Compose (Material 3)
*   **Architecture**: MVVM 패턴
*   **Local Database**: Room DB (KSP 기반)
*   **Image Loading**: Coil
*   **AI/ML**: Google ML Kit (Face Detection, Image Labeling)
*   **Build System**: Gradle (Kotlin DSL, AGP 8.7.3)

## 📱 권한 및 설정
*   **권한**: `READ_MEDIA_IMAGES` (Android 13+), `READ_EXTERNAL_STORAGE` (Android 12-)
*   **보안**: 외부 AI 연동을 위한 API Key 설정 구조가 마련되어 있으며, 현재는 회사 내부망 보안을 고려하여 공백으로 유지 중입니다. (`Config.kt`)

---
*본 프로젝트는 최신 안드로이드 개발 표준을 준수하며, 100% Kotlin과 Compose로만 작성되었습니다.*
