# Context: AiServer (Vision OCR & AI Timeline Engine)

## Glossary (도메인 용어 사전)

### 1. 근무 및 교대 도메인
- **근무 코드 (Shift Code)**: 간호사의 일별 근무 유형을 나타내는 표준 코드.
  - `DAY`: 주간 근무 (예: 07:00 ~ 15:00)
  - `EVENING`: 저녁/오후 근무 (예: 15:00 ~ 23:00)
  - `NIGHT`: 야간 근무 (예: 23:00 ~ 익일 07:00)
  - `OFF`: 휴무/비번
- **근무 전환 (Shift Transition)**: 현재(이전) 근무에서 다음 근무로의 전환 패턴 (총 16가지 경우의 수: DAY_TO_DAY, DAY_TO_EVENING, DAY_TO_NIGHT, DAY_TO_OFF, EVENING_TO_DAY 등).

### 2. 통합 분석 지표 (Integrated Analysis Metrics)
- **위험도 (Risk Level)**: 근무 전환 패턴, 연속 근무 일수, 다음 근무까지의 휴식 시간 등을 종합한 위험 등급 (`NORMAL`, `CAUTION`, `DANGER`).
- **피로도 (Fatigue Level)**: 사용자의 주관적/측정된 현재 피로 상태 (`LOW`, `MEDIUM`, `HIGH`).
- **회복 상태 (Recovery State)**: 피로도, 수면 시간, 활동량, 심박수를 종합한 생체 회복 등급 (`GOOD` / 양호, `NEEDS_RECOVERY` / 회복 필요, `PRIORITY_RECOVERY` / 회복 우선 필요).
- **가용 시간 (Available Hours)**: 다음 근무 시작 전까지 개인이 수면, 휴식, 활동에 배분할 수 있는 남은 시간(분 단위 또는 시간 단위).

### 3. 타임라인 도메인 (AI Timeline Engine)
- **타임라인 모드 (Timeline Mode)**:
  - `TODAY (Real-time)`: 실시간 통합 분석 결과(위험도, 회복상태, 현재 피로도, 남은 가용시간)를 반영한 당일 맞춤형 일정 추천 모드.
  - `FUTURE (Scheduled)`: 미래 날짜의 근무 전환 및 근무 시간표 기반의 표준 권장 루틴 생성 모드 (실시간 생체 지표 제외).
- **타임라인 블록 (Timeline Block)**: 추천 일정의 개별 단위 (`startTime`, `endTime`, `activityType`, `title`, `description`).
  - `Activity Type`: `SLEEP`(수면), `NAP`(낮잠), `MEAL`(식사), `WORK`(근무), `REST`(휴식), `EXERCISE`(가벼운 운동/스트레칭) 등.
- **AI 조언 (AI Advice / Summary)**: 현재 상태 및 근무 전환을 고려하여 간호사에게 제공하는 공감과 맞춤 가이드 텍스트.
