# [AiServer] AI 맞춤 타임라인 생성 엔진 기능 설명서 (Backend Guide)

---

## 1. 이 기능은 무엇인가요? (Feature Overview)

**"교대근무(3교대) 간호사를 위한 초개인화 AI 웰니스 라이프케어 엔진"**입니다.

3교대 간호사는 불규칙한 근무 전환(예: `DAY→NIGHT`, `EVENING→DAY`)과 연속 근무로 인해 수면 패턴과 생체 리듬이 무너지기 쉽습니다. 
이 엔진은 백엔드에서 계산된 **실시간 건강/피로 분석 지표**, **현재 시각**, **병원별 출퇴근 시간표**, **사용자 개인 메모**를 종합하여, 간호사가 오늘 하루 어떻게 쉬고 언제 자야 하는지 최적의 시간표와 따뜻한 코칭을 실시간으로 생성해 줍니다.

---

## 2. 왜 백엔드 하드코딩이 아닌 AI(LLM)를 사용하나요?

단순히 16가지 규칙표에 적힌 고정 시간(예: "23:00 식사, 00:40 취침")을 내려주는 것이라면 백엔드 `if-else`로 충분합니다. 하지만 실제 사용자의 상황은 훨씬 복잡하고 유동적입니다:

1. **시간 지평선에 따른 유동적 역산 스케줄링 (Reverse Time-Interpolation)**
   - 사용자가 앱을 켜는 시각은 매번 다릅니다(16:15, 17:30, 23:10 등).
   - AI는 **"현재 시각부터 다음 출근 시각까지 남은 가용 시간(예: 6시간 20분)"**을 파악하고, 소화 시간(식사 후 1.5시간)과 수면 시간, 출근 준비 시간을 생체 리듬에 맞추어 **실시간으로 역산하여 빈틈없이 시간표를 조립**합니다.
   - 이미 지나간 과거 시간대의 일정(예: 오후 4시에 켰는데 아침 8시 일정)은 알아서 배제합니다.
2. **다차원 상태 종합 추론 (Holistic Context Reasoning)**
   - `위험도(CAUTION) + 피로도(HIGH) + 4일 연속 근무 + 짧은 가용시간(5.5시간)`처럼 복잡하게 얽힌 상태를 종합 분석하여, 활동적인 일정을 과감히 소거하고 **초압축 수면/회복 중심 루틴**으로 계획의 성격을 동적으로 전환합니다.
3. **진정성 있는 공감형 라이프 코칭**
   - 기계적인 문장이 아니라, 간호사의 현재 피로와 연속근무 상태에 깊이 공감하는 맞춤형 타이틀과 실천 팁 3가지를 매번 살아있는 문장으로 생성합니다.

---

## 3. 핵심 동작 원리 (4대 핵심 기능)

### ① 당일 모드 (TODAY) vs 미래 모드 (FUTURE) 자동 분기
- **당일(오늘) 조회 시**: 요청에 `analysisResult`(통합 분석 결과)를 담아 보내면, AI가 실시간 지표를 반영한 **초개인화 당일 맞춤 모드(`TODAY`)**로 동작합니다.
- **미래(내일 이후) 조회 시**: 요청에 `analysisResult`를 `null`(생략)로 보내면, 미래 날짜의 16가지 근무 전환 규칙에 기반한 **24시간 표준 생활 루틴 모드(`FUTURE`)**로 동작합니다.

### ② 호출 시점(`currentTime`) 앵커링 (과거 시간 제외)
- 당일 모드 호출 시 현재 시각(예: `"16:30"`)을 넘겨주면, AI가 **"현재 시각 이후 ~ 다음 출근 전"**까지의 실천 가능한 남은 일정들만 집중적으로 생성합니다.

### ③ 병원별 실제 교대 시간표(`shiftTimes`) 동적 바인딩
- 병원/병동마다 출퇴근 시간이 제각각 다릅니다 (예: DAY 06:30~14:30 vs 07:00~15:00).
- `shiftTimes` 객체에 사용자의 병원 시간표를 전달하면, AI가 해당 출퇴근 기준에 정확히 맞추어 기상 및 출근 준비, 취침 시각을 계산합니다. (생략 시 표준 07-15 / 15-23 / 23-07 자동 적용)

### ④ 사용자 개인 메모/선호(`userNotes`) 반영
- `"카페인에 민감함"`, `"암막안대 필수"`, `"소화가 잘 안 됨"` 등의 사용자 메모를 전달하면, AI가 식사 메뉴나 취침 팁, 추천 포인트에 이를 적극 반영합니다.

---

## 4. 백엔드-AI 데이터 통신 구조 (API Specification)

- **Endpoint**: `POST /api/timeline/generate` (AiServer 포트)
- **Content-Type**: `application/json`

### 1) 백엔드가 AiServer에 넘겨주는 데이터 (Request)

| 필드명 | 타입 | 필수 | 설명 |
| :--- | :--- | :---: | :--- |
| `targetDate` | String | **Y** | **조회 대상 날짜 (YYYY-MM-DD)** *(누락 시 오늘로 fallback)* |
| `currentShift` | String | **Y** | **현재/기준일 근무 (`DAY`, `EVENING`, `NIGHT`, `OFF`)** |
| `nextShift` | String | **Y** | **다음 근무 (`DAY`, `EVENING`, `NIGHT`, `OFF`)** |
| `transitionType` | String | N | 전환 유형 (예: `EVENING_TO_DAY`). 생략 시 자동 조합 |
| `currentTime` | String | N | **현재 시각 (HH:mm, 예: "16:30")** *(당일 모드 시 현재 이후 일정 위주 생성)* |
| `currentWorkEnd` | String | N | **현재 근무 실제 퇴근 일시 (ISO-8601, 예: "2026-08-17T15:00", OFF 시 null)** |
| `nextWorkStart` | String | N | **다음 근무 실제 출근 일시 (ISO-8601, 예: "2026-08-18T23:00", OFF 시 null)** |
| `commuteMinutes` | Number | N | **편도 통근 시간 (분 단위, 예: 30, 미입력 시 기본 30분 적용)** |
| `userNotes` | String | N | 사용자 개인 특이사항/선호 메모 |
| `shiftTimes` | Object | N | 병원별 실제 교대 시간표 (`dayStart`, `dayEnd`, `eveningStart`, `eveningEnd`, `nightStart`, `nightEnd`) |
| `analysisResult` | Object | N | **실시간 분석 결과 (있으면 TODAY 모드, 없으면 FUTURE 모드로 자동 분기)** |
| `↳ riskLevel` | String | Y* | 위험도 (`NORMAL`, `CAUTION`, `DANGER`) |
| `↳ recoveryStatus` | String | Y* | 회복 상태 (`GOOD`, `RECOVERY_NEEDED`, `RECOVERY_PRIORITY`) |
| `↳ fatigueLevel` | String | Y* | 피로도 (`LOW`, `MEDIUM`, `HIGH`) |
| `↳ availableHours` | Number | Y* | 다음 근무 전 활용 가능 시간 (예: 6.5) |
| `↳ consecutiveDays` | Number | Y* | 연속 근무 일수 (예: 2) |

*(analysisResult 객체를 포함할 때 내부 필드는 필수)*

### 2) AiServer가 백엔드에 돌려주는 데이터 (Response)

**프론트엔드 UI 최종 명세(`pageTitle`, `pageSubtitle`, `timelineItems`, `recommendations`)와 1:1로 일치**하므로, 백엔드에서는 별도의 가공 없이 그대로 프론트엔드의 `data` 필드에 감싸서 반환하시면 됩니다.

| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| `targetDate` | String | 대상 날짜 (YYYY-MM-DD) |
| `mode` | String | 실행 모드 (`TODAY` \| `FUTURE`) |
| `pageTitle` | String | **[상단] 메인 타이틀** (예: "오늘부터 내일 Day 근무 전까지의 맞춤 계획이에요") |
| `pageSubtitle` | String | **[상단] 서브 타이틀** (예: "피로도가 높은 날이에요. 회복을 최우선으로 한 개인 맞춤 루틴입니다.") |
| `timelineItems` | Array | **[중앙] 시간순 정렬된 AI 웰니스 타임라인 리스트 (현재 시각 이후 잔여 일정)** |
| `↳ time` | String | 시각 (HH:mm) |
| `↳ title` | String | 일정 제목 (예: "취침", "저녁 식사") |
| `↳ description` | String | 상세 가이드 및 실천 팁 |
| `↳ category` | String | 활동 카테고리 (`MEAL`, `PREPARATION`, `SLEEP`, `WAKE_UP`, `WORK`, `NAP`, `REST`, `EXERCISE`, `FREE`) |
| `↳ highlight` | String | 강조 문구 (수면 목표 등, 없을 시 null) |
| `recommendations` | Array[String] | **[우측] AI 맞춤 추천 포인트 3선** |

---

## 5. 하이브리드 장애 대응 (Fallback 전략)

- **정상 상황 (`isFallback=false`)**: AiServer를 호출하여 실시간 초개인화 타임라인을 프론트엔드에 전달합니다.
- **비상 상황 (`isFallback=true`)**: AiServer 네트워크 타임아웃이나 예외 발생 시, 백엔드에 기존 구현된 16가지 기본 룰셋 기반의 고정 시간표를 fallback으로 내려주어 서비스 무중단을 보장합니다.
