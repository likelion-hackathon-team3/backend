# AiServer API 명세서

## 1. 근무표 사진 인식 (OCR) API

* **Endpoint**: `POST /api/ocr/schedule`
* **Description**: 메인 백엔드에서 전달받은 근무표 이미지를 분석하여 텍스트 데이터를 추출하고 구조화하여 반환한다.
* **Content-Type**: `multipart/form-data`

### Request (요청)
* `file`: 근무표 이미지 파일 (jpeg, png 등)

### Response (응답)

#### 성공 및 부분 실패 응답
* **Content-Type**: `application/json`

| 필드명 | 타입 | 설명 |
| --- | --- | --- |
| `success` | boolean | 인식 성공 여부 |
| `recognizedSchedules` | array | 인식된 날짜별 근무 목록 |
| `recognizedSchedules[].date` | string | 날짜 (YYYY-MM-DD) |
| `recognizedSchedules[].shift` | string | 근무유형 ("DAY"/"EVENING"/"NIGHT"/"OFF") |
| `failedDates` | array | 인식 실패한 날짜 목록 |

**성공 예시 (전체 인식 성공)**
```json
{
  "success": true,
  "recognizedSchedules": [
    {"date": "2026-08-10", "shift": "DAY"},
    {"date": "2026-08-11", "shift": "EVENING"}
  ],
  "failedDates": []
}
```

**부분 성공 예시 (일부 날짜 인식 실패)**
```json
{
  "success": true,
  "recognizedSchedules": [
    {"date": "2026-08-10", "shift": "DAY"}
  ],
  "failedDates": [
    "2026-08-12"
  ]
}
```

#### 실패 응답 (전체 인식 실패)
* **Content-Type**: `application/json`

| 필드명 | 타입 | 설명 |
| --- | --- | --- |
| `success` | boolean | `false` 고정 |
| `message` | string | 에러 또는 실패 메시지 |

**예시**
```json
{
  "success": false,
  "message": "이미지를 인식할 수 없습니다. 직접 입력해주세요."
}
```
