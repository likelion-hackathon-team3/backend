# 01 — 미래 예정 모드 (FUTURE Mode) 표준 타임라인 생성 엔드투엔드 파이프라인

**What to build:** `targetDate`, `currentShift`, `nextShift`, `transitionType`을 입력받아 16가지 표준 근무 전환 규칙에 기반한 권장 루틴 타임라인과 AI 조언을 생성하는 기본 API(`POST /api/timeline/generate`)를 완성합니다.

**Blocked by:** None — can start immediately

**Status:** resolved

- [x] 타임라인 생성 요청/응답 DTO 정의 (Request: targetDate, currentShift, nextShift, transitionType, Optional analysisResult / Response: targetDate, mode, aiSummary, timelineBlocks)
- [x] 표준 활동 유형(ActivityType: SLEEP, NAP, MEAL, WORK, REST, EXERCISE, FREE) 및 DTO 스키마 구현
- [x] 16가지 표준 근무 전환 규칙이 포함된 미래 모드 프롬프트 템플릿(`timeline_future.st`) 작성
- [x] TimelineService 및 Spring AI 연동 파이프라인 구현
- [x] TimelineController `POST /api/timeline/generate` 엔드포인트 구현
- [x] Controller 및 Service에 대한 단위/통합 테스트(TDD) 작성 및 통과
