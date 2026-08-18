# Spec: AI 타임라인 생성 정밀도 향상 및 응답 속도 최적화 (Timeline Schedule Precision & Latency Optimization)

Status: ready-for-agent

## Problem Statement

현재 AI 타임라인 생성 엔진(AiServer)은 3교대 간호사를 위한 맞춤형 웰니스 타임라인을 생성하지만, 실제 백엔드 연동 및 다양한 근무 전환 패턴(OFF → NIGHT, DAY → EVENING 등) 테스트에서 다음과 같은 문제점들이 확인되었습니다.

1. **활동 시간 충돌 및 비현실적인 스케줄링**:
   - 낮잠 권장 시간(NAP)과 통근 시간(commuteMinutes) 및 실제 근무 시작 시간(WORK)이 겹치는 현상 발생 (예: 21:30에 90분 낮잠 권장 후 23:00 NIGHT 시작 및 30분 통근으로 인해 물리적으로 출근 불가).
   - 익일 오후 출근(DAY → EVENING)임에도 새벽 05:00에 불필요하게 조기 기상시키거나, 낮잠 후 각성 시간 부족, 식사(MEAL) 아이템 누락 등의 스케줄링 오류 발생.
2. **응답 지연(Latency)으로 인한 타임아웃 위험**:
   - 무거운 LLM 모델(gpt-4o) 사용으로 인해 단일 타임라인 생성에 40~60초가 소요되어 백엔드 호출 시 타임아웃 발생 위험이 상존함.
3. **타이틀 필드(pageTitle, pageSubtitle) 한글 인코딩 불안정**:
   - 프롬프트 템플릿 로딩 및 응답 생성 과정에서 특정 타이틀 필드의 한글 깨짐 현상이 간헐적으로 발생함.

## Solution

1. **프롬프트 내 불변 제약조건(Hard Constraints) 및 역산 공식 명시**:
   - 출근 시각, 편도 통근 시간, 출근 준비/각성 시간(최소 30분)을 기반으로 한 엄격한 역산 한계 시각 공식 적용.
   - 모든 활동(수면, 낮잠, 식사, 준비, 이동, 근무) 간 시간 겹침을 원천 차단(Zero-Overlap)하고 필수 식사 및 안정적 수면 시간을 보장.
2. **타임라인 전용 경량화 모델(gpt-4o-mini) 적용 및 옵션 최적화**:
   - 타임라인 생성 모델을 최적화하여 응답 시간을 2~4초 수준으로 대폭 단축하고 안정적 처리 속도 확보.
3. **UTF-8 인코딩 명시 및 다국어 텍스트 무결성 보장**:
   - 프롬프트 리소스 로딩 시 UTF-8 Charset을 명시적으로 강제하고 프롬프트 내 한국어 출력 규칙 강화.

## User Stories

1. As a 3교대 간호사, I want 야간(NIGHT) 출근 전 낮잠 시간이 출근 준비 및 통근 시간과 겹치지 않게 추천받아서, so that 지각하지 않고 여유롭게 출근할 수 있다.
2. As a 3교대 간호사, I want DAY 근무 후 다음 날 EVENING 근무 시 새벽에 억지로 깨우지 않고 충분한 7~8시간의 본 수면을 추천받아서, so that 피로를 완전히 회복할 수 있다.
3. As a 3교대 간호사, I want 다음 근무 출근 전 적절한 식사(MEAL) 시간을 타임라인에 포함시켜 주어서, so that 공복 상태로 힘들게 근무하지 않을 수 있다.
4. As a 3교대 간호사, I want 낮잠(NAP) 후 바로 출근하는 것이 아니라 20~30분의 각성 및 준비 시간이 확보된 일정을 받아서, so that 수면 관성 없이 맑은 정신으로 출근할 수 있다.
5. As a 모바일 앱 사용자, I want 타임라인 화면의 제목(pageTitle)과 부제목(pageSubtitle)이 깨짐 없는 온전한 한글로 표시되어서, so that 나를 위한 따뜻한 맞춤 가이드를 명확하게 읽을 수 있다.
6. As a 모바일 앱 사용자, I want 타임라인 요청 시 수십 초 동안 대기하지 않고 3~5초 이내에 빠르게 결과를 받아서, so that 앱이 멈춘 것처럼 느끼지 않고 쾌적하게 서비스를 이용할 수 있다.
7. As a 백엔드 시스템(backendServer), I want AiServer가 3초 이내의 빠른 응답 속도와 일관된 JSON 규격을 제공하여, so that 타임아웃 없이 안정적으로 타임라인 데이터를 클라이언트에 전달할 수 있다.
8. As a 3교대 간호사, I want 나의 실제 통근 시간(예: 30분, 45분)이 타임라인의 이동 및 기상 시간에 정밀하게 반영되어서, so that 실제 내 생활 패턴에 꼭 맞는 일정을 따를 수 있다.
9. As a 3교대 간호사, I want 타임라인에 표시되는 모든 일정들이 시간순으로 빈틈없이 자연스럽게 이어져서, so that 하루 일과를 한눈에 직관적으로 파악할 수 있다.
10. As a 3교대 간호사, I want 피로도나 위험도가 높은 날(예: 연속근무 4일 이상) 필수적인 휴식과 수면 위주로 최적화된 일정을 받아서, so that 무리한 활동 없이 건강을 지킬 수 있다.

## Implementation Decisions

1. **프롬프트 역산 스케줄링 제약조건 고도화**:
   - `timeline_today.st` 및 `timeline_future.st`에 활동 간 배타성(Disjoint Time Intervals) 규칙 명시.
   - 역산 규칙: `다음 출근 시각 - 편도 통근 시간(commuteMinutes) - 출근 준비(최소 30분) = 기상/활동 종료 한계 시각`.
   - 활동 지속 시간 기본 가이드 정의: 본 수면(SLEEP, 6~8시간), 쪽잠/낮잠(NAP, 60~90분), 식사(MEAL, 40~60분), 출근 준비(PREPARATION, 30분 이상).
   - 전환 패턴별 기상 시간 현실화: `DAY -> EVENING` 및 `OFF -> EVENING`은 기상 시각을 07:30~09:00 사이로 유도.
2. **타임라인 AI 모델 및 옵션 분리**:
   - OCR/Vision 작업(gpt-4o)과 달리 텍스트 추론 및 JSON 포맷팅에 특화된 경량 모델(`gpt-4o-mini`)을 타임라인 생성에 적용하여 지연 시간 대폭 개선.
   - Temperature 파라미터 안정화(0.2~0.4)를 통해 논리적 시간 계산의 일관성 및 정밀도 향상.
3. **프롬프트 템플릿 로딩 및 인코딩 무결성 확보**:
   - Spring Resource 기반 `PromptTemplate` 생성 시 UTF-8 인코딩 명시.
   - 응답 DTO 변환 시 유니코드 무결성 보장.

## Testing Decisions

1. **외부 동작 중심 테스트 (End-to-End & Service-Level Seams)**:
   - `POST /api/timeline/generate` 엔드포인트 및 `TimelineService.generateTimeline` 계층을 테스트 접점(Seam)으로 활용.
2. **검증 시나리오**:
   - **시나리오 A (OFF -> NIGHT)**: 23:00 출근, 통근 30분 입력 시 낮잠(NAP) 및 준비 일정이 22:00 이전에 정상 종료되는지, 근무/통근과 겹치지 않는지 검증.
   - **시나리오 B (DAY -> EVENING)**: 15:00 출근 시 주 수면 기상 시각이 비현실적인 새벽 5시가 아닌 오전 시간대(07:30~09:00)로 배치되고 출근 전 MEAL이 포함되는지 검증.
   - **시나리오 C (응답 속도 및 인코딩)**: API 호출 시 `pageTitle`, `pageSubtitle`의 한글 깨짐 여부 및 응답 시간 벤치마크 확인.
3. **Prior Art**:
   - 기존 `TimelineControllerTest` 및 `TimelineServiceTest`의 Mock 및 통합 테스트 구조를 확장하여 시간 정합성 검증 assertion 추가.

## Out of Scope

- backendServer 내부의 타임아웃 및 DTO 매핑 코드 수정 (AiServer 외부 영역)
- OCR Vision 이미지 분석 파이프라인 변경 (기존 gpt-4o 유지)
- 새로운 근무 교대 유형(4교대 등) 추가 (기존 16가지 3교대 패턴 유지)

## Further Notes

- 프롬프트 제약조건 강화 후 실제 OpenAI API 호출 테스트를 통해 다양한 경계값(통근 시간 60분, 가용 시간 4시간 등)에 대한 정합성을 확인합니다.
