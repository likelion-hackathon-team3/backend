package com.hackathon.backend.feedback;

import com.hackathon.backend.feedback.dto.CaffeineIntakeRequest;
import com.hackathon.backend.feedback.dto.FeedbackRequest;
import com.hackathon.backend.feedback.dto.FeedbackResponse;
import com.hackathon.backend.schedule.Schedule;
import com.hackathon.backend.schedule.ScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

// POST /api/feedback 실제 로직을 담는 계층. Controller와 Repository 사이에 있다.
// 이 프로젝트에는 GlobalExceptionHandler/커스텀 예외 구조가 없으므로,
// 다른 Service들과 같은 방식으로 검증 실패도 예외를 던지지 않고 FeedbackResponse(실패 DTO)를 반환한다.
@Service
public class FeedbackService {

    // API 명세대로 정확히 "HH:mm"만 허용한다(초 포함 등 다른 형식은 거부).
    private static final DateTimeFormatter LAST_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final FeedbackRepository feedbackRepository;
    private final ScheduleRepository scheduleRepository;

    public FeedbackService(FeedbackRepository feedbackRepository, ScheduleRepository scheduleRepository) {
        this.feedbackRepository = feedbackRepository;
        this.scheduleRepository = scheduleRepository;
    }

    // POST /api/feedback : 피드백 저장(같은 feedbackDate면 upsert)
    @Transactional
    public FeedbackResponse save(FeedbackRequest request) {
        if (request == null) {
            return FeedbackResponse.missing();
        }

        CaffeineIntakeRequest caffeineIntake = request.getCaffeineIntake();
        Boolean taken = caffeineIntake == null ? null : caffeineIntake.getTaken();

        // caffeineIntake 자체가 null이면 lastTime도 없는 것으로 취급한다(NPE 방지).
        // null뿐 아니라 빈 문자열("")도 미입력으로 취급한다.
        String rawLastTime = caffeineIntake == null ? null : caffeineIntake.getLastTime();
        boolean lastTimeMissing = rawLastTime == null || rawLastTime.isBlank();

        // 1단계: 필수값 누락 검사.
        // taken=true면 lastTime도 필수다(프론트에 taken=true일 때 입력 UI가 있음을 확인함).
        if (request.getFeedbackDate() == null
                || request.getActualSleepDuration() == null
                || taken == null
                || request.getPostShiftFatigue() == null
                || request.getRoutineHelpfulness() == null
                || (taken && lastTimeMissing)) {
            return FeedbackResponse.missing();
        }

        // 2단계: 날짜/시간 형식 검증
        LocalDate feedbackDate = parseDate(request.getFeedbackDate());
        if (feedbackDate == null) {
            return FeedbackResponse.invalidFormat();
        }

        LocalTime lastTime = null;
        if (!lastTimeMissing) {
            lastTime = parseTime(rawLastTime);
            if (lastTime == null) {
                return FeedbackResponse.invalidFormat();
            }
        }

        // 3단계: 점수 범위 검증
        int fatigue = request.getPostShiftFatigue();
        if (fatigue < 1 || fatigue > 10) {
            return FeedbackResponse.fatigueOutOfRange();
        }

        int helpfulness = request.getRoutineHelpfulness();
        if (helpfulness < 1 || helpfulness > 5) {
            return FeedbackResponse.helpfulnessOutOfRange();
        }

        // taken=false면 요청에 lastTime이 와도 정규화해서 null로 저장한다.
        LocalTime normalizedLastTime = taken ? lastTime : null;

        String transitionType = calculateTransitionType(feedbackDate);
        LocalDateTime now = LocalDateTime.now();

        feedbackRepository.findByFeedbackDate(feedbackDate)
                .ifPresentOrElse(
                        existing -> existing.update(transitionType, request.getActualSleepDuration(),
                                taken, normalizedLastTime, fatigue, helpfulness, now),
                        () -> feedbackRepository.save(new Feedback(feedbackDate, transitionType,
                                request.getActualSleepDuration(), taken, normalizedLastTime,
                                fatigue, helpfulness, now))
                );

        return FeedbackResponse.ok();
    }

    // DELETE /api/feedback?feedbackDate=YYYY-MM-DD : 특정 날짜 피드백 삭제
    @Transactional
    public FeedbackResponse delete(String feedbackDate) {
        LocalDate target = parseDate(feedbackDate);
        if (target == null) {
            return FeedbackResponse.invalidFormat();
        }

        return feedbackRepository.findByFeedbackDate(target)
                .map(feedback -> {
                    feedbackRepository.delete(feedback);
                    return FeedbackResponse.deleted();
                })
                .orElseGet(FeedbackResponse::notFound);
    }

    // feedbackDate와 feedbackDate+1일의 Schedule을 조회해서 transitionType을 계산한다.
    // 둘 중 하나라도 없으면 OFF로 추론하지 않고 null을 반환한다.
    private String calculateTransitionType(LocalDate feedbackDate) {
        Optional<Schedule> current = scheduleRepository.findByDate(feedbackDate);
        Optional<Schedule> next = scheduleRepository.findByDate(feedbackDate.plusDays(1));

        if (current.isEmpty() || next.isEmpty()) {
            return null;
        }
        return current.get().getShift().name() + "_TO_" + next.get().getShift().name();
    }

    // "YYYY-MM-DD" 문자열을 LocalDate로 바꾼다. 형식이 틀리면 null.
    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (Exception e) {
            return null;
        }
    }

    // "HH:mm" 문자열을 LocalTime으로 바꾼다. 형식이 틀리면(초 포함 등) null.
    private LocalTime parseTime(String time) {
        try {
            return LocalTime.parse(time, LAST_TIME_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }
}
