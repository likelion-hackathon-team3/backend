package com.hackathon.backend.timeline;

import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.environment.EnvironmentRepository;
import com.hackathon.backend.schedule.Schedule;
import com.hackathon.backend.schedule.ScheduleRepository;
import com.hackathon.backend.schedule.ShiftType;
import com.hackathon.backend.timeline.dto.TimelineData;
import com.hackathon.backend.timeline.dto.TimelineItemResponse;
import com.hackathon.backend.timeline.dto.TimelineResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

// GET /api/timeline 실제 로직을 담는 계층.
// 이번 단계는 AiServer 연동 전이므로 항상 규칙 기반 fallback Timeline을 생성하고
// isFallback=true로 응답한다(AiServer 연동은 별도 단계에서 이 자리에 추가된다).
//
// Timeline 전환은 항상 schedule(date)+schedule(date+1)만으로 결정하고,
// 기존 AnalysisService/DisplayTransitionCalculator 등은 호출하지도, 수정하지도 않는다.
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

    private final ScheduleRepository scheduleRepository;
    private final EnvironmentRepository environmentRepository;

    private final TimelineRangeCalculator rangeCalculator = new TimelineRangeCalculator();
    private final TimelineBudgetClassifier budgetClassifier = new TimelineBudgetClassifier();
    private final TimelineItemPlacer itemPlacer = new TimelineItemPlacer();
    private final TimelineTodayFilter todayFilter = new TimelineTodayFilter();
    private final TimelineDescriptionGenerator descriptionGenerator = new TimelineDescriptionGenerator();

    public TimelineService(ScheduleRepository scheduleRepository, EnvironmentRepository environmentRepository) {
        this.scheduleRepository = scheduleRepository;
        this.environmentRepository = environmentRepository;
    }

    @Transactional(readOnly = true)
    public TimelineResponse getTimeline(String dateParam) {
        // 이번 요청 안에서는 now를 한 번만 만들어 "오늘" 판정과 TimelineTodayFilter에
        // 동일하게 전달한다(자정 경계에서 서로 다른 호출 결과가 갈리는 것을 막는다).
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
                // Timeline API 명세에 Environment 미등록 전용 메시지가 없으므로 기타 오류 계약을 쓴다.
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
            TimelineBudgetLevel budget = budgetClassifier.classify(range.timelineStart(), range.timelineEnd());

            List<TimelineItemDraft> drafts = itemPlacer.place(currentShift, nextShift, range, budget);
            drafts = todayFilter.apply(drafts, date, now);

            List<TimelineItemResponse> items = descriptionGenerator.toItemResponses(drafts, nextShift);
            TimelineData data = new TimelineData(
                    descriptionGenerator.pageTitle(range, nextShift),
                    descriptionGenerator.pageSubtitle(range),
                    items,
                    descriptionGenerator.recommendations(currentShift, nextShift, range, budget)
            );

            return TimelineResponse.ok(data);
        } catch (RuntimeException e) {
            return TimelineResponse.fail("타임라인 정보를 불러오지 못했습니다.");
        }
    }
}
