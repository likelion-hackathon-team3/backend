# Spec: 사용자/병원별 맞춤 근무 시간대(Shift Times) 동적 지원

Status: resolved

## Problem Statement

병원 및 병동마다 간호사의 출퇴근 시간 기준(예: DAY 06:30~14:30 vs 07:00~15:00 vs 08:00~16:00)이 상이합니다. 현재 AI 타임라인 엔진이 표준 3교대 시간(DAY 07:00~15:00, EVENING 15:00~23:00, NIGHT 23:00~익일 07:00)으로 고정되어 있으면, 다른 근무 시간표를 가진 병원의 간호사에게 실제 출퇴근 시간과 맞지 않는 잘못된 수면 및 루틴 시간표가 추천되는 문제가 발생합니다.

## Solution

AiServer의 타임라인 생성 요청 DTO에 선택적(Optional) 사용자 근무 시간대 설정(`shiftTimes`)을 추가하고, 프롬프트 템플릿에 해당 시간대를 동적으로 주입합니다.
- `shiftTimes`가 주입된 경우: 사용자의 실제 병원 출퇴근 시간(예: DAY 06:30~14:30)을 앵커로 삼아 정확한 수면·식사·출근 준비 타임라인을 생성합니다.
- `shiftTimes`가 생략된 경우: 표준 기본값(07:00~15:00 / 15:00~23:00 / 23:00~07:00)으로 자동 fallback 처리하여 기존 API 클라이언트와의 하위 호환성을 유지합니다.

## User Stories

1. As a 간호사 whose hospital operates DAY shifts from 06:30 to 14:30, I want the AI timeline to anchor around 06:30, so that my wake-up and preparation times match my real shift.
2. As a 간호사 whose hospital operates EVENING shifts from 14:30 to 22:30, I want my evening routine and sleep schedule to reflect the 22:30 end time, so that I can go to sleep at a realistic time.
3. As a backend developer, I want to optionally pass the user's custom shift time configuration in `TimelineGenerateRequest`, so that the AI server dynamically adapts without requiring hardcoded hospital-specific branches.
4. As a backend developer, I want the API to gracefully fall back to standard hospital shift hours when `shiftTimes` is omitted, so that basic requests still work without failure.
5. As a 간호사 with 40-minute commute time, I want my preparation and travel slots to accurately reflect my custom shift start times, so that I am never late for work.

## Implementation Decisions

1. **DTO Schema Expansion**
   - `ShiftTimesDto` 레코드 신설: `dayTime` (기본값 "07:00 ~ 15:00"), `eveningTime` (기본값 "15:00 ~ 23:00"), `nightTime` (기본값 "23:00 ~ 익일 07:00").
   - `TimelineGenerateRequest`에 `ShiftTimesDto shiftTimes` (선택 필드) 추가.

2. **Prompt Template Dynamic Injection**
   - `timeline_future.st` 및 `timeline_today.st`의 `## 2. 표준 근무 시간표` 섹션을 동적 템플릿 변수(`{dayTime}`, `{eveningTime}`, `{nightTime}`)로 치환.
   - `TimelineAiGenerator`에서 `shiftTimes`가 null이면 표준 기본 시간 문자열을 모델 맵에 바인딩.

3. **Backward Compatibility Guarantee**
   - `shiftTimes`가 null이거나 빈 값이더라도 기존 동작과 동일하게 07-15 / 15-23 / 23-07 기준으로 무결하게 작동하도록 기본값 보장.

## Testing Decisions

1. **High-Level Seam Testing**
   - Controller 통합 테스트 (`TimelineControllerTest`): `shiftTimes`가 포함된 요청과 생략된 요청 모두 200 OK와 올바른 타임라인 데이터를 반환하는지 검증.
   - Service 단위 테스트 (`TimelineServiceImplTest`): `shiftTimes` 제공 시 올바른 시간대 문자열이 프롬프트 렌더링 파라미터로 전달되는지 검증.

2. **Boundary Testing**
   - 비표준 시간대(예: DAY 06:00~14:00, EVENING 14:00~22:00, NIGHT 22:00~06:00) 주입 시 타임라인 항목의 출근 및 취침 시각이 비표준 시간대를 정확히 반영하는지 검증.

## Out of Scope

- 2교대(12시간 교대) 또는 전담 야간 근무 등 비 3교대 특수 근무 형태 지원.
- 병원별 지오펜싱/GPS 기반 출퇴근 시간 자동 감지.

## Further Notes

- 백엔드 서버의 `Environment` 엔티티 필드(`dayStartTime`, `dayEndTime`, `eveningStartTime` 등)와 자연스럽게 1:1 매핑이 가능합니다.
