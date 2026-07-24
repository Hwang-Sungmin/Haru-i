# Haru-i (하루-아이) 👶📸

**Haru-i**는 AI 기반의 안드로이드 네이티브 아기 사진 관리 앱입니다. 기기 내 수많은 사진 중 우리 아기 사진만 정교하게 찾아내고, 소중한 순간들을 AI가 분석하여 기록을 보조하며, 예쁘게 꾸밀 수 있는 기능을 제공합니다.

---

## 🚀 주요 기능

### 1. 스마트 갤러리 & 타임라인
*   **연도-월별 필터링**: 헤더의 연도-월을 클릭해 원하는 시점의 사진만 쏙쏙 골라볼 수 있는 인터랙티브 타임라인.
*   **최신순 자동 정렬**: 앱 실행 시 가장 최신의 추억을 먼저 노출.

### 2. 고도화된 아기 사진 판별 (AI Hybrid Filter)
*   **1단계 (온디바이스)**: Google ML Kit을 통해 얼굴 유무 및 아기 관련 라벨 1차 선별.
*   **2단계 (서버 정밀 인식)**: Python AI 서버(DeepFace)와 연동하여 등록된 '우리 아기'와 얼굴 지문(Embedding) 비교 분석.
*   **백그라운드 자동화**: WorkManager를 통해 앱 종료 후에도 분석 작업을 지속하며, 진행 상황을 상단 알림(10개 단위 최적화)으로 제공.

### 3. AI 기반 스마트 기록 보조 (Smart Journaling)
*   **감정 분석**: 아기의 표정을 분석하여 기분(행복, 평온, 졸림 등)을 자동으로 감지.
*   **AI 캡션 제안**: 분석된 감정에 맞춰 감성적인 기록 초안 자동 생성 및 원클릭 입력 지원.

### 4. 클라우드 기반 데이터 관리 (Supabase)
*   **영구적 데이터 보존**: 모든 분석 이력이 Supabase PostgreSQL DB에 실시간 저장되어 서버 재시작 시에도 유지.
*   **사용자 격리 시스템**: 사용자별 고유 ID(UUID)를 기반으로 아기 기준 사진과 분석 이력을 분리하여 저장(Supabase Storage).

### 5. 성능 및 리소스 최적화
*   **이미지 리사이징**: 서버 전송 전 기기 내에서 이미지를 최적화(최대 1024px)하여 전송 속도 90% 향상 및 데이터 사용량 절감.
*   **지능형 상태 동기화**: 'Sticky State' 로직을 통해 백그라운드 전환 시에도 분석 상태가 완벽하게 유지.

---

## 🏗 프로젝트 진화 과정 (Technical History)

### **Phase 1: 기반 구축 (On-device & Basic UI)**
*   Jetpack Compose 기반의 현대적인 안드로이드 UI 개발.
*   ML Kit을 활용한 온디바이스 얼굴 및 라벨 인식 구현.
*   Room DB를 통한 갤러리/앨범 메타데이터 로컬 저장.

### **Phase 2: AI 서버 연동 (FastAPI & DeepFace)**
*   Python FastAPI 기반 정밀 분석 서버 구축.
*   DeepFace(Facenet512)를 이용한 정밀 인물 검증 시스템 도입.
*   웹 기반 AI 모니터링 대시보드 개발.

### **Phase 3: 안정성 및 UX 고도화 (WorkManager & Refactoring)**
*   WorkManager 기반의 백그라운드 분석 엔진 전환 및 알림 기능 구현.
*   'Sticky State' 로직 도입으로 포그라운드/백그라운드 전환 시 UI 싱크 문제 해결.
*   서버 코드 리팩토링 (Main, Dashboard, State 분리) 및 Fail-safe 백엔드 시스템 구축.

### **Phase 4: 클라우드 확장 및 다중 사용자 (Supabase)**
*   Supabase PostgreSQL 연동으로 분석 이력 영구 저장.
*   사용자별 고유 ID(UUID) 헤더 기반 인증 및 데이터 격리 시스템 구축.
*   Supabase Storage 도입으로 사용자별 프로필 사진 클라우드 동기화.

### **Phase 5: 성능 최적화 및 안정화 (Current)**
*   **On-device Pre-processing**: 업로드 전 이미지 리사이징(1024px) 도입으로 네트워크 부하 최소화.
*   **배치 모드 강화**: 서버 대시보드의 실시간 상태 플리커링(Flickering) 현상 제거.
*   **알림 최적화**: 시스템 자원 절약을 위한 알림 갱신 빈도 조절(10개 단위).

---

## 🖥 AI 분석 서버 사용 가이드 (Python)

### 1. 서버 환경 구축
```bash
# 1. 서버 폴더 이동
cd server

# 2. 필수 라이브러리 설치
pip3 install -r requirements.txt

# 3. 환경 변수 설정 (.env 파일 생성)
SUPABASE_URL=your_project_url
SUPABASE_KEY=your_anon_key

# 4. 서버 실행
python3 app/main.py
```
*   **모니터링**: 브라우저에서 `http://localhost:8000`에 접속하여 실시간 분석 상태를 확인할 수 있습니다.

---

## 🛠 기술 스택

### Android App
*   **Language**: Kotlin (2.0.21)
*   **UI**: Jetpack Compose (Material 3)
*   **Async**: Coroutines & WorkManager
*   **Local DB**: Room (v4 Destructive Migration)
*   **Network**: Retrofit2 & OkHttp (Header 기반 식별)

### AI Server
*   **Framework**: Python 3.10+, FastAPI
*   **Database**: Supabase (PostgreSQL)
*   **Storage**: Supabase Storage (`profiles` bucket)
*   **AI Engine**: DeepFace (Facenet512, SSD, OpenCV)

---
*본 프로젝트는 소중한 아이의 성장을 더 쉽고 똑똑하게 기록하기 위해 개발되었습니다.*
