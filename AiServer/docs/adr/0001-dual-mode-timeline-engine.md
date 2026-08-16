# 1. Dual-Mode AI 맞춤형 타임라인 생성 및 동적 근무 시간표 설계

Date: 2026-08-16
Status: Accepted

## Context

교대근무(3교대) 간호사는 16가지의 불규칙한 근무 전환(예: `DAY_TO_NIGHT`, `EVENING_TO_DAY`)을 경험하며, 병원/병동마다 출퇴근 시간 기준(예: DAY 06:30~14:30 vs 07:00~15:00)이 다릅니다.
또한, "당일 실시간 일정"은 현재의 피로도, 위험도, 남은 가용 시간을 반영해야 하지만, "미래 날짜 일정"은 실시간 생체 데이터를 알 수 없으므로 표준 근무 전환 규칙에 기반해야 합니다.

## Decision

1. **단일 엔드포인트(`POST /api/timeline/generate`) 내 이원화 모드(Dual-Mode) 자동 분기**
   - 요청 DTO에 `analysisResult`(통합 분석 결과)가 포함되어 있으면 `TODAY` 모드로 동작하여 실시간 피로/가용시간을 적극 반영한 압축 및 맞춤 수면 배치를 수행합니다.
   - `analysisResult`가 없으면 `FUTURE` 모드로 동작하여 16가지 근무 전환 기본 룰셋 기반의 표준 권장 루틴을 생성합니다.

2. **병원별 교대 시간대(`ShiftTimes`)의 동적 주입 및 Fallback**
   - 요청 DTO에 `shiftTimes`(`dayTime`, `eveningTime`, `nightTime`)를 전달받아 프롬프트의 기준 근무 시간을 동적으로 앵커링합니다.
   - 생략 시 표준 3교대 시간(07-15 / 15-23 / 23-07)으로 자동 fallback 처리합니다.

3. **프론트엔드 UI 스키마와의 1:1 직렬화**
   - 상단 메인/서브 타이틀(`pageTitle`, `pageSubtitle`), 우측 추천 리스트(`recommendations`), 시간대별 타임라인(`timelineItems` - `time`, `category`, `highlight`)을 LLM이 직접 구조화된 JSON으로 생성합니다.

## Consequences

- **Pros**:
  - 백엔드와 프론트엔드 간의 불필요한 DTO 변환 레이어를 최소화하고 단일 책임 원칙을 유지합니다.
  - 병원별 특수 시간표 및 당일/미래 일정 조회를 단 하나의 API로 유연하게 처리할 수 있습니다.
- **Cons**:
  - LLM 프롬프트가 이원화되어 유지보수 대상 프롬프트 템플릿(`timeline_today.st`, `timeline_future.st`)이 2개로 관리됩니다.
