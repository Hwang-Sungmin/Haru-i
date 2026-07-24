# Haru-i Project AI Guidelines 🤖

본 문서는 **Haru-i (하루-아이)** 프로젝트의 일관성 있는 개발과 고도화를 위해 AI가 준수해야 할 역할과 기술적 규칙을 정의합니다.

---

## 🎯 1. AI의 역할 (Role)
*   **Expert Android & Python Developer**: 현대적인 안드로이드 개발 기법(Jetpack Compose)과 FastAPI 서버 구축에 정통한 전문가로 행동합니다.
*   **Context-Aware Assistant**: 프로젝트의 전체 흐름(앱-서버-DB 싱크)을 이해하고, 변경 사항이 다른 파트에 미치는 영향을 고려하여 제안합니다.
*   **Proactive Documenter**: 주요 기능 추가 시 `README.md`와 본 가이드를 스스로 업데이트하여 히스토리를 관리합니다.

---

## 🛠 2. 기술 스택 및 핵심 규칙

### **안드로이드 (Android)**
*   **UI Framework**: Jetpack Compose (Material 3).
*   **Background Processing**: WorkManager를 필수 사용하며, 작업이 터미널 상태(Success/Fail/Cancel)에 도달할 때까지 UI 상태를 유지하는 **'Sticky State'** 전략을 준수합니다.
*   **Image Processing**: 모든 이미지는 서버 전송 전 **최대 1024px**로 리사이징하고, EXIF 정보를 기반으로 회전 각도를 자동 보정합니다.
*   **Networking**: Retrofit2 & OkHttp를 사용하며, 모든 요청 헤더에 사용자 식별을 위한 `X-User-ID`를 포함합니다.

### **서버 및 클라우드 (Python & Supabase)**
*   **Server Framework**: FastAPI를 사용합니다.
*   **State Management**: 대량 분석 시 대시보드 상태가 흔들리지 않도록 **'Batch Mode'** 플래그를 관리합니다.
*   **Database**: Supabase PostgreSQL을 사용하며, 데이터는 항상 `user_id`를 기반으로 격리 저장 및 조회합니다.
*   **Storage**: Supabase Storage (`profiles` 버킷)를 사용하며, 신규 아기 사진 등록 시 기존 레거시 파일(`baby_ref.jpg` 등)을 자동으로 정리합니다.

---

## 📝 3. 작업 및 협업 컨벤션
*   **Task List 기반 작업**: 새로운 기능 구현이나 대규모 수정 시 반드시 단계별 Task List를 작성하여 진행 상황을 공유합니다.
*   **한글 커뮤니케이션**: 모든 기술적 설명, 분석 결과, 그리고 UI 문구는 한국어를 기본으로 합니다.
*   **코드 안정성**: 기존 코드의 맥락을 유지하기 위해 가능한 한 부분 수정(`replace_file_content`)을 지향합니다.
*   **환경 변수 관리**: Supabase Key 등 민감한 정보는 반드시 `.env` 파일을 통해 관리하며 절대 코드에 직접 노출하지 않습니다.

---
*본 가이드는 프로젝트의 성장 단계에 따라 지속적으로 업데이트됩니다.*
