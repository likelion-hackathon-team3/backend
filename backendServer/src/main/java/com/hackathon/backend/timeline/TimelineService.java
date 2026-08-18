package com.hackathon.backend.timeline;

import com.hackathon.backend.analysis.AnalysisService;
import com.hackathon.backend.analysis.dto.AnalysisResponse;
import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.environment.EnvironmentRepository;
import com.hackathon.backend.personalization.PersonalizationService;
import com.hackathon.backend.personalization.dto.PersonalizationResponse;
import com.hackathon.backend.schedule.Schedule;
import com.hackathon.backend.schedule.ScheduleRepository;
import com.hackathon.backend.schedule.ShiftType;
import com.hackathon.backend.timeline.ai.AiTimelineClient;
import com.hackathon.backend.timeline.ai.AiTimelineConverter;
import com.hackathon.backend.timeline.ai.AiTimelineRequest;
import com.hackathon.backend.timeline.ai.AiTimelineRequestBuilder;
import com.hackathon.backend.timeline.ai.AiTimelineResponse;
import com.hackathon.backend.timeline.dto.TimelineData;
import com.hackathon.backend.timeline.dto.TimelineItemResponse;
import com.hackathon.backend.timeline.dto.TimelineResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

// GET /api/timeline 실제 로직을 담는 계층.
// AiServer(메인 생성기)를 먼저 시도하고, 아래 경우에는 항상 기존 규칙 기반 fallback을 실행한다:
//   - 조회 date가 과거(PAST)인 경우 (AiServer는 TODAY/FUTURE만 지원하기로 팀 합의, PAST는
//     FUTURE 형식으로도 보내지 않고 AiServer를 아예 호출하지 않는다)
//   - TODAY인데 AnalysisService로 현재 상태를 정상적으로 확보하지 못한 경우
//     (success=false 또는 RuntimeException — Analysis 확보 단계의 실패만 fallback 전환 조건)
//   - AI 요청 조립/호출/변환(AI 전용 구간)에서 실패했거나 무효 응답인 경우
// 이 세 경우 모두 isFallback=true로 응답한다. AI가 유효한 응답을 주면 isFallback=false.
//
// Timeline의 transitionType은 AnalysisService의 값을 재사용하지 않고
// schedule(date) + schedule(date+1)의 currentShift/nextShift로 직접 결정한다.
// AnalysisService.analyze()는 TODAY에서 riskLevel, fatigueLevel, recoveryStatus,
// availableHours, consecutiveDays 등 현재 상태를 AI 요청에 전달하기 위해서만 사용한다.
//
// 같은 transitionType 문자열을 PersonalizationService.getPersonalization(transitionType, date) 조회에도
// 그대로 쓴다(GET /api/personalization을 HTTP로 부르지 않고 Service를 직접 주입해서 호출).
// date(=targetDate)를 함께 넘겨서, 조회 대상 날짜 당일이나 그 이후에 쌓인 동일 transitionType
// Feedback이 자기 자신의 타임라인 개인화에 섞이지 않도록 한다(PersonalizationService가 Before로 필터).
// AI 전용 구간 안에서만 호출하므로 PAST/Analysis-확보-실패 시에는 조회 자체가 일어나지 않고,
// 예외가 나도 이 구간을 감싸는 기존 catch가 그대로 fallback으로 흡수한다.
// repeatedPatternFound/recommendedRoutineNotice는 AiServer 요청에 포함하지 않는다(팀 확정).
//
// 이 프로젝트에는 GlobalExceptionHandler/커스텀 예외 구조가 없으므로,
// 다른 Service들과 같은 방식으로 실패 상황도 예외를 던지지 않고
// TimelineResponse(실패 DTO)를 직접 만들어 반환한다. 명시적으로 구분되는 실패
// (날짜 형식 오류 / Schedule 자체 없음 / 선택 날짜(또는 다음날) Schedule 없음)는
// 각자의 계약 메시지를 그대로 쓰고, 그 외 예상하지 못한 RuntimeException은
// "타임라인 정보를 불러오지 못했습니다."(서버 오류 계약)로 묶어서 Spring 기본
// 오류 응답이 그대로 노출되지 않게 한다.
@Service
public class TimelineService {

    private static final Logger log = LoggerFactory.getLogger(TimelineService.class);

    private final ScheduleRepository scheduleRepository;
    private final EnvironmentRepository environmentRepository;
    private final AnalysisService analysisService;
    private final AiTimelineClient aiTimelineClient;
    private final PersonalizationService personalizationService;

    private final TimelineRangeCalculator rangeCalculator = new TimelineRangeCalculator();
    private final TimelineBudgetClassifier budgetClassifier = new TimelineBudgetClassifier();
    private final TimelineItemPlacer itemPlacer = new TimelineItemPlacer();
    private final TimelineTodayFilter todayFilter = new TimelineTodayFilter();
    private final TimelineDescriptionGenerator descriptionGenerator = new TimelineDescriptionGenerator();
    private final AiTimelineRequestBuilder aiRequestBuilder = new AiTimelineRequestBuilder();
    private final AiTimelineConverter aiConverter = new AiTimelineConverter();

    public TimelineService(ScheduleRepository scheduleRepository, EnvironmentRepository environmentRepository,
                           AnalysisService analysisService, AiTimelineClient aiTimelineClient,
                           PersonalizationService personalizationService) {
        this.scheduleRepository = scheduleRepository;
        this.environmentRepository = environmentRepository;
        this.analysisService = analysisService;
        this.aiTimelineClient = aiTimelineClient;
        this.personalizationService = personalizationService;
    }

    @Transactional(readOnly = true)
    public TimelineResponse getTimeline(String dateParam) {
        // 이번 요청 안에서는 now를 한 번만 만들어 "오늘" 판정, PAST/TODAY/FUTURE 분기,
        // TODAY currentTime, TimelineTodayFilter에 동일하게 전달한다.
        LocalDateTime now = LocalDateTime.now();

        LocalDate date;
        if (dateParam == null) {
            date = now.toLocalDate();
        } else {
            try {
                date = LocalDate.parse(dateParam);
            } catch (DateTimeParseException e) {
                return TimelineResponse.fail("날짜 형식이 올바르지 않습니다.");
            }
        }

        try {
            if (scheduleRepository.count() == 0) {
                return TimelineResponse.fail("등록된 근무표가 없습니다.");
            }

            Environment environment = environmentRepository.findTopByOrderByIdAsc().orElse(null);
            if (environment == null) {
                return TimelineResponse.fail("타임라인 정보를 불러오지 못했습니다.");
            }

            Optional<Schedule> currentSchedule = scheduleRepository.findByDate(date);
            Optional<Schedule> nextSchedule = scheduleRepository.findByDate(date.plusDays(1));
            if (currentSchedule.isEmpty() || nextSchedule.isEmpty()) {
                return TimelineResponse.fail("해당 날짜에 등록된 근무가 없습니다.");
            }

            ShiftType currentShift = currentSchedule.get().getShift();
            ShiftType nextShift = nextSchedule.get().getShift();

            TimelineRange range = rangeCalculator.calculate(date, currentShift, nextShift, environment);

            if (date.isBefore(now.toLocalDate())) {
                // PAST는 AI 자체를 시도하지 않으므로(팀 합의) personalization도 조회하지 않는다 —
                // 기존 fallback과 동일하게 보정 없음(0/null)으로 유지한다.
                return runFallback(date, currentShift, nextShift, range, now, 0, null);
            }

            boolean isToday = date.equals(now.toLocalDate());
            AnalysisResponse analysisResponse = null;
            if (isToday) {
                try {
                    AnalysisResponse analysis = analysisService.analyze();
                    if (Boolean.FALSE.equals(analysis.getSuccess())) {
                        return runFallback(date, currentShift, nextShift, range, now, 0, null);
                    }
                    analysisResponse = analysis;
                } catch (RuntimeException e) {
                    // Analysis 확보 단계만의 실패다. fallback 자체 예외는 아래 바깥 catch가 처리한다.
                    return runFallback(date, currentShift, nextShift, range, now, 0, null);
                }
            }

            // AI 성공/실패와 관계없이 동일한 Personalization 보정이 적용되도록, try 블록 밖에서
            // 선언해 기본값(0/null)으로 시작한다. personalization 조회 자체가 성공하면 즉시 이 값을
            // 채워서, 이후 AI 요청 조립/호출이 실패하더라도 아래 fallback에 그대로 전달되게 한다.
            int recommendedSleepBuffer = 0;
            String adjustedCaffeineCutoff = null;

            // AI 전용 구간: 요청 조립/호출/변환 중 문제가 생겨도 Timeline API 전체 실패가 아니라
            // AI 사용 실패로 보고 fallback으로 내려간다. Schedule/Environment/range 확보는 이미
            // 끝난 뒤라 여기서 발생하는 RuntimeException은 AI 경로에서만 난 것으로 간주한다.
            // runFallback 자체는 이 catch 밖에서 실행해, fallback 내부 예외는 바깥 catch가 처리하게 둔다.
            try {
                String transitionType = currentShift.name() + "_TO_" + nextShift.name();
                PersonalizationResponse personalization = personalizationService.getPersonalization(transitionType, date);
                recommendedSleepBuffer = personalization.getRecommendedSleepBuffer();
                adjustedCaffeineCutoff = personalization.getAdjustedCaffeineCutoff();
                log.info("Personalization 계산 완료: transitionType={}, targetDate={}, recommendedSleepBuffer={}, adjustedCaffeineCutoff={}",
                        transitionType, date, recommendedSleepBuffer, adjustedCaffeineCutoff);

                AiTimelineRequest request = aiRequestBuilder.build(date, currentShift, nextShift,
                        range.nextWorkActualStart(), environment, isToday, now, analysisResponse,
                        recommendedSleepBuffer, adjustedCaffeineCutoff);
                Optional<AiTimelineResponse> aiResponse = aiTimelineClient.generate(request);
                log.info("AiTimelineClient.generate() 결과: present={}", aiResponse.isPresent());

                if (aiResponse.isPresent()) {
                    return TimelineResponse.ok(aiConverter.convert(aiResponse.get()), false);
                }
            } catch (RuntimeException e) {
                // AI 요청 조립/호출/변환 실패 -> 아래 기존 fallback으로 진행
                log.warn("AI 전용 구간에서 예외 발생, fallback으로 진행: exceptionClass={}, message={}",
                        e.getClass().getName(), e.getMessage());
            }

            return runFallback(date, currentShift, nextShift, range, now, recommendedSleepBuffer, adjustedCaffeineCutoff);
        } catch (RuntimeException e) {
            return TimelineResponse.fail("타임라인 정보를 불러오지 못했습니다.");
        }
    }

    // 기존에 확정된 규칙 기반 fallback Timeline 생성.
    // PAST / TODAY-Analysis-확보실패 경로는 recommendedSleepBuffer=0, adjustedCaffeineCutoff=null로
    // 호출되어 기존과 완전히 동일하게 동작한다. AI 실패-무효응답 경로만 실제 personalization 값을 받는다.
    // recommendedSleepBuffer는 TimelineItemPlacer가 SLEEP/RECOVERY_SLEEP 길이 상한에 반영하고(방어적으로
    // 0~30으로 정규화됨), adjustedCaffeineCutoff는 TimelineDescriptionGenerator가 recommendation
    // 문구에만 반영한다(카페인은 이 데이터 모델에 실제 시간 슬롯이 없다 — AI 성공 경로도 동일).
    private TimelineResponse runFallback(LocalDate date, ShiftType currentShift, ShiftType nextShift,
                                         TimelineRange range, LocalDateTime now,
                                         int recommendedSleepBuffer, String adjustedCaffeineCutoff) {
        TimelineBudgetLevel budget = budgetClassifier.classify(range.timelineStart(), range.timelineEnd());

        List<TimelineItemDraft> drafts = itemPlacer.place(currentShift, nextShift, range, budget, recommendedSleepBuffer);
        drafts = todayFilter.apply(drafts, date, now);

        List<TimelineItemResponse> items = descriptionGenerator.toItemResponses(drafts, nextShift);
        TimelineData data = new TimelineData(
                descriptionGenerator.pageTitle(range, nextShift),
                descriptionGenerator.pageSubtitle(range),
                items,
                descriptionGenerator.recommendations(currentShift, nextShift, range, budget, adjustedCaffeineCutoff)
        );

        return TimelineResponse.ok(data); // isFallback=true (기존과 동일)
    }
}
