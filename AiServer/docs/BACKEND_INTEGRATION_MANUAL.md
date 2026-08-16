# 퇴근후애(愛) - AI 맞춤 타임라인 생성 API 백엔드 연동 매뉴얼

본 문서는 `backendServer`에서 `AiServer`의 AI 맞춤 타임라인 생성 엔진(`POST /api/timeline/generate`)을 연동하기 위한 상세 가이드입니다.

---

## 1. 아키텍처 개요 및 역할 분담 (Hybrid Architecture)

- **backendServer (메인 백엔드)**:
  - 사용자 인증, 근무표 DB 조회, 실시간 생체/피로 지표 계산(위험도, 가용시간 등)
  - `AiServer` 호출 및 프론트엔드 반환 (`GET /api/timeline`)
  - AI 서버 타임아웃/장애 발생 시 기본 규칙 기반 시간표 제공 (`isFallback=true`)
- **AiServer (AI 마이크로서비스)**:
  - 실시간 생체 데이터와 현재 시각, 병원별 출퇴근 시간표를 바탕으로 **유동적 역산 스케줄링(Reverse Time-Interpolation)** 및 **공감형 맞춤 코칭** 제공 (`isFallback=false`)

---

## 2. API 기본 정보

- **Method**: `POST`
- **Path**: `/api/timeline/generate`
- **Content-Type**: `application/json`
- **기본 포트**: `http://localhost:8080` (AiServer 기본 포트)

---

## 3. 요청(Request) 규격

### 요청 파라미터 (TimelineGenerateRequest)

| 필드명 | 타입 | 필수 | 설명 |
| :--- | :--- | :---: | :--- |
| `targetDate` | String | N | 조회 대상 날짜 (YYYY-MM-DD). 생략 시 오늘 날짜 |
| `currentShift` | String | N | 기준 근무 (`DAY`, `EVENING`, `NIGHT`, `OFF`). 기본값 `OFF` |
| `nextShift` | String | N | 다음 근무 (`DAY`, `EVENING`, `NIGHT`, `OFF`). 기본값 `OFF` |
| `transitionType` | String | N | 전환 유형 (예: `EVENING_TO_DAY`). 생략 시 `currentShift_TO_nextShift` 자동 조합 |
| `currentTime` | String | N | **호출 시점의 현재 시각 (HH:mm, 예: "16:30")**. 당일 모드 시 이 시각 이후의 잔여 일정만 생성 |
| `userNotes` | String | N | **사용자 개인 특이사항/선호 메모 (예: "카페인 민감, 암막커튼 사용")** |
| `shiftTimes` | Object | N | **병원/사용자 맞춤 근무 시간대 (생략 시 07-15 / 15-23 / 23-07 표준시간 적용)** |
| `↳ dayTime` | String | N | DAY 근무 시간대 (예: "06:30 ~ 14:30") |
| `↳ eveningTime` | String | N | EVENING 근무 시간대 (예: "14:30 ~ 22:30") |
| `↳ nightTime` | String | N | NIGHT 근무 시간대 (예: "22:30 ~ 익일 06:30") |
| `analysisResult` | Object | N | **실시간 통합 분석 결과 (포함 시 당일 TODAY 모드, null/생략 시 미래 FUTURE 모드)** |
| `↳ riskLevel` | String | Y | 위험도 (`NORMAL`, `CAUTION`, `DANGER`) |
| `↳ recoveryStatus` | String | Y | 회복 상태 (`GOOD`, `RECOVERY_NEEDED`, `RECOVERY_PRIORITY`) |
| `↳ fatigueLevel` | String | Y | 피로도 (`LOW`, `MEDIUM`, `HIGH`) |
| `↳ availableHours` | Number | Y | 다음 근무 전 활용 가능 시간 (시간 단위 실수, 예: 6.5) |
| `↳ consecutiveDays` | Number | Y | 연속 근무 일수 (정수, 예: 2) |

---

## 4. 응답(Response) 규격 (프론트엔드 명세와 1:1 매핑)

AiServer의 응답은 프론트엔드 `GET /api/timeline`의 `data` 필드와 완벽히 일치하므로, 별도 가공 없이 그대로 담아서 내려주시면 됩니다.

### 응답 필드 (TimelineGenerateResponse)

| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| `targetDate` | String | 대상 날짜 (YYYY-MM-DD) |
| `mode` | String | 타임라인 생성 모드 (`TODAY` \| `FUTURE`) |
| `pageTitle` | String | **[상단] 메인 타이틀** (예: "오늘부터 내일 Day 근무 전까지의 맞춤 계획이에요") |
| `pageSubtitle` | String | **[상단] 서브 타이틀** (예: "피로도가 높은 날이에요. 회복을 최우선으로 한 개인 맞춤 루틴입니다.") |
| `timelineItems` | Array | **[중앙] 시간순 정렬된 AI 웰니스 타임라인 리스트 (현재 시각 이후 잔여 일정)** |
| `↳ time` | String | 시각 (HH:mm) |
| `↳ title` | String | 일정 제목 |
| `↳ description` | String | 상세 가이드 및 실천 팁 |
| `↳ category` | String | 활동 카테고리 (`MEAL`, `PREPARATION`, `SLEEP`, `WAKE_UP`, `WORK`, `NAP`, `REST`, `EXERCISE`, `FREE`) |
| `↳ highlight` | String | 강조 문구 (수면 목표 등, 없을 시 `null`) |
| `recommendations` | Array[String] | **[우측] AI 맞춤 추천 포인트 3선** |

---

## 5. 백엔드 구현 가이드 (Spring Cloud OpenFeign 예제)

### 1) FeignClient 선언
```java
@FeignClient(name = "ai-server", url = "${ai-server.url:http://localhost:8080}")
public interface AiServerClient {

    @PostMapping("/api/timeline/generate")
    TimelineGenerateResponse generateTimeline(@RequestBody TimelineGenerateRequest request);
}
```

### 2) 서비스 계층 연동 흐름
```java
@Service
@RequiredArgsConstructor
public class TimelineService {

    private final AiServerClient aiServerClient;
    private final ScheduleRepository scheduleRepository;
    private final AnalysisService analysisService;

    public TimelineResponse getTimeline(Long memberId, LocalDate targetDate) {
        LocalDate queryDate = (targetDate != null) ? targetDate : LocalDate.now();
        boolean isToday = queryDate.equals(LocalDate.now());

        // 1. 근무표 조회 (현재 근무, 다음 근무)
        ShiftType currentShift = scheduleRepository.findShiftByDate(memberId, queryDate);
        ShiftType nextShift = scheduleRepository.findShiftByDate(memberId, queryDate.plusDays(1));

        // 2. 당일인 경우 실시간 지표 계산, 미래인 경우 null
        AnalysisResultDto analysisResult = null;
        String currentTime = null;
        if (isToday) {
            analysisResult = analysisService.calculateRealtimeMetrics(memberId);
            currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        }

        // 3. 사용자 맞춤 병원 시간대 및 메모 조회
        ShiftTimesDto shiftTimes = scheduleRepository.findHospitalShiftTimes(memberId);
        String userNotes = scheduleRepository.findUserPreferences(memberId);

        // 4. AiServer 요청 구성
        TimelineGenerateRequest aiRequest = new TimelineGenerateRequest(
                queryDate,
                currentShift,
                nextShift,
                null, // transitionType (자동 조합)
                currentTime,
                userNotes,
                shiftTimes,
                analysisResult
        );

        try {
            // 5. AI 타임라인 생성 호출
            TimelineGenerateResponse aiResponse = aiServerClient.generateTimeline(aiRequest);
            return TimelineResponse.success(aiResponse);

        } catch (Exception e) {
            log.error("AiServer 호출 실패, 기본 Fallback 규칙을 제공합니다.", e);
            // 6. 장애 시 Fallback 규칙 제공
            return TimelineResponse.fallback(generateFallbackTimeline(queryDate, currentShift, nextShift));
        }
    }
}
```

---

## 6. 호출 분기 요약

- **오늘 날짜 조회 시 (`isToday == true`)**:
  - `analysisResult` 및 `currentTime`을 함께 전달 → **`TODAY` 모드** 동작.
  - AI가 이미 지난 과거 시간을 제외하고 현재 이후의 잔여 일정 및 맞춤 수면을 역산하여 반환합니다.
- **미래 날짜 조회 시 (`isToday == false`)**:
  - `analysisResult`를 `null`로 전달 → **`FUTURE` 모드** 동작.
  - AI가 16가지 근무 전환 규칙에 기반한 24시간 표준 생활 루틴을 반환합니다.
