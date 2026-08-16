# 1. 퇴근후애(愛) - AiServer (근무표 OCR 및 AI 맞춤 타임라인 엔진)

간호사 근무표 이미지를 분석하여 텍스트로 추출·정규화하고, 실시간 피로도와 근무 전환 패턴을 기반으로 맞춤형 수면·생활 루틴 타임라인을 생성하는 AI API 서버입니다.

- **Vision OCR**: OpenCV 전처리와 Vision LLM(GPT-4o)을 결합하여 형광펜/노이즈가 많은 비정형 근무표에서도 높은 인식률을 달성합니다.
- **AI Timeline Engine**: 16가지 근무 전환 패턴과 실시간 건강/피로 분석 지표(위험도, 회복상태, 가용시간) 및 병원별 출퇴근 시간표를 반영하여 최적의 수면/식사/휴식 시간표를 생성합니다.

---

## 2. 주요 기능 (Features)

### 1) 근무표 이미지 OCR 분석
- **근무표 텍스트 원문 검출**: 개인 달력형(`PERSONAL`) 및 부서 전체 표형(`MULTI`) 근무표 이미지에서 날짜별 근무 내용 추출
- **Dual-Image Vision 분석**: 원본 이미지와 고대비 흑백 이미지를 동시에 AI에 분석시켜 인식 정확도 극대화
- **OpenCV 기반 이미지 정제**: 사진 기울기 자동 보정(Deskew), 형광펜 마스킹 및 제거, 셀 Bounding Box 추출
- **Rule-based 정규화**: 검출된 원문(D, Day, 데이 등)을 시스템 표준 근무 코드(DAY, EVENING, NIGHT, OFF 등)로 맵핑

### 2) AI 맞춤 타임라인 생성 (Dual-Mode)
- **당일 실시간 맞춤 모드 (TODAY)**: 실시간 위험도(`NORMAL`/`CAUTION`/`DANGER`), 회복상태, 피로도, 가용시간(<6시간 수면 압축 등)을 반영한 당일 맞춤형 루틴 및 3대 실천 팁 생성
- **미래 권장 루틴 모드 (FUTURE)**: 16가지 교대 전환 규칙에 기반한 사전 표준 생활 계획표 생성
- **병원별 교대 시간대 동적 앵커링**: 병원마다 상이한 출퇴근 시간표(`ShiftTimes`)를 주입받아 정확한 출근 준비 및 취침 시간표 계산
- **프론트엔드 UI 1:1 직렬화**: 상단 헤드라인(`pageTitle`, `pageSubtitle`), 우측 추천 리스트(`recommendations`), 시간순 타임라인 리스트(`timelineItems` - `time`, `category`, `highlight`) 반환

---

## 3. 기술 스택 (Tech Stack)
- **Language**: Java 17
- **Framework**: Spring Boot 4.1.0
- **AI/Vision**: Spring AI 2.0.0, OpenAI GPT-4o
- **Image Processing**: OpenCV 4.9.0
- **Build Tool**: Gradle
- **API Documentation**: Springdoc OpenAPI (Swagger UI)

---

## 4. 시작 가이드 (Getting Started)

### 사전 요구사항 (Prerequisites)
- Java 17 이상
- OpenAI API Key 발급 필요

### 환경 변수 설정 (.env)
프로젝트 최상위 경로(AiServer 폴더 내)에 `.env` 파일을 생성하고 아래와 같이 환경변수를 설정합니다. (`.env.example` 파일 참고)
```properties
OPENAI_API_KEY=your_openai_api_key_here
```

### 실행 및 테스트
개발 서버 실행 (기본 포트: 8080):
```bash
./gradlew bootRun
```

빌드 및 단위/통합 테스트:
```bash
./gradlew test
```

---

## 5. API 명세 (API Endpoints)

서버 실행 후 브라우저에서 Swagger UI로 접속하여 API를 직접 테스트할 수 있습니다.
👉 `http://localhost:8080/swagger-ui/index.html`

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/api/ocr` | 근무표 이미지 파일 및 타입 업로드 후 분석 결과 반환 |
| `POST` | `/api/timeline/generate` | 근무 전환 정보, 병원 시간표, 통합 분석 지표를 기반으로 AI 맞춤 타임라인 생성 |

---

## 6. 프로젝트 구조 (Directory Structure)

```text
AiServer/
├── src/main/java/com/likeLion/backend/aiserver/
│   ├── controller/      # REST API 엔드포인트 계층 (ScheduleOcrController, TimelineController)
│   ├── service/         # 비즈니스 로직 (ScheduleOcrService, TimelineService)
│   │   └── layer/       # 핵심 AI 레이어 (OpenCV 전처리, Vision 추출, Timeline AI 생성, 정규화)
│   ├── mapper/          # 추출 텍스트 변환 (ShiftMapper)
│   ├── dto/             # 통신 데이터 모델 (OCR DTO 및 timeline DTO)
│   └── exception/       # 전역 예외 처리
├── src/main/resources/  
│   ├── application.properties # Spring 구동 설정
│   └── prompts/         # Spring AI 프롬프트 템플릿 (.st: OCR 프롬프트, timeline_today, timeline_future)
├── docs/                # 아키텍처 및 기술 명세서
│   ├── adr/             # 아키텍처 결정 기록 (ADR-0001)
├── .env.example         # 환경변수 템플릿
└── build.gradle         # 빌드 스크립트 및 의존성
```
