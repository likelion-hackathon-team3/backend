# 🏥 AiServer 시스템 아키텍처 및 개발 가이드

이 문서는 3교대 근무 간호사를 위한 **근무표 OCR 이미지 인식** 및 **개인 맞춤형 웰니스 타임라인 생성 서버(`AiServer`)**의 구조와 핵심 기능을 설명합니다.

---

## 1. 프로젝트 개요 (Overview)

- **역할**: 
  1. 간호사 근무표 사진/이미지를 업로드받아 OpenCV 전처리 및 Vision LLM으로 교대 근무표를 정밀 추출
  2. 추출된 근무표 및 개인 피로도/선호도를 바탕으로 시간 충돌이 없는 24시간 맞춤형 웰니스 타임라인 생성
- **기술 스택**: Java 17, Spring Boot 4.1.0, Spring AI 2.0.0, OpenAI GPT-4o, OpenCV 4.9.0, Gradle
- **핵심 설계 철학**: 
  - **"시간표 뼈대는 백엔드 수학 엔진이 100% 확정하고, 감성과 콘텐츠는 AI가 채운다" (하이브리드 슬롯 필러 아키텍처)**

---

## 2. 전체 시스템 파이프라인

AiServer는 크게 **2개의 독립적인 핵심 파이프라인**으로 구성되어 있습니다.

```text
[클라이언트 요청]
   │
   ├── 📷 1. 근무표 이미지 분석 (Vision OCR Pipeline)
   │      └─ OpenCvPreProcessingLayer (기울기 보정/이진화)
   │      └─ SpringAiVisionLayer (GPT-4o Dual-Image Vision)
   │      └─ RuleBasedNormalizingLayer (DAY, EVENING, NIGHT 정규화)
   │
   └── 🕒 2. 맞춤 타임라인 생성 (Hybrid Slot Filler Timeline Pipeline)
          └─ TimelineSlotCalculator (수학적 역산 및 수면/식사 슬롯 확정)
          └─ TimelineAiGenerator (Spring AI + GPT-4o 감성 콘텐츠 채우기)
          └─ TimelineServiceImpl (ISO-8601 자연 정렬 및 오케스트레이션)
```

---

## 3. 핵심 모듈별 기능 설명

### 🌟 파이프라인 A: 근무표 OCR 이미지 분석 (`com.likeLion.backend.aiserver.service.layer`)

1. **OpenCvPreProcessingLayer (이미지 전처리)**:
   - 카메라로 비스듬히 촬영된 근무표의 **기울기를 보정(Deskew)**합니다.
   - 형광펜/노이즈를 마스킹하여 제거하고, **고대비 흑백(Binarization) 이미지**를 생성합니다.
2. **SpringAiVisionLayer (Vision LLM)**:
   - **원본 이미지 + 흑백 처리 이미지**를 듀얼 이미지(Dual-Image)로 Vision LLM(GPT-4o)에 전달하여 인식 정확도를 극대화합니다.
3. **RuleBasedNormalizingLayer (정규화 계층)**:
   - LLM이 추출한 다양한 형태의 근무명(D, Day, 데이, N, 나이트, E, 이브닝 등)을 시스템 표준 Enum인 `ShiftType` (`DAY`, `EVENING`, `NIGHT`, `OFF`)으로 안전하게 맵핑합니다.

---

### 🌟 파이프라인 B: 하이브리드 슬롯 필러 타임라인 생성 (`service.layer` & `service`)

1. **TimelineSlotCalculator (수학적 규칙 엔진)**:
   - **퇴근/통근 시간 연동 귀가 기준선(`homeArrival`)**: 퇴근 시각 + 편도 통근시간 이후부터 첫 웰니스 활동이 시작되도록 계산합니다.
   - **출근 준비 역산**: 다음 출근 시각에서 `편도 통근 시간 + 준비 시간(20~30분)`을 정확히 역산하여 출근 준비(`PREPARATION`) 슬롯을 배치합니다.
   - **16가지 교대 패턴 표준화**:
     - `EVENING ➡️ DAY`: 8시간 초단축 턴어라운드 시 수면을 최우선 사수하고 식사/준비 시간을 지능적으로 압축.
     - `DAY ➡️ NIGHT`: 야간 출근 전 필수 90분 쪽잠(`NAP`) 및 사전 각성 슬롯 배치.
     - `NIGHT ➡️ DAY`: 퇴근 직후 1차 주간 수면(08:30~13:30) + 익일 출근 대비 2차 야간 조기 수면(22:30~05:30) 분할 수면 지원.
   - **적응형 수면 및 소요 시간 상수 (Duration Bounds)**:
     - 수면 상한(Cap): 최대 8.5시간(기본 7.5시간)으로 제한하여 낮 시간대 14시간 과다 수면 할루시네이션 원천 차단.
     - 0시간 최소 수면: 2~3시간만 쉬고 나가는 극단적 초단축 상황 시 긴 수면 대신 30~90분 파워 쪽잠(`NAP`) 또는 휴식(`REST`)으로 적응 분기.
   - **Flex Intervals(여유 구간) 추출**: 필수 일정 사이에 3시간 이상 비어있는 시간대를 AI 자율 활동 구간으로 추출.

2. **TimelineAiGenerator (AI 생성 레이어)**:
   - 계산된 고정 슬롯 뼈대(`skeletonJson`)와 여유 구간(`flexIntervals`), 총 가용 자유 시간(`totalFreeHours`)을 프롬프트 변수로 주입합니다.
   - AI는 고정 슬롯의 시간(`time`)과 카테고리(`category`)를 100% 유지하며, 친근하고 따뜻한 `title`, `description`, `highlight`, `recommendations`를 채웁니다.
   - 여유 구간에 대해서는 사용자의 개인 메모(`userNotes`) 및 선호도(필라테스, 산책 등)를 반영한 맞춤형 활동을 추가합니다.

3. **TimelineServiceImpl (오케스트레이션)**:
   - 요청 모드 판별: `analysisResult` 또는 `currentTime`이 있으면 **TODAY 모드**, 없으면 **FUTURE 모드**로 분기합니다.
   - 모든 타임라인 아이템을 `LocalDateTime` (`YYYY-MM-DDTHH:mm`) 기준으로 오름차순 자연 정렬하고, 카테고리 null 누락 시 `REST`로 안전하게 폴백합니다.

---

## 4. API 엔드포인트 명세

### 1. 타임라인 생성 API (`POST /api/timeline/generate`)

- **요청 Body 예시**:
```json
{
  "targetDate": "2026-08-20",
  "currentShift": "DAY",
  "nextShift": "EVENING",
  "transitionType": "DAY_TO_EVENING",
  "currentTime": "15:00",
  "currentWorkEnd": "2026-08-20T15:00",
  "nextWorkStart": "2026-08-21T15:00",
  "commuteMinutes": 40,
  "userNotes": "카페인에 민감함, 친구와 점심 약속 있음",
  "personalization": {
    "sleepBuffer": 30,
    "caffeineCutoff": "15:30"
  },
  "analysisResult": {
    "riskLevel": "CAUTION",
    "recoveryStatus": "RECOVERY_NEEDED",
    "fatigueLevel": "HIGH",
    "availableHours": 24.0,
    "consecutiveDays": 3
  }
}
```

- **응답 Body 예시**:
```json
{
  "targetDate": "2026-08-20",
  "mode": "TODAY",
  "pageTitle": "오늘부터 내일 EVENING 근무 전까지의 맞춤 계획이에요",
  "pageSubtitle": "피로도가 높고 회복이 필요한 상태입니다. 충분한 휴식과 수면으로 에너지를 회복하세요.",
  "timelineItems": [
    {
      "time": "2026-08-20T15:40",
      "title": "귀가 후 가벼운 식사 및 휴식",
      "description": "퇴근 후 40분 이동 후 편안하게 휴식을 취하세요.",
      "category": "MEAL",
      "highlight": null
    },
    {
      "time": "2026-08-20T23:30",
      "title": "취침",
      "description": "편안한 수면 환경을 조성하고 충분한 휴식을 취하세요.",
      "category": "SLEEP",
      "highlight": "권장 수면: 7시간 30분"
    },
    {
      "time": "2026-08-21T07:00",
      "title": "기상",
      "description": "상쾌한 아침을 맞이하며 하루를 시작하세요.",
      "category": "WAKE_UP",
      "highlight": null
    },
    {
      "time": "2026-08-21T12:20",
      "title": "점심 식사",
      "description": "친구와의 약속을 즐기며 든든한 점심을 드세요.",
      "category": "MEAL",
      "highlight": "출근 전 든든한 식사"
    },
    {
      "time": "2026-08-21T13:50",
      "title": "출근 준비",
      "description": "출근 전 필요한 준비를 마치고 여유롭게 출발하세요.",
      "category": "PREPARATION",
      "highlight": "통근 및 근무 준비"
    },
    {
      "time": "2026-08-21T15:00",
      "title": "EVENING 근무 시작",
      "description": "새로운 근무를 시작하며 최선을 다하세요.",
      "category": "WORK",
      "highlight": null
    }
  ],
  "recommendations": [
    "15:30 이후 카페인 섭취를 피하고 수분을 충분히 섭취하세요.",
    "취침 전에는 따뜻한 샤워나 명상으로 몸과 마음을 릴랙스하세요.",
    "내일 출근 전에는 든든한 점심을 챙겨 드세요."
  ]
}
```

---

## 5. 프로젝트 디렉토리 구조

```text
AiServer/
├── src/main/java/com/likeLion/backend/aiserver/
│   ├── AiServerApplication.java           ← 메인 엔트리포인트 (Dotenv 로더)
│   ├── controller/
│   │   ├── TimelineController.java        ← 타임라인 생성 REST 엔드포인트
│   │   └── ScheduleAnalyzeController.java ← 근무표 이미지 분석 REST 엔드포인트
│   ├── dto/
│   │   ├── ShiftType.java                 ← DAY, EVENING, NIGHT, OFF 표준 Enum
│   │   └── timeline/                      ← 요청/응답 DTO 및 슬롯 모델
│   │       ├── BaseSlotDto.java           ← 수학적 고정 슬롯 DTO
│   │       ├── TimelineSkeletonDto.java   ← 뼈대 및 여유 구간 DTO
│   │       ├── TimelineGenerateRequest.java
│   │       └── TimelineGenerateResponse.java
│   ├── service/
│   │   ├── TimelineService.java
│   │   ├── TimelineServiceImpl.java       ← 타임라인 오케스트레이션 서비스
│   │   └── layer/
│   │       ├── TimelineSlotCalculator.java← 규칙 기반 결정론적 슬롯 계산 엔진
│   │       ├── TimelineAiGenerator.java   ← Spring AI + GPT-4o 연동 엔진
│   │       ├── OpenCvImagePreprocessor.java
│   │       └── SpringAiVisionLayer.java
│   └── exception/
│       └── GlobalExceptionHandler.java    ← 전역 에러 핸들러
└── src/main/resources/
    ├── application.properties             ← 포트, 모델(gpt-4o), API 키 설정
    └── prompts/
        ├── timeline_future.st             ← FUTURE 모드 프롬프트 템플릿
        └── timeline_today.st              ← TODAY 모드 프롬프트 템플릿
```

---

## 6. 핵심 규칙 및 제약사항 요약

| 항목 | 표준 규칙 및 상수 |
| :--- | :--- |
| **시간 포맷** | `YYYY-MM-DDTHH:mm` (ISO-8601) 필수 |
| **수면(SLEEP) 시간** | 최소 `0시간`(초단축 시) ~ 기본 `7.5시간`(450분) ~ 최대 `8.5시간`(510분) |
| **쪽잠(NAP) 시간** | 최소 `30분` ~ 기본 `90분` |
| **식사(MEAL) 시간** | 최소 `20분`(야식) ~ 기본 `40분` ~ 최대 `45분` |
| **출근 준비(PREPARATION)** | 최소 `20분` ~ 기본 `30분` (통근 시간과 함께 역산) |
| **허용 카테고리 (8종)** | `SLEEP`, `NAP`, `PREPARATION`, `WAKE_UP`, `MEAL`, `WORK`, `REST`, `EXERCISE` |
