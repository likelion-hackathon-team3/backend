# 1. 퇴근후애(愛) - AiServer (근무표 OCR/Vision AI 서버)

간호사 근무표 이미지를 분석하여 텍스트로 추출하고 정규화하는 AI API 서버입니다. 
단순한 OCR 방식을 넘어 OpenCV 전처리와 Vision LLM(GPT-4o)을 결합하여, 노이즈가 많거나 형광펜이 칠해진 비정형 근무표에서도 높은 인식률을 달성합니다.

## 2. 주요 기능 (Features)
- **근무표 텍스트 원문 검출**: 개인 달력형 및 부서 전체 표형 근무표 이미지에서 날짜별 근무 내용 추출
- **Dual-Image Vision 분석**: 원본 이미지와 고대비 흑백 이미지를 동시에 AI에 분석시켜 인식 정확도 극대화
- **OpenCV 기반 이미지 정제**: 
  - 사진 기울기 자동 보정 (Deskew)
  - 형광펜/파스텔톤 배경 마스킹 및 제거
  - 텍스트 셀 Bounding Box 좌표 추출 
- **Rule-based 정규화**: 검출된 원문(D, Day, 데이 등)을 시스템 표준 근무 코드(DAY, EVENING, NIGHT, OFF 등)로 맵핑

## 3. 기술 스택 (Tech Stack)
- **Language**: Java 17
- **Framework**: Spring Boot 4.1.0
- **AI/Vision**: Spring AI 2.0.0, OpenAI GPT-4o
- **Image Processing**: OpenCV 4.9.0
- **Build Tool**: Gradle
- **API Documentation**: Springdoc OpenAPI (Swagger UI)

## 4. 시작 가이드 (Getting Started)

### 사전 요구사항 (Prerequisites)
- Java 17 이상
- OpenAI API Key 발급 필요

### 설치 방법 (Installation)
레포지토리를 클론하고 프로젝트 폴더로 이동합니다.
```bash
git clone https://github.com/likelion-hackathon-team3/backend.git
cd backend/AiServer
```

### 환경 변수 설정 (.env)
프로젝트 최상위 경로(AiServer 폴더 내)에 `.env` 파일을 생성하고 아래와 같이 환경변수를 설정합니다. (`.env.example` 파일 참고)
```properties
# .env 파일 내 작성
OPENAI_API_KEY=your_openai_api_key_here
```

## 5. 실행 및 사용법 (Usage)

개발 서버 실행 (기본 포트: 8080):
```bash
./gradlew bootRun
```
(또는 사용하는 IDE 환경에서 `AiServerApplication` 클래스를 실행합니다.)

빌드 및 테스트:
```bash
./gradlew build
./gradlew test
```

## 6. 프로젝트 구조 (Directory Structure)

```text
AiServer/
├── src/main/java/com/likeLion/backend/aiserver/
│   ├── controller/      # REST API 엔드포인트 계층 (ScheduleOcrController)
│   ├── service/         # 비즈니스 로직 진입점
│   │   └── layer/       # 핵심 OCR/Vision 파이프라인 (OpenCV 전처리, Spring AI 추출, 텍스트 정규화)
│   ├── mapper/          # 추출된 텍스트를 규격화된 타입으로 변환 (ShiftMapper)
│   ├── dto/             # 클라이언트와 통신하는 데이터 모델 
│   └── exception/       # 전역 에러 및 예외 처리
├── src/main/resources/  
│   ├── application.properties # Spring 구동 설정 파일
│   └── prompts/         # Spring AI에서 사용하는 프롬프트 템플릿 (.st)
├── docs/                # 아키텍처 등 프로젝트 문서
├── .env.example         # 환경변수 템플릿 파일
└── build.gradle         # 빌드 스크립트 및 의존성 관리
```

## 7. API 문서 및 상세 아키텍처

- **API Endpoints (Swagger UI)**
  서버 실행 후 브라우저에서 아래 주소로 접속하여 직접 API를 테스트할 수 있습니다.
  👉 `http://localhost:8080/swagger-ui/index.html`
  - `POST /api/ocr` : 이미지 파일, 타겟 사용자명, 근무표 타입을 업로드하여 분석 결과를 반환받는 핵심 API

- **상세 기술 문서**
  OCR 및 Vision 파이프라인에 대한 상세한 기술 원리는 [docs/ocr_vision_architecture.md](./docs/ocr_vision_architecture.md)에서 확인하실 수 있습니다.
