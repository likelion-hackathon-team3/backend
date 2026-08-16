# 03 — 고위험 근무 전환(NIGHT→DAY) 및 긴급 가용시간 압축 에지 케이스 대응과 유효성 검증

**What to build:** 가용시간 6시간 미만(DANGER) 시 수면 집중 압축 스케줄링, NIGHT→DAY 직결 전환 등 극단적인 피로 상황에 대한 안전 프롬프트 강화 및 DTO 입력 유효성 검증을 완성합니다.

**Blocked by:** 02 — 당일 실시간 모드 (TODAY Mode) 통합 분석 지표 연동 맞춤 타임라인 생성 파이프라인

**Status:** resolved

- [x] 요청 DTO Bean Validation 및 필드 정규화(transitionType 자동 조합 등) 적용
- [x] 가용시간 6시간 미만 시 필수 수면 위주 압축 스케줄 지침 프롬프트 강화
- [x] NIGHT→DAY 직결 위험 상황(DANGER) 시 회복 최우선 및 안전 경고 메시지 생성 보장
- [x] 에지 케이스 및 유효성/방어 로직 테스트 케이스 작성 및 통과
