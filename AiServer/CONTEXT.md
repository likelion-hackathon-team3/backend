# Context: AiServer (Vision OCR & AI Timeline Engine)

## 1. 아키텍처 철학: 하이브리드 지능형 라이프케어 (Hybrid Architecture)

- **백엔드 서버 (Deterministic Fallback Engine)**:
  - 수학적/규칙적 기본 연산(시간 뺄셈, 위험도 점수 합산)을 1ms 내에 정확히 수행하며, AI 서버 장애 또는 응답 지연 시 제공할 최소한의 고정 기본 시간표(`isFallback=true`)를 보유합니다.
- **AI 서버 (Adaptive Reasoning & Empathetic Coaching Engine)**:
  - 사용자가 앱을 켠 실시간 시각, 잔여 가용시간, 연속 근무일수, 위험도, 회복상태, 개인 특이사항(`userNotes`)을 종합적으로 추론하여, 정적 템플릿으로는 불가능한 **초개인화 유동 스케줄링과 공감형 라이프 코칭(`isFallback=false`)**을 전담합니다.

---

## 2. 근무표 Vision OCR 도메인 (Schedule OCR Domain)

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
- **추출 원문 (Raw Extraction)**: 이미지 내 텍스트 셀에서 광학적으로 검출된 가공되지 않은 기호나 약어.
- **정규화 (Normalization)**: 비정형 원문 문자열을 사전(Symbol Dictionary)과 규칙을 통해 표준 근무 코드로 맵핑하는 행위.

---

## 3. 통합 분석 지표 (Integrated Analysis Metrics)

- **위험도 (Risk Level)**: 근무 전환 패턴, 연속 근무 일수, 다음 근무까지의 잔여 휴식 시간을 종합 산출한 위험 등급 (`NORMAL`, `CAUTION`, `DANGER`).
- **피로도 (Fatigue Level)**: 사용자의 주관적/측정된 현재 피로 상태 (`LOW`, `MEDIUM`, `HIGH`).
- **회복 상태 (Recovery State / RecoveryStatus)**: 피로도, 수면 시간, 걸음 수, 심박수를 종합 합산한 신체 회복 등급 (`GOOD`, `RECOVERY_NEEDED`, `RECOVERY_PRIORITY`).
- **가용 시간 (Available Hours)**: 통근 시간을 제외하고 수면과 개인 활동에 쓸 수 있는 순수 잔여 시간.
- **연속 근무 일수 (Consecutive Days)**: 휴무(OFF) 없이 연속으로 근무한 일수.

---

## 4. AI 타임라인 엔진 도메인 (AI Timeline Engine Domain)

### 근무 전환 (Shift Transition)
- 직전(현재) 근무와 다음 근무 사이의 16가지 조합 패턴.

### 교대 시간대 (Shift Times)
- 병원 또는 병동별로 상이하게 정의되는 실제 출퇴근 기준 시간대 (`dayTime`, `eveningTime`, `nightTime`).

### 타임라인 시점 및 유동적 스케줄링 (Adaptive Time Horizon)
- **실시간 역산 스케줄링 (Reverse Time-Interpolation)**: 
  - 고정된 시간 템플릿이 아닌, 호출 시점(`currentTime`)부터 다음 출근 시각까지의 남은 시간 구간을 유동적으로 분할하여 수면·식사·준비 시간을 역산 배치합니다.
  - 이미 지나간 과거 시간대의 일정은 배제합니다.
- **사용자 특이사항 반영 (Personal Preferences)**:
  - 카페인 민감도, 수면 보조 습관, 식사 선호 등 `userNotes`를 반영하여 룰 엔진이 흉내 낼 수 없는 맞춤형 조언을 제공합니다.

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
- **페이지 타이틀/서브타이틀 (Page Title & Subtitle)**: 상황에 맞는 감성적이고 직관적인 메인/서브 헤드라인.
- **추천 포인트 (Recommendations)**: 오늘 반드시 유의해야 할 3가지 핵심 실천 가이드.
