# AiServer 기술 스택 (Tech Stack)

## 1. Core (핵심)
* **Language**: Java 17
* **Framework**: Spring Boot (v3.x 이상)
* **Build Tool**: Gradle

## 2. AI & Data Processing (AI 및 데이터 처리)
* **AI Integration**: Spring AI (`spring-ai-starter-model-openai`)
* **Vision Model (OCR)**: OpenAI `gpt-4o` (또는 `gpt-4-vision-preview`)
* **JSON Parser**: Jackson (Spring Boot 내장)

## 3. API & Communication (통신)
* **Web Framework**: Spring Web (RESTful API 구현)
* **API Testing & Documentation**: Swagger (Springdoc OpenAPI), Postman

## 4. Infra & DevOps (인프라 및 배포)
* **Cloud Infrastructure**: AWS (Amazon Web Services)
* **버전 관리**: Git / GitHub

## 5. 아키텍처 특징
* 메인 백엔드 서버와 분리된 **독립적인 마이크로서비스(Microservice)** 구조.
* DB(데이터베이스) 저장은 메인 백엔드 서버에서 전담하며, AiServer는 별도 DB 없이 메인 백엔드와의 API 통신을 통해 입력(이미지)을 받아 처리 및 JSON 응답만을 반환하는 Stateless AI 전용 서버.
