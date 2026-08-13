# 간호사 근무표 OCR 인식 및 분류 시스템 문서 (Schedule OCR System)

## 1. 개요 및 아키텍처 개관

본 시스템은 멀티모달 Vision AI(OpenAI GPT-4o)와 OpenCV 기반 이미지 전처리, 그리고 Java 하드코딩 매핑 룰 체계를 결합하여 **간호사 및 병원 근무표 이미지 인식률을 극대화한 다단계 파이프라인(Multi-Stage Pipeline)** 서비스입니다.

단일 AI 모델에 전체 판단을 위임하던 방식에서 벗어나, **시각적 레이아웃 분석 / 원문 글자 검출 / 사전 기반 텍스트·색상 정규화 / 이미지 전처리**로 역할을 명확히 모듈화했습니다.

---

## 2. 주요 시스템 파이프라인 (Multi-Layer Architecture)

```
[클라이언트 요청] ── (File, userName, scheduleType: PERSONAL / MULTI)
       │
       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. OpenCV 이미지 전처리 레이어 (OpenCvImagePreprocessor)                      │
│    - 원본 컬러 이미지 + OpenCV 흑백 고대비(Adaptive Thresholding + CLAHE)     │
│    - 기울기 보정 (Deskew): 허프 변환을 이용한 자동 수평 보정                  │
│    - HSV 배경 분리: 파스텔톤 배경색을 순백색으로 치환하여 글자 대비 극대화    │
│    - 두 개 이미지(Media 2장) 생성                                            │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2. Dual-Image Vision 검출 레이어 (MultiNurseTableExtractor / Personal...)   │
│    - MULTI (부서 전체 표) / PERSONAL (개인 달력) 서식별 전용 파서 실행         │
│    - 1번째(컬러): 셀 배경색(cellColor) 감지                                 │
│    - 2번째(고대비): 뭉개진 원문 글자 및 표선(rawShift) 감지                   │
│    - 결과: RawExtractionResponse (날짜별 원문 텍스트 + 배경색 이름)           │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 3. 타입 안정 매퍼 및 사전 정규화 레이어 (ShiftMapper & ScheduleShiftNormalizer) │
│    - AI 환각 완전 차단: Java 코드 기반 화이트리스트 사전 대조                  │
│    - 1순위: 텍스트 우선 매핑 (DE, EN, ND, MD, DAY, EVENING, NIGHT, OFF)       │
│    - 2순위: 레벤슈타인 거리 기반 퍼지 매칭 (오타 자동 보정)                     │
│    - 3순위: 셀 배경색 Fallback 매핑 (YELLOW, GREEN, BLUE, RED/PINK 등)        │
│    - ShiftType Enum으로 표준화                                              │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
                    [최종 ScheduleOcrResponse 반환]
```

---

## 3. 핵심 구성 요소 및 패키지 구조

### 3.1. DTO (`com.likeLion.backend.aiserver.dto`)
- **`ScheduleType` (Enum)**: API 수신 파라미터. `PERSONAL` (개인 달력 / 1인 전용) 또는 `MULTI` (부서 전체 다인원 표, 기본값)
- **`ShiftType` (Enum)**: 표준화된 근무 유형 (`DAY`, `EVENING`, `NIGHT`, `OFF`, `DE`, `EN`, `ND`, `MD`)
- **`RawExtractionResponse`**: Vision 레이어 intermediate 검출 결과 (`date`, `rawShift`, `cellColor`)
- **`ScheduleOcrResponse`**: 최종 클라이언트 반환 데이터 (`recognizedSchedules`, `failedDates`, `success`, `message`)

### 3.2. Symbol 사전 (`com.likeLion.backend.aiserver.symbol`)
- **`ShiftSymbolDictionary`**: 
  - 병원별 근무 약어 사전 관리 (`DAY`, `EVENING`, `NIGHT`, `OFF`, `DE`, `EN`, `ND`, `MD`)
  - 셀 배경색 사전 관리 (`DAY_COLORS`, `EVENING_COLORS`, `NIGHT_COLORS`, `OFF_COLORS`)

### 3.3. Mapper (`com.likeLion.backend.aiserver.mapper`)
- **`ShiftMapper`**:
  - 원문 글자(`rawText`) 및 배경색(`color`)을 받아 `ShiftType` Enum으로 매핑.
  - 이중/변형 근무(`DE`, `EN` 등)를 단일 근무(`D`, `E`)보다 우선하여 오인식 방지.
  - **레벤슈타인 거리(Levenshtein Distance) 퍼지 매칭**: 문자열 길이에 따라 1~2글자 오타를 자동 보정.

### 3.4. Layer 및 Service (`com.likeLion.backend.aiserver.service`)
- **`OpenCvImagePreprocessor`**: OpenCV (`nu.pattern.OpenCV.loadLocally()`) 기반 CLAHE 명암 향상 및 Adaptive Threshold 이진화
- **`MultiNurseTableExtractor`**: 부서 전체 근무표용 Dual-Image 파서 (`prompts/multi-nurse-table.st` 이용)
- **`PersonalCalendarExtractor`**: 개인 달력/1인 전용 표용 Dual-Image 파서 (`prompts/personal-calendar.st` 이용)
- **`ScheduleShiftNormalizer`**: Raw 데이터를 `ShiftMapper`로 정규화 및 에러 처리
- **`ScheduleOcrServiceImpl`**: 파이프라인 통제 오케스트레이터 및 디버깅용 `upload/` 이미지 자동 저장 처리

---

## 4. 프롬프트 템플릿 (`src/main/resources/prompts/`)

Java 코드와 완전히 분리된 Spring StringTemplate(`.st`) 기법 사용:
- **`multi-nurse-table.st`**: 원본 컬러와 OpenCV 고대비 이미지를 비교하여 간호사 행 위치 및 원문/색상 검출
- **`personal-calendar.st`**: 1인/달력 형태의 원문/색상 검출

---

## 5. API 규격 (Controller)

- **Endpoint**: `POST /api/ocr/schedule` (`multipart/form-data`)
- **Request Parameters**:
  - `file` (MultipartFile, 필수): 근무표 이미지 파일
  - `userName` (String, 선택): 특정 간호사 이름 (미입력 시 1인 전용 또는 대표 1인 행 인식)
  - `scheduleType` (ScheduleType Enum, 선택): `MULTI` (기본값) 또는 `PERSONAL`

---

## 6. 주요 개선 성과 및 인식률 향상 포인트

1. **AI 역할 제한 및 코드 규칙 적용**: AI에게 의미 매핑을 시키지 않고 원문 글자와 색상만 추출하도록 하여 환각(Hallucination) 에러를 0%에 가깝게 차단.
2. **OpenCV Dual-Image 파이프라인**: 
   - **흑백 고대비 보정 (CLAHE + Adaptive Threshold)**: 찌그러짐, 어두운 조명, 연한 선 및 글자의 인식률을 대폭 상향.
   - **기울기 보정 (Deskew)**: 허프 변환을 통해 수평선을 검출하여 이미지 회전 보정.
   - **HSV 배경 분리**: 파스텔톤 배경을 하얗게 제거하여 글자 테두리 뭉개짐 방지.
3. **오타 자동 보정 (Fuzzy Matching)**: 레벤슈타인 거리 알고리즘을 도입하여 비전 모델의 오타(예: 'DAY' -> 'OAY')를 유연하게 흡수.
4. **이중/변형 근무 지원**: `DE`, `EN`, `ND`, `MD` 등 특수 근무 지원.
5. **셀 배경색 Fallback**: 글자가 뭉개졌거나 색상으로만 구분된 셀도 색상 인식으로 자동 복구.
