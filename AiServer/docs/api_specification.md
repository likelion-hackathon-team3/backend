# AiServer API 명세서

## 1. 근무표 사진 인식 (OCR) API

* **Endpoint**: `POST /api/ocr`
* **Description**: 근무표 이미지를 분석하여 텍스트 데이터를 추출하고 정규화된 표준 근무 일정 목록으로 반환합니다.
* **Content-Type**: `multipart/form-data`

### Request (요청)
* `file`: 근무표 이미지 파일 (jpeg, png 등)
* `targetName`: 인식 대상 간호사 이름 (부서 전체 표형일 때 필수)
* `type`: 근무표 유형 (`PERSONAL` / `MULTI`)

### Response (응답)
* **Content-Type**: `application/json`

| 필드명 | 타입 | 설명 |
| --- | --- | --- |
| `success` | boolean | 인식 성공 여부 |
| `recognizedSchedules` | array | 인식된 날짜별 근무 목록 |
| `recognizedSchedules[].date` | string | 날짜 (YYYY-MM-DD) |
| `recognizedSchedules[].shift` | string | 근무유형 (`DAY`/`EVENING`/`NIGHT`/`OFF`) |
| `failedDates` | array | 인식 실패한 날짜 목록 |

---

## 2. AI 맞춤 타임라인 생성 API

* **Endpoint**: `POST /api/timeline/generate`
* **Description**: 16가지 근무 전환 패턴, 병원별 실제 근무 시간대, 실시간 통합 분석 지표(선택)를 바탕으로 당일(`TODAY`) 또는 미래(`FUTURE`) 맞춤형 생활 루틴 및 추천 가이드를 생성합니다.
* **Content-Type**: `application/json`

### Request (요청)

| 필드명 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `targetDate` | string | N | 대상 날짜 (YYYY-MM-DD). 미입력 시 오늘 |
| `currentShift` | string | N | 현재/기준일 근무 (`DAY`, `EVENING`, `NIGHT`, `OFF`). 기본값 `OFF` |
| `nextShift` | string | N | 다음 근무 (`DAY`, `EVENING`, `NIGHT`, `OFF`). 기본값 `OFF` |
| `transitionType` | string | N | 근무 전환 유형 (예: `EVENING_TO_DAY`). 미입력 시 자동 조합 |
| `shiftTimes` | object | N | 병원별 실제 교대 시간대 설정 (미입력 시 표준 시간 기준 적용) |
| `↳ dayTime` | string | N | DAY 근무 시간대 (예: "06:30 ~ 14:30") |
| `↳ eveningTime` | string | N | EVENING 근무 시간대 (예: "14:30 ~ 22:30") |
| `↳ nightTime` | string | N | NIGHT 근무 시간대 (예: "22:30 ~ 익일 06:30") |
| `analysisResult` | object | N | 실시간 통합 분석 결과 (**포함 시 TODAY 모드, 생략 시 FUTURE 모드로 자동 분기**) |
| `↳ riskLevel` | string | Y | 위험도 (`NORMAL`, `CAUTION`, `DANGER`) |
| `↳ recoveryStatus` | string | Y | 회복 상태 (`GOOD`, `RECOVERY_NEEDED`, `RECOVERY_PRIORITY`) |
| `↳ fatigueLevel` | string | Y | 피로도 (`LOW`, `MEDIUM`, `HIGH`) |
| `↳ availableHours` | number | Y | 다음 근무 전 활용 가능 시간 (예: 6.5) |
| `↳ consecutiveDays` | number | Y | 연속 근무 일수 (예: 2) |

### Request 예시 (당일 맞춤 모드)
```json
{
  "targetDate": "2026-07-12",
  "currentShift": "EVENING",
  "nextShift": "DAY",
  "transitionType": "EVENING_TO_DAY",
  "shiftTimes": {
    "dayTime": "06:30 ~ 14:30",
    "eveningTime": "14:30 ~ 22:30",
    "nightTime": "22:30 ~ 06:30"
  },
  "analysisResult": {
    "riskLevel": "CAUTION",
    "recoveryStatus": "RECOVERY_NEEDED",
    "fatigueLevel": "HIGH",
    "availableHours": 6.5,
    "consecutiveDays": 2
  }
}
```

### Response (응답)
* **Content-Type**: `application/json`

| 필드명 | 타입 | 설명 |
| --- | --- | --- |
| `targetDate` | string | 대상 날짜 (YYYY-MM-DD) |
| `mode` | string | 타임라인 생성 모드 (`TODAY` \| `FUTURE`) |
| `pageTitle` | string | 메인 헤드라인 (상단 타이틀) |
| `pageSubtitle` | string | 서브 헤드라인 (상단 서브타이틀) |
| `timelineItems` | array | 시간순으로 정렬된 AI 웰니스 타임라인 리스트 |
| `↳ time` | string | 시작/해당 시각 (HH:mm) |
| `↳ title` | string | 일정 제목 |
| `↳ description` | string | 상세 가이드 및 팁 |
| `↳ category` | string | 활동 카테고리 (`MEAL`, `PREPARATION`, `SLEEP`, `WAKE_UP`, `WORK`, `NAP`, `REST`, `EXERCISE`, `FREE`) |
| `↳ highlight` | string | 강조 문구 (수면 목표 등, 없을 시 null) |
| `recommendations` | array(string) | AI 맞춤 추천 포인트 리스트 (3개) |

### Response 예시
```json
{
  "targetDate": "2026-07-12",
  "mode": "TODAY",
  "pageTitle": "오늘부터 내일 Day 근무 전까지의 맞춤 계획이에요",
  "pageSubtitle": "피로도가 높은 날이에요. 회복을 최우선으로 한 개인 맞춤 루틴입니다.",
  "timelineItems": [
    {
      "time": "23:00",
      "title": "저녁 식사",
      "description": "단백질 위주의 가벼운 식사를 권장해요.",
      "category": "MEAL",
      "highlight": null
    },
    {
      "time": "23:40",
      "title": "취침 준비",
      "description": "샤워 및 조명 낮추기, 디지털 기기 사용 줄이기",
      "category": "PREPARATION",
      "highlight": null
    },
    {
      "time": "00:10",
      "title": "취침 (권장 취침 시간)",
      "description": "수면 목표 5시간 20분",
      "category": "SLEEP",
      "highlight": "권장 수면 시간: 5시간 20분"
    },
    {
      "time": "05:30",
      "title": "기상",
      "description": "햇빛을 10분 이상 쬐고 물 한 잔을 마셔요.",
      "category": "WAKE_UP",
      "highlight": null
    },
    {
      "time": "06:30",
      "title": "D 근무 시작",
      "description": "파이팅! 오늘도 잘 해내요!",
      "category": "WORK",
      "highlight": null
    }
  ],
  "recommendations": [
    "오늘은 수면 확보가 가장 중요해요.",
    "카페인은 14시 이후 섭취를 피해 주세요.",
    "낮잠이 필요하면 20분 이내로 짧게 유지하세요."
  ]
}
```
