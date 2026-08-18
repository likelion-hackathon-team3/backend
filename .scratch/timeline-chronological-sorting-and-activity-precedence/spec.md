# Spec: 타임라인 시간순 자동 정렬 및 활동 선후관계/무중복 안전망 강화 (Timeline Chronological Sorting & Activity Precedence Safety)

Status: ready-for-agent

## Problem Statement

실제 AI 타임라인 생성 테스트 결과, 대부분의 시나리오에서 고품질의 일정이 생성되었으나 특정 시나리오(예: OFF → NIGHT)에서 다음과 같은 문제점이 확인되었습니다.

1. **타임라인 배열 내 시간 역전(Unsorted Array)**:
   - LLM이 항목들을 출력하는 도중 특정 활동(예: 21:00 저녁 식사)을 뒤늦게 생성하여, 20:30 쪽잠 → 22:00 기상 → 22:30 출근 준비 뒤에 21:00 일정이 위치하는 시간 역전 현상이 발생함.
2. **수면/쪽잠 시간 중간에 다른 활동(식사 등) 중복 배치(Activity Collision)**:
   - 20:30~22:00 쪽잠 시간대 중간에 21:00 저녁 식사가 배치되어, 잠을 자는 도중에 식사를 해야 하는 비현실적인 스케줄이 구성됨.

## Solution

1. **Java 서비스 계층의 시간순 자동 정렬(Chronological Auto-Sorting) 안전망 구축**:
   - AI 모델이 반환한 `timelineItems` 리스트를 클라이언트에 전달하기 직전, Java 서비스 계층에서 시각("HH:mm")을 파싱하여 시간 흐름(자정 넘김을 고려한 오름차순)에 맞게 100% 보장 정렬합니다.
2. **프롬프트 내 활동 선후 관계(Precedence) 및 수면 중 활동 금지 불변식 강화**:
   - 식사(MEAL)는 수면/낮잠 시작 최소 1시간 전에 완료되어야 함을 명시.
   - 수면(SLEEP) 및 쪽잠(NAP)이 진행되는 시간 구간에는 어떠한 다른 활동(식사, 휴식, 이동 등)도 중복 배치할 수 없는 단일 독점(Single Exclusive Interval) 규칙을 강제.

## User Stories

1. As a 3교대 간호사, I want 타임라인의 모든 일정이 시간순(오름차순)으로 완벽히 정렬되어 표시되어서, so that 하루 일과를 시간의 흐름에 따라 헷갈림 없이 직관적으로 확인할 수 있다.
2. As a 3교대 간호사, I want 쪽잠이나 수면을 취하는 시간 도중에 식사나 다른 일정이 끼어들지 않아서, so that 방해받지 않고 온전하게 수면을 취할 수 있다.
3. As a 3교대 간호사, I want 식사(MEAL)가 수면 시작 전에 적절한 소화 시간을 두고 배치되어서, so that 속이 더부룩하지 않은 상태로 편안하게 잠들 수 있다.
4. As a 모바일 앱 프론트엔드, I want AiServer가 항상 시간순으로 완전히 정렬된 리스트를 반환하여, so that 프론트엔드에서 별도의 불안정한 재정렬 로직을 작성할 필요 없이 그대로 렌더링할 수 있다.
5. As a 3교대 간호사, I want 야간(NIGHT) 출근 전 저녁 식사 후 충분한 쪽잠을 자고 일어나 바로 출근 준비로 이어지는 매끄러운 루틴을 추천받아서, so that 출근 전 컨디션을 최상으로 끌어올릴 수 있다.
6. As a 백엔드 시스템, I want AI의 비결정적(Non-deterministic) 출력 순서 글리치에도 불구하고 서비스 계층 안전망을 통해 항상 일관된 시간 정합성을 보장받아서, so that 프로덕션 환경에서 오류 없는 데이터를 제공할 수 있다.

## Implementation Decisions

1. **서비스 계층 정렬 안전망 (`TimelineServiceImpl`)**:
   - `RawTimelineAiResponse`에서 추출한 `timelineItems`가 null이 아니거나 비어있지 않은 경우, 리스트를 시간순으로 정렬하는 헬퍼 적용.
   - 자정(00:00)을 넘어가는 24시간 구간(예: 18:00 시작 ~ 익일 07:00 근무)에서도 첫 번째 시작 시각을 기준으로 한 회전(Offset/Cyclic) 시간 순서를 반영하여 시간 왜곡 없이 정렬.
2. **프롬프트 불변 규칙 고도화 (`timeline_today.st`, `timeline_future.st`)**:
   - **수면 독점 구간(Exclusive Sleep Interval)**: 수면(SLEEP, NAP) 시작 시각부터 종료 시각 사이에는 다른 일정을 절대 배치 금지.
   - **식사 선행 원칙(Meal Precedence)**: 식사(MEAL)는 수면/낮잠 시작 전(최소 1시간 전)에 완료하거나, 기상(WAKE_UP) 후 최소 30분 뒤에 배치.

## Testing Decisions

1. **외부 동작 중심 테스트 (Service & API Seams)**:
   - `TimelineServiceImplTest`: 뒤섞인 시간(20:30, 22:00, 22:30, 21:00)을 가진 Mock 응답이 주어졌을 때, 최종 응답의 `timelineItems`가 시간순(20:30, 21:00, 22:00, 22:30 또는 18:00 기준 시간 흐름)으로 정확히 정렬되어 반환되는지 단위 테스트 검증.
   - `TimelineAiGeneratorTest`: 프롬프트 템플릿에 수면 독점 및 식사 선행 규칙이 정확히 반영되었는지 검증.
2. **Prior Art**:
   - 기존 `TimelineServiceImplTest`의 검증 로직에 시간 오름차순 정합성 assertion 추가.

## Out of Scope

- 프론트엔드 UI 컴포넌트 내부의 클라이언트 사이드 재정렬 로직 수정 (백엔드/AiServer 레벨에서 원천 해결).
- 새로운 DTO 필드 추가 (기존 `TimelineItemDto` 구조 100% 유지).

## Further Notes

- 시간 정렬 알고리즘은 호출 시점(`currentTime`) 또는 첫 번째 아이템의 시각을 기준 앵커로 삼아 24시간 자정 넘김을 안전하게 처리합니다.
