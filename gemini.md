# Project Overview & Gemini Guide

## 1. Repository Structure & Roles
이 저장소(Lionbackend)는 멀티 모듈 형태의 백엔드 프로젝트입니다.

- **AiServer (`/AiServer`)**: **현재 사용자(User)가 전담하여 개발 및 유지보수하는 메인 레포지토리/디렉토리**입니다.
  - 역할: 간호사 근무표 이미지 OCR/Vision AI 분석 서버 (OpenCV 전처리 + Spring AI / Vision LLM)
  - 기술 스택: Java 17, Spring Boot 4.1.0, Spring AI 2.0.0, OpenCV 4.9.0, Gradle
- **backendServer (`/backendServer`)**: **협업 동료가 담당하여 개발 중인 디렉토리**입니다.
  - 사용자 명시적 요청이 없는 한 불필요한 수정이나 변경을 피합니다.
- **upload (`/upload`)**: 업로드 파일 임시/테스트 디렉토리

---

## 2. AiServer Architecture & Key Guidelines

### 주요 기능 및 파이프라인
1. **OpenCV 이미지 전처리 (`OpenCvPreProcessingLayer`)**
   - 이미지 기울기 보정 (Deskew)
   - 형광펜/노이즈 마스킹 및 제거
   - 고대비 흑백(Binarization) 이미지 생성
2. **Spring AI Vision 파이프라인 (`SpringAiVisionLayer`)**
   - 원본 이미지와 흑백 처리 이미지를 Dual-Image로 Vision LLM(GPT-4o)에 전달하여 정확도 향상
   - 프롬프트 템플릿: `src/main/resources/prompts/`
3. **규칙 기반 정규화 (`RuleBasedNormalizingLayer`, `ShiftMapper`)**
   - 추출된 원문(D, Day, 데이 등)을 표준 근무 코드(DAY, EVENING, NIGHT, OFF 등)로 맵핑

### 환경 설정
- `AiServer/.env`에 `OPENAI_API_KEY` 등 필수 환경변수 설정 필요

---

## 3. Working Principles for AI Assistant
1. **작업 우선순위**: 모든 코드 변경 및 기능 개발 요청은 기본적으로 `AiServer`를 대상으로 수행합니다.
2. **협업 격리**: `backendServer` 영역은 충돌을 방지하기 위해 사용자 지시가 있을 때만 확인/수정합니다.
3. **독립 실행성**: `AiServer` 단독으로 빌드 및 테스트(`gradlew bootRun`, `gradlew test`)가 가능하도록 의존성과 설정을 유지합니다.
