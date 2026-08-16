# 02 — 당일 실시간 모드 (TODAY Mode) 통합 분석 지표 연동 맞춤 타임라인 생성 파이프라인

**What to build:** 요청에 `analysisResult`(위험도 `NORMAL`/`CAUTION`/`DANGER`, 회복상태, 피로도, 가용시간, 연속근무)가 포함된 경우, 현재의 생체/피로 데이터를 적극 반영하여 수면·식사·휴식 일정을 동적으로 재배치하고 개인화된 맞춤 코멘트를 생성하는 기능을 완성합니다.

**Blocked by:** 01 — 미래 예정 모드 (FUTURE Mode) 표준 타임라인 생성 엔드투엔드 파이프라인

**Status:** resolved

- [x] 통합 분석 결과 DTO(AnalysisResultDto: riskLevel, recoveryStatus, fatigueLevel, availableHours, consecutiveDays) 연동
- [x] 당일 실시간 맞춤 프롬프트 템플릿(`timeline_today.st`) 작성
- [x] TimelineService에서 `analysisResult` 존재 여부에 따른 TODAY / FUTURE 모드 자동 분기 로직 구현
- [x] 당일 실시간 지표(가용시간, 피로도, 회복상태 등)가 반영된 타임라인 생성 단위/통합 테스트 작성 및 통과
