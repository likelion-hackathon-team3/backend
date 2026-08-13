# 근무표 OCR 및 Vision 처리 아키텍처 (팀원 공유용)

## 1. 개요
현재 AiServer의 근무표 인식은 전통적인 OCR 라이브러리(Tesseract 등)에만 의존하지 않고, **OpenCV를 활용한 이미지 전처리 기술**과 **LLM 기반 Vision API(Spring AI)**를 결합한 하이브리드 방식으로 동작합니다. 이를 통해 노이즈가 많은 촬영 이미지나 비정형화된 근무표에서도 높은 인식률을 확보하도록 설계되었습니다.

---

## 2. 전체 처리 파이프라인
- **API Endpoint**: `/api/ocr` 
- **진입점**: `ScheduleOcrController` -> `ScheduleOcrServiceImpl`

전체 프로세스는 크게 **3단계 계층(Layer)**으로 나뉩니다.
1. **이미지 전처리 (OpenCV 기반)**: 노이즈 제거 및 텍스트 부각
2. **Vision 원문 검출 (Dual-Image 처리)**: 원본과 전처리본을 동시 분석하여 원문 텍스트 추출
3. **정규화 (Rule-based Normalization)**: 검출된 텍스트를 시스템 내부 표준 근무 코드로 변환

---

## 3. 세부 동작 원리

### 3.1. 이미지 전처리 단계 (`OpenCvImagePreprocessor.java`)
OpenCV 네이티브 라이브러리를 사용하여 Vision 모델이 인식하기 쉬운 형태로 이미지를 정제합니다. 주요 기능은 다음과 같습니다.

* **기울기 보정 (`deskew`)**: 허프 변환(Hough Transform) 알고리즘으로 문서 내부의 선분을 검출하고 수평선 기준 각도를 계산하여 삐뚤어진 사진을 올바르게 회전(Affine Transform)시킵니다.
* **파스텔톤 배경 제거 (`removePastelBackground`)**: 이미지를 HSV 색공간으로 변환 후, 명도(V) 채널을 활용해 어두운 글자는 보존하고 형광펜 마킹이나 밝은 파스텔톤 배경색은 순백색(White)으로 치환합니다.
* **고대비 흑백 변환**: 
  * **CLAHE (제한적 적응형 히스토그램 균일화)**를 적용해 음영이 고르지 않거나 그림자가 진 이미지의 국지적 대비를 높입니다.
  * **적응형 이진화(Adaptive Threshold)**를 수행하여 텍스트만 뚜렷하게 흑백으로 분리합니다.
* **셀 좌표 추출 (`extractCellCoordinates`)**: 모폴로지(Morphology) 팽창 연산으로 텍스트 영역을 뭉친 후, 외곽선(Contours)을 검출해 각 근무표 셀의 Bounding Box 위치(x, y, w, h)를 JSON 형태로 추출합니다.

### 3.2. Vision 원문 검출 단계 (`MultiNurseTableExtractor` & `PersonalCalendarExtractor`)
Spring AI 모듈의 `ChatModel`을 이용해 LLM Vision 모델(예: OpenAI GPT-4o)에 이미지 기반 질의를 수행합니다.

* **Dual-Image 전략**: 사용자가 업로드한 **원본 이미지**와 OpenCV를 거친 **고대비 흑백 보정 이미지** 2장을 동시에 Vision 모델에 제공합니다. 원본의 문맥적/색상 정보와 보정본의 뚜렷한 텍스트 형태를 상호 보완하여 인식 정확도를 극대화합니다.
* **스마트 프롬프트 & 좌표 주입**: 
  * 근무표 유형(개인 달력 / 부서 전체 표)에 따라 각기 다른 프롬프트 템플릿(`.st`)을 사용합니다.
  * 전처리 단계에서 획득한 셀 좌표 정보(JSON)를 프롬프트에 주입하여 모델이 이미지 내 어느 부분을 분석해야 하는지 가이드합니다.
  * 부서 전체 표(`MultiNurseTableExtractor`)의 경우, 특정 간호사 이름(`userName`)의 Row(행)를 타겟팅하도록 동적 지시문이 추가됩니다.
* **JSON Structured Output (구조화된 응답)**: `BeanOutputConverter`와 OpenAI의 `JSON_OBJECT` 응답 형식을 강제 지정하여, 환각(Hallucination) 없이 `RawExtractionResponse` (날짜, 원문 글자 등) DTO로 정확하게 매핑 받습니다.

### 3.3. 텍스트 정규화 단계 (`ScheduleShiftNormalizer.java`)
Vision 계층에서 추출된 Raw 텍스트를 시스템 표준 포맷으로 변환합니다.

* **규칙 기반 매핑 (`ShiftMapper`)**: 검출된 원문(예: "D", "데이", "Day")과 셀 색상 정보를 종합하여 정해진 근무 형태 타입(`ShiftType` - DAY, EVENING, NIGHT, OFF 등)으로 맵핑합니다.
* 매핑에 성공한 결과는 `RecognizedScheduleDto`로 담고, 매핑할 수 없거나 인식에 실패한 날짜는 `failedDates`로 분리하여 최종 응답 객체(`ScheduleOcrResponse`)로 반환합니다.

---

## 4. 아키텍처 요약 다이어그램

```mermaid
graph TD
    A[Client 업로드] -->|MultipartFile| B(ScheduleOcrController)
    B --> C(ScheduleOcrServiceImpl)
    
    subgraph 1. OpenCV Preprocessing
    C --> D[기울기 보정 & 배경제거]
    D --> E[CLAHE 고대비 흑백화]
    E --> F[셀 Bounding Box 좌표 추출]
    end
    
    subgraph 2. Vision Extraction (Spring AI)
    C -->|원본 + 전처리 이미지<br>+ 좌표 JSON| G[ChatModel Vision API 호출]
    G --> H{근무표 타입}
    H -->|Personal| I[PersonalCalendarExtractor]
    H -->|Multi| J[MultiNurseTableExtractor]
    I --> K[JSON 포맷 원문 텍스트 추출]
    J --> K
    end
    
    subgraph 3. Rule-based Normalization
    K --> L(ScheduleShiftNormalizer)
    L --> M[ShiftMapper 매핑]
    M --> N[ShiftType 정규화 완료]
    end
    
    N --> O[최종 ScheduleOcrResponse 반환]
```

## 5. 핵심 도입 이점
- **강건성(Robustness)**: OpenCV CLAHE와 HSV 마스킹 기법 도입으로 조명 난반사, 구겨짐, 마커펜 칠 등 악의적인(?) 이미지 환경에 매우 강합니다.
- **Vision Hallucination 억제**: 단순히 이미지 통짜를 LLM에 던지는 것이 아니라, "좌표 주입 + Dual 이미지 + JSON 스키마 강제"를 조합하여 AI의 오동작 확률을 크게 낮췄습니다.
- **유연한 확장성**: 전처리(OpenCV)와 원문추출(Spring AI), 정규화(Rule Mapper)가 모듈 단위(Layer)로 완벽하게 분리되어 있어, 향후 병원별로 근무표 양식이 다르더라도 프롬프트 템플릿이나 Mapper만 추가하면 손쉽게 대응 가능합니다.
