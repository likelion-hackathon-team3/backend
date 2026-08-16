# Context: AiServer (Vision OCR & AI Timeline Engine)

## 1. 근무표 Vision OCR 도메인 (Schedule OCR Domain)

### 근무표 유형 (Schedule Type)
- **개인 달력형 (PERSONAL)**: 1인 간호사의 월간 달력 형태로 된 개인 근무표 이미지.
- **부서 표형 (MULTI)**: 병동 내 여러 간호사의 날짜별 근무가 행/열 그리드로 배치된 단체 근무표 이미지.

### 근무 코드 (Shift Code / ShiftType)
- **DAY (주간)**: 아침에 출근하여 낮 동안 수행하는 주간 근무 (기본: 07:00 ~ 15:00).
- **EVENING (오후/저녁)**: 오후에 출근하여 밤늦게 퇴근하는 저녁 근무 (기본: 15:00 ~ 23:00).
- **NIGHT (야간)**: 밤에 출근하여 익일 아침에 퇴근하는 밤샘 야간 근무 (기본: 23:00 ~ 익일 07:00).
- **OFF (휴무)**: 비번 / 쉬는 날.
- **특수 연속 근무 (Special Shifts)**: `DE` (Day-Evening), `EN` (Evening-Night), `ND` (Night-Day), `MD` (Mid 중간근무).

### 원문 추출 및 정규화 (Raw Extraction & Normalization)
- **추출 원문 (Raw Extraction)**: 이미지 내 텍스트 셀에서 광학적으로 검출된 가공되지 않은 기호나 약어 (예: "D", "Day", "나", "데", "오프", "E1" 등).
- **정규화 (Normalization)**: 비정형 원문 문자열을 사전(Symbol Dictionary)과 규칙을 통해 표준 근무 코드로 맵핑하는 행위.

---

## 2. 통합 분석 지표 (Integrated Analysis Metrics)

- **위험도 (Risk Level)**: 근무 전환 패턴, 연속 근무 일수, 다음 근무까지의 잔여 휴식 시간을 종합 산출한 위험 등급.
  - `NORMAL` (정상/안정), `CAUTION` (주의), `DANGER` (위험 / 번아웃 경고).
- **피로도 (Fatigue Level)**: 사용자가 입력하거나 측정한 주관적/객관적 현재 피로 상태.
  - `LOW` (낮음), `MEDIUM` (보통), `HIGH` (높음).
- **회복 상태 (Recovery State / RecoveryStatus)**: 피로도, 수면 시간, 걸음 수(활동량), 심박수를 종합 합산한 신체 회복 등급.
  - `GOOD` (양호), `RECOVERY_NEEDED` (회복 필요), `RECOVERY_PRIORITY` (회복 우선 필요).
- **가용 시간 (Available Hours)**: 이전 근무 퇴근(또는 현재 시점)부터 다음 근무 출근 전까지 통근 시간을 제외하고 수면과 개인 활동에 쓸 수 있는 순수 잔여 시간 (시간 단위 실수).
- **연속 근무 일수 (Consecutive Days)**: 휴무(OFF) 없이 연속으로 근무한 일수.

---

## 3. AI 타임라인 엔진 도메인 (AI Timeline Engine Domain)

### 근무 전환 (Shift Transition)
- 직전(현재) 근무와 다음 근무 사이의 16가지 조합 패턴 (`DAY_TO_DAY`, `DAY_TO_NIGHT`, `EVENING_TO_DAY`, `NIGHT_TO_DAY`, `NIGHT_TO_OFF` 등).

### 교대 시간대 (Shift Times)
- 병원 또는 병동별로 상이하게 정의되는 실제 출퇴근 기준 시간대 (`dayTime`, `eveningTime`, `nightTime`).

### 타임라인 모드 (Timeline Mode)
- **TODAY (당일 실시간 모드)**: 실시간 통합 분석 지표(위험도, 회복도, 가용시간)를 적극 반영하여 수면과 휴식을 동적으로 압축/재배치하는 당일 맞춤형 루틴 생성 모드.
- **FUTURE (미래 예정 모드)**: 미래 날짜의 표준 근무 시간표와 16가지 전환 규칙에 기반하여 최적의 생활 리듬을 안내하는 사전 권장 루틴 생성 모드.

### 타임라인 구성 요소 (Timeline Structure)
- **타임라인 아이템 (Timeline Item)**: 특정 시점("HH:mm")을 기준으로 권장되는 개별 행동 블록 (`time`, `title`, `description`, `category`, `highlight`).
- **활동 카테고리 (Activity Category)**:
  - `SLEEP`: 본 수면 (권장 취침)
  - `NAP`: 쪽잠 / 낮잠 (야간 출근 전 사전 수면 등)
  - `PREPARATION`: 취침 준비(조명 낮추기, 샤워) 및 출근 준비
  - `WAKE_UP`: 기상, 햇볕 쬐기, 물 섭취
  - `MEAL`: 규칙적인 영양 식사
  - `WORK`: 실제 근무 수행
  - `REST`: 휴식 및 심신 이완
  - `EXERCISE`: 가벼운 스트레칭 및 산책
  - `FREE`: 자유 시간 및 이동
- **페이지 타이틀/서브타이틀 (Page Title & Subtitle)**: 다음 근무 형태와 피로도를 직관적으로 안내하는 상단 헤드라인.
- **추천 포인트 (Recommendations)**: 간호사가 오늘 반드시 유의해야 할 3가지 핵심 실천 가이드.
