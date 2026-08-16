# Spec: AI 맞춤형 추천 타임라인 생성 엔진 (AI Timeline Generation Engine)

Status: resolved

## Problem Statement

교대근무(3교대) 간호사는 16가지에 이르는 불규칙한 근무 전환(예: DAY→NIGHT, EVENING→DAY 등)과 연속 근무로 인해 수면 패턴과 생체 리듬이 무너지기 쉽습니다. 특히 짧은 휴식 시간이나 위험한 근무 전환 상황에서 언제 수면을 취하고, 낮잠이나 식사, 휴식을 어떻게 배치해야 피로를 최소화하고 안전하게 다음 근무를 준비할 수 있는지 개인 맞춤형 일정 가이드를 스스로 계획하기 어렵습니다. 또한 당일의 피로도/위험도와 미래 날짜의 표준 근무 일정이 달라 각각에 적합한 일정 추천이 필요합니다.

## Solution

AiServer에 백엔드의 통합 분석 데이터(16가지 근무 전환 유형, 위험도, 회복 상태, 가용 시간, 피로도 등)를 주입받아 개인화된 수면·휴식·활동 추천 타임라인을 생성하는 LLM 기반 타임라인 엔진을 구축합니다.
- **당일 실시간 모드 (TODAY)**: 현재의 생체/피로 상태, 위험 등급, 남은 가용 시간을 고려하여 최적의 수면 및 회복 일정을 실시간으로 추천하고 맞춤 조언을 제공합니다.
- **미래 예정 모드 (FUTURE)**: 미래 날짜의 근무 전환 규칙에 기반하여 표준 권장 생활 루틴 타임라인을 생성합니다.
- 구조화된 표준 카테고리(수면, 낮잠, 식사, 근무, 휴식, 운동, 자유시간)와 시간 블록으로 일정을 일관성 있게 반환합니다.

## User Stories

1. As a 3교대 간호사, I want to receive an AI-recommended daily timeline based on my current fatigue and risk level, so that I can efficiently manage my rest and sleep before the next shift.
2. As a 3교대 간호사 transitioning from DAY to NIGHT, I want specific nap and sleep schedule recommendations, so that I do not feel drowsy during my night shift.
3. As a 3교대 간호사 with high fatigue and DANGER risk level, I want recovery-prioritized timeline recommendations, so that I can focus on rest and prevent burnout.
4. As a 3교대 간호사 planning upcoming shifts, I want to see standard recommended routines for future dates, so that I can prepare in advance for upcoming shift transitions.
5. As a backend server developer, I want to call a unified timeline generation API with optional real-time analysis metrics, so that the AI server automatically switches between real-time tailored mode and future scheduled mode.
6. As a frontend client, I want timeline activities categorized into standardized types (SLEEP, NAP, MEAL, WORK, REST, EXERCISE, FREE), so that I can reliably render intuitive icons and UI components.
7. As a 3교대 간호사, I want a warm, empathetic AI summary and actionable advice along with my timeline, so that I feel supported and understand the rationale behind the schedule.
8. As a 3교대 간호사 with limited available hours (< 6 hours), I want essential sleep-focused compressed scheduling, so that I maximize sleep without missing my shift.

## Implementation Decisions

1. **API Endpoint & Contract**
   - 단일 엔드포인트 `POST /api/timeline/generate` 운영.
   - 요청 DTO: 대상 날짜(`targetDate`), 현재/기준 근무(`currentShift`), 다음 근무(`nextShift`), 전환 유형(`transitionType`), 선택적 통합 분석 결과(`analysisResult` - 위험도, 회복상태, 피로도, 가용시간, 연속근무일수).
   - 응답 DTO: 대상 날짜(`targetDate`), 실행 모드(`mode`: `TODAY` | `FUTURE`), AI 맞춤 총평(`aiSummary`), 타임라인 블록 리스트(`timelineBlocks` - `startTime`, `endTime`, `activityType`, `title`, `description`).

2. **Dual-Mode Prompting Architecture**
   - `analysisResult`가 존재하는 경우: 당일 맞춤 프롬프트 템플릿(실시간 위험도/회복도/가용시간 반영) 적용.
   - `analysisResult`가 없는 경우: 미래 권장 루틴 프롬프트 템플릿(16가지 근무 전환 기본 패턴 중심) 적용.

3. **16가지 근무 전환 기본 룰셋 탑재**
   - 백엔드에서 정립된 16가지 전환 유형 및 교대근무 특성(예: NIGHT 전 낮잠 확보, EVENING 후 취침 시간 등)을 프롬프트 시스템 지침으로 탑재하여 일관성 있는 일정 생성 보장.

4. **Structured JSON Output Parsing**
   - Spring AI의 `StructuredOutputConverter` 또는 BeanOutputConverter를 사용하여 LLM 출력을 규격화된 JSON/DTO로 안전하게 역직렬화 및 검증.

## Testing Decisions

1. **High-Level Seam Testing**
   - Controller 계층 통합 테스트(`MockMvc`): 다양한 요청 페이로드(당일 분석 결과 포함 vs 미포함 미래 요청)에 대한 HTTP 200 응답 및 JSON 응답 구조 검증.
   - Service 계층 단위 테스트: Mock LLM Client를 통해 요청 데이터에 따른 올바른 프롬프트 템플릿 선택 및 응답 DTO 매핑 동작 검증.
   - 16가지 근무 전환 타입별 프롬프트 주입 유효성 테스트.

2. **External Behavior Focus**
   - LLM 내부 구현이 아닌 외부 API 계약(입출력 DTO 규격, HTTP 상태 코드, 필수 필드 누락 시 유효성 검증)에 집중하여 테스트 작성.

## Out of Scope

- 사용자의 실제 캘린더 외부 동기화(Google Calendar, Apple Calendar 등).
- 백엔드 서버의 위험도/피로도/수면시간 직접 계산 로직(backendServer가 전담).
- 근무표 이미지 OCR 분석 파이프라인 변경(기존 `/api/ocr`과 독립 유지).

## Further Notes

- 프롬프트 템플릿은 `AiServer/src/main/resources/prompts/` 하위에 관리하여 유지보수성을 높입니다.
- 표준 카테고리(`activityType`): `SLEEP`, `NAP`, `MEAL`, `WORK`, `REST`, `EXERCISE`, `FREE`.
