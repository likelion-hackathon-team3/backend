# Spec: 개인화 보정 지표 단순화 및 탄력적 제약조건 통합 (Personalization Constraint Simplification & Elastic Scheduling)

Status: ready-for-agent

## Problem Statement

이전의 개인화 보정 모델은 4개의 필드(`recommendedSleepBuffer`, `adjustedCaffeineCutoff`, `hasRepeatedPattern`, `personalizationMessage`)를 포함하여 파생 데이터와 서술형 문구가 혼재되어 있었습니다. 이로 인해 다음과 같은 문제점이 있었습니다:

1. **프롬프트 토큰 낭비 및 AI 추론 혼선**: 불필요한 설명 문구와 불리언 플래그가 LLM 프롬프트에 들어가며 추론 정밀도가 분산될 수 있음.
2. **수면 버퍼의 탄력적 반영 가이드 부재**: 가용 시간이 부족한 상황에서 추가 수면 버퍼(+30분)를 무리하게 강제할 경우 시간 충돌이 발생할 수 있어, 남은 시간에 맞추어 유연하게 수면/휴식을 가산하는 탄력적 반영(Elastic Soft Constraint) 규칙이 필요함.
3. **카페인 차단 시각의 명확한 제약화**: 과거 카페인 섭취 이력 및 수면 부족 피드백을 기반으로 산출된 컷오프 시각을 타임라인 설명과 추천사항에 정확한 제약조건으로 반영할 필요가 있음.

## Solution

1. **`PersonalizationDto`를 2대 핵심 제약 수치 필드로 간소화**:
   - `recommendedSleepBuffer`: 추가 권장 수면 시간 (분 단위, 정수, 예: 30)
   - `adjustedCaffeineCutoff`: 카페인 섭취 중단 권장 시각 (String "HH:mm", 예: "14:30")
   - (파생 필드인 `hasRepeatedPattern`, `personalizationMessage`는 DTO에서 제거하거나 기본 처리하여 스키마 단순화)
2. **탄력적 수면 버퍼 가산 규칙(Elastic Sleep Buffer Rule) 명시**:
   - 가용 시간(Available Hours)이 충분한 경우 기본 수면에 최대 `recommendedSleepBuffer`분을 온전히 가산하여 배분.
   - 가용 시간이 부족하거나 고위험인 경우 가능한 범위 내에서만 탄력적으로 수면/이완 시간을 확보하고, 출근/통근 시간을 절대 침범하지 않음.
3. **카페인 컷오프 제약조건의 명확한 반영**:
   - `adjustedCaffeineCutoff`가 제공되면 해당 시각 이후의 모든 식사/휴식 항목 설명 및 3대 실천 팁에 카페인 섭취 금지 및 디카페인/수분 섭취 안내를 필수 배치.

## User Stories

1. As a 3교대 간호사, I want 과거 수면 부족이나 높은 피로 이력이 있을 때 최대 30분의 추가 수면/휴식을 상황에 맞게 탄력적으로 추천받아서, so that 무리한 일정 없이 피로를 안전하게 회복할 수 있다.
2. As a 3교대 간호사, I want 과거에 늦게 커피를 마셔 잠을 설쳤던 날을 기준으로 30분 앞당겨진 카페인 차단 시각(예: 14:30)을 명확하게 안내받아서, so that 숙면을 방해받지 않고 카페인을 관리할 수 있다.
3. As a 백엔드 시스템(backendServer), I want 복잡한 서술 문구 없이 `recommendedSleepBuffer`와 `adjustedCaffeineCutoff` 2가지 숫자/시각 값만 AiServer에 전달하여, so that API 연동 인터페이스가 단순하고 명확해진다.
4. As a 3교대 간호사, I want 가용 시간이 촉박한 날에는 무리하게 수면 시간을 늘려 출근 시간에 쫓기지 않고, 현실적으로 가능한 최대 수면 시간표를 받아서, so that 지각하지 않고 안전하게 출근할 수 있다.
5. As a 모바일 앱 사용자, I want 개인화 보정 지표가 없을 때(0분, null)는 기본 표준 수면과 권장 루틴이 자연스럽게 제공되어서, so that 언제나 일관된 사용자 경험을 얻을 수 있다.

## Implementation Decisions

1. **DTO 스키마 정제 (`PersonalizationDto`)**:
   - 핵심 필드 2개(`recommendedSleepBuffer`, `adjustedCaffeineCutoff`) 중심 구조로 정리.
   - 기존 코드와의 호환성을 위해 2개 인자 전용 생성자 제공.
2. **프롬프트 템플릿 제약조건 단순화 (`timeline_today.st`, `timeline_future.st`)**:
   - 대상 정보 섹션을 2개 제약 지표(추가 수면 버퍼 분, 카페인 차단 시각)로 간결하게 정리.
   - 역산 규칙에 '탄력적 수면 가산' 명시: `가용 시간 한도 내에서 최대 {recommendedSleepBuffer}분을 수면에 가산하되, 출근/통근 불변식을 엄격히 준수`.
   - '카페인 차단 제약': `{adjustedCaffeineCutoff}` 이후 시간대 일정 및 추천사항에 카페인 섭취 중단 안내 강제.
3. **서비스 계층 모델 맵 바인딩 간소화 (`TimelineAiGenerator`)**:
   - 2개 핵심 필드 파싱 및 Null-Safety 처리.

## Testing Decisions

1. **외부 동작 중심 테스트 (Service & API Seams)**:
   - `TimelineServiceImplTest`: `recommendedSleepBuffer=30`, `adjustedCaffeineCutoff="14:30"`이 포함된 요청이 정상 처리되는지 검증.
   - `TimelineAiGeneratorTest`: 프롬프트 렌더링에 2개 제약값이 정확히 주입되는지 검증.
2. **Prior Art**:
   - 기존 `TimelineServiceImplTest` 및 `TimelineAiGeneratorTest` 확장.

## Out of Scope

- 백엔드 내부의 과거 피드백 통계 집계 쿼리 구현 (backendServer 영역).
- 카페인 외 다른 영양 성분(알코올, 야식 등)의 세부 컷오프 알고리즘.

## Further Notes

- 프롬프트 단순화를 통해 모델의 추론 속도와 일관성이 더욱 향상됩니다.
