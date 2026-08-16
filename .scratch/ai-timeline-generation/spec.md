# Spec: AI 맞춤형 추천 타임라인 생성 엔진 (AI Timeline Generation Engine)

Status: resolved

## Problem Statement

교대근무(3교대) 간호사는 16가지에 이르는 불규칙한 근무 전환(예: DAY→NIGHT, EVENING→DAY 등)과 연속 근무로 인해 수면 패턴과 생체 리듬이 무너지기 쉽습니다. 특히 짧은 휴식 시간이나 위험한 근무 전환 상황에서 언제 수면을 취하고, 낮잠이나 식사, 휴식을 어떻게 배치해야 피로를 최소화하고 안전하게 다음 근무를 준비할 수 있는지 개인 맞춤형 일정 가이드를 스스로 계획하기 어렵습니다. 또한 당일의 피로도/위험도와 미래 날짜의 표준 근무 일정이 달라 각각에 적합한 일정 추천이 필요합니다.

## Solution

AiServer에 백엔드의 통합 분석 데이터(16가지 근무 전환 유형, 위험도, 회복 상태, 가용 시간, 피로도 등)와 병원별 출퇴근 시간(`shiftTimes`)을 주입받아 개인화된 수면·휴식·활동 추천 타임라인을 생성하는 LLM 기반 타임라인 엔진을 구축합니다.
- **당일 실시간 모드 (TODAY)**: 현재의 생체/피로 상태, 위험 등급, 남은 가용 시간을 고려하여 최적의 수면 및 회복 일정을 실시간으로 추천하고 상단 타이틀, 서브타이틀, 3대 추천 포인트를 제공합니다.
- **미래 예정 모드 (FUTURE)**: 미래 날짜의 근무 전환 규칙에 기반하여 표준 권장 생활 루틴 타임라인을 생성합니다.
- 구조화된 표준 카테고리(SLEEP, NAP, PREPARATION, WAKE_UP, MEAL, WORK, REST, EXERCISE, FREE)와 특정 시각(time) 중심 아이템으로 일정을 일관성 있게 반환합니다.

## User Stories

1. As a 3교대 간호사, I want to receive an AI-recommended daily timeline based on my current fatigue and risk level, so that I can efficiently manage my rest and sleep before the next shift.
2. As a 3교대 간호사 transitioning from DAY to NIGHT, I want specific nap and sleep schedule recommendations, so that I do not feel drowsy during my night shift.
3. As a 3교대 간호사 with high fatigue and DANGER risk level, I want recovery-prioritized timeline recommendations, so that I can focus on rest and prevent burnout.
4. As a 3교대 간호사 planning upcoming shifts, I want to see standard recommended routines for future dates, so that I can prepare in advance for upcoming shift transitions.
5. As a backend server developer, I want to call a unified timeline generation API with optional real-time analysis metrics and shift times, so that the AI server automatically switches between real-time tailored mode and future scheduled mode.
6. As a frontend client, I want timeline activities categorized into standardized types (SLEEP, NAP, PREPARATION, WAKE_UP, MEAL, WORK, REST, EXERCISE, FREE), so that I can reliably render intuitive icons and UI components.
7. As a 3교대 간호사, I want a warm, empathetic main title, subtitle, and actionable 3-point recommendations along with my timeline, so that I feel supported and understand the rationale behind the schedule.
8. As a 3교대 간호사 with limited available hours (< 6 hours), I want essential sleep-focused compressed scheduling, so that I maximize sleep without missing my shift.

## Implementation Decisions

1. **API Endpoint & Contract**
   - 단일 엔드포인트 `POST /api/timeline/generate` 운영.
   - 요청 DTO (`TimelineGenerateRequest`):
     - `targetDate` (LocalDate): 대상 날짜 (생략 시 오늘)
     - `currentShift` (ShiftType): 현재/기준일 근무
     - `nextShift` (ShiftType): 다음 근무
     - `transitionType` (String): 전환 유형 (생략 시 자동 조합)
     - `shiftTimes` (ShiftTimesDto, Optional): 병원별 실제 근무 시간대 (`dayTime`, `eveningTime`, `nightTime`, 생략 시 표준시간)
     - `analysisResult` (AnalysisResultDto, Optional): 위험도, 회복상태, 피로도, 가용시간, 연속근무일수.
   - 응답 DTO (`TimelineGenerateResponse`):
     - `targetDate` (LocalDate): 대상 날짜
     - `mode` (TimelineMode): `TODAY` | `FUTURE`
     - `pageTitle` (String): 메인 헤드라인 (상단)
     - `pageSubtitle` (String): 서브 헤드라인 (상단)
     - `timelineItems` (List<TimelineItemDto>): 시간순 정렬 일정 (`time`, `title`, `description`, `category`, `highlight`)
     - `recommendations` (List<String>): 우측 핵심 추천 포인트 3선

2. **Dual-Mode Prompting Architecture**
   - `analysisResult`가 존재하는 경우: 당일 맞춤 프롬프트 템플릿(`timeline_today.st` - 실시간 위험도/회복도/가용시간 반영) 적용.
   - `analysisResult`가 없는 경우: 미래 권장 루틴 프롬프트 템플릿(`timeline_future.st` - 16가지 근무 전환 기본 패턴 중심) 적용.

3. **16가지 근무 전환 및 병원별 시간표 앵커링**
   - 백엔드에서 정립된 16가지 전환 유형 및 주입된 병원별 출퇴근 시간표(`{dayTime}`, `{eveningTime}`, `{nightTime}`)를 프롬프트에 동적 바인딩하여 오차 없는 일정 생성 보장.

4. **Structured JSON Output Parsing**
   - Spring AI의 `BeanOutputConverter<RawTimelineAiResponse>`를 사용하여 LLM 출력을 규격화된 JSON/DTO로 안전하게 역직렬화 및 검증.

## Testing Decisions

1. **High-Level Seam Testing**
   - Controller 계층 통합 테스트(`TimelineControllerTest`): 당일 모드, 미래 모드, 커스텀 `shiftTimes` 포함 요청에 대한 HTTP 200 응답 및 JSON 응답 구조 검증.
   - Service 계층 단위 테스트 (`TimelineServiceImplTest`): `TODAY`/`FUTURE` 모드 분기, DTO 정규화(transitionType 자동 생성), 커스텀 시간표 주입 검증.

2. **External Behavior Focus**
   - LLM 내부 구현이 아닌 외부 API 계약(입출력 DTO 규격, HTTP 상태 코드)에 집중하여 테스트 작성.

## Out of Scope

- 사용자의 실제 캘린더 외부 동기화(Google Calendar, Apple Calendar 등).
- 백엔드 서버의 위험도/피로도/수면시간 직접 계산 로직(backendServer가 전담).
- 근무표 이미지 OCR 분석 파이프라인 변경(기존 `/api/ocr`과 독립 유지).

## Further Notes

- 프롬프트 템플릿은 `AiServer/src/main/resources/prompts/` 하위에 관리하여 유지보수성을 높입니다.
- 표준 카테고리(`category`): `SLEEP`, `NAP`, `PREPARATION`, `WAKE_UP`, `MEAL`, `WORK`, `REST`, `EXERCISE`, `FREE`.
