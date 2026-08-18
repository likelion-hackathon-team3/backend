# 01 — PersonalizationDto 2대 제약조건 단순화 및 탄력적 수면 가산 프롬프트 반영

**What to build:**
`PersonalizationDto`를 불필요한 파생 문구 없이 핵심 2대 제약 수치(`recommendedSleepBuffer`, `adjustedCaffeineCutoff`)로 간소화하고, 프롬프트 템플릿에 가용 시간에 맞춘 탄력적 수면/휴식 가산(Elastic Constraint) 및 카페인 컷오프 제약 규칙을 명확히 반영하여 API 인터페이스와 추론 품질을 고도화합니다.

**Blocked by:** None — can start immediately

**Status:** resolved

- [x] `PersonalizationDto`가 `recommendedSleepBuffer`(Integer)와 `adjustedCaffeineCutoff`(String) 2개 필드 중심으로 깔끔하게 정의된다.
- [x] 프롬프트에서 `recommendedSleepBuffer`가 주어지면 가용 시간 한도 내에서 탄력적으로 수면에 가산되고 highlight에 반영된다.
- [x] 프롬프트에서 `adjustedCaffeineCutoff`가 주어지면 해당 시각 이후 카페인 섭취 중단 안내가 타임라인 및 추천 팁에 정확히 반영된다.
- [x] 기존 및 신규 단위 테스트(`./gradlew test`)가 100% 통과한다.
- [x] 연동 문서(`BACKEND_INTEGRATION_MANUAL.md`, `api_specification.md`)에 2개 핵심 필드 중심의 깔끔한 요청 스펙이 업데이트된다.
