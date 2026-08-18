package com.hackathon.backend.feedback;

import com.hackathon.backend.feedback.dto.CaffeineIntakeRequest;
import com.hackathon.backend.feedback.dto.FeedbackRequest;
import com.hackathon.backend.feedback.dto.FeedbackResponse;
import com.hackathon.backend.schedule.Schedule;
import com.hackathon.backend.schedule.ScheduleRepository;
import com.hackathon.backend.schedule.ShiftType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// FeedbackService 로직 테스트.
// @Transactional : 각 테스트가 끝나면 DB 변경을 자동으로 되돌려 서로 영향을 주지 않게 한다.
@SpringBootTest
@Transactional
class FeedbackServiceTest {

    @Autowired
    FeedbackService feedbackService;

    @Autowired
    FeedbackRepository feedbackRepository;

    @Autowired
    ScheduleRepository scheduleRepository;

    // 요청 DTO를 편하게 만들기 위한 헬퍼 (DTO에 setter가 없어 리플렉션으로 값 주입)
    private FeedbackRequest req(String feedbackDate, Double actualSleepDuration,
                                 Boolean taken, String lastTime,
                                 Integer postShiftFatigue, Integer routineHelpfulness) {
        CaffeineIntakeRequest caffeine = new CaffeineIntakeRequest();
        ReflectionTestUtils.setField(caffeine, "taken", taken);
        ReflectionTestUtils.setField(caffeine, "lastTime", lastTime);

        FeedbackRequest r = new FeedbackRequest();
        ReflectionTestUtils.setField(r, "feedbackDate", feedbackDate);
        ReflectionTestUtils.setField(r, "actualSleepDuration", actualSleepDuration);
        ReflectionTestUtils.setField(r, "caffeineIntake", caffeine);
        ReflectionTestUtils.setField(r, "postShiftFatigue", postShiftFatigue);
        ReflectionTestUtils.setField(r, "routineHelpfulness", routineHelpfulness);
        return r;
    }

    private FeedbackRequest reqNoCaffeine(String feedbackDate, Double actualSleepDuration,
                                           Integer postShiftFatigue, Integer routineHelpfulness) {
        FeedbackRequest r = new FeedbackRequest();
        ReflectionTestUtils.setField(r, "feedbackDate", feedbackDate);
        ReflectionTestUtils.setField(r, "actualSleepDuration", actualSleepDuration);
        ReflectionTestUtils.setField(r, "caffeineIntake", null);
        ReflectionTestUtils.setField(r, "postShiftFatigue", postShiftFatigue);
        ReflectionTestUtils.setField(r, "routineHelpfulness", routineHelpfulness);
        return r;
    }

    @Test
    void 정상_저장_및_transitionType_계산() {
        scheduleRepository.save(new Schedule(LocalDate.parse("2026-08-17"), ShiftType.DAY));
        scheduleRepository.save(new Schedule(LocalDate.parse("2026-08-18"), ShiftType.NIGHT));

        FeedbackResponse res = feedbackService.save(
                req("2026-08-17", 6.5, true, "15:30", 7, 4));

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getMessage()).isEqualTo("피드백이 성공적으로 등록되었습니다.");

        Feedback saved = feedbackRepository.findByFeedbackDate(LocalDate.parse("2026-08-17")).orElseThrow();
        assertThat(saved.getTransitionType()).isEqualTo("DAY_TO_NIGHT");
        assertThat(saved.getActualSleepDuration()).isEqualTo(6.5);
        assertThat(saved.isCaffeineTaken()).isTrue();
        assertThat(saved.getLastCaffeineTime().toString()).isEqualTo("15:30");
        assertThat(saved.getPostShiftFatigue()).isEqualTo(7);
        assertThat(saved.getRoutineHelpfulness()).isEqualTo(4);
        assertThat(saved.getSubmittedAt()).isNotNull();
    }

    @Test
    void Schedule이_하나라도_없으면_transitionType_null이지만_저장은_성공() {
        // feedbackDate(2026-08-20)의 Schedule만 있고 다음 날 Schedule은 없음
        scheduleRepository.save(new Schedule(LocalDate.parse("2026-08-20"), ShiftType.EVENING));

        FeedbackResponse res = feedbackService.save(
                req("2026-08-20", 5.0, false, null, 5, 3));

        assertThat(res.isSuccess()).isTrue();
        Feedback saved = feedbackRepository.findByFeedbackDate(LocalDate.parse("2026-08-20")).orElseThrow();
        assertThat(saved.getTransitionType()).isNull();
    }

    @Test
    void 같은_feedbackDate_재요청시_upsert된다() {
        FeedbackResponse first = feedbackService.save(req("2026-08-21", 6.0, false, null, 4, 3));
        assertThat(first.isSuccess()).isTrue();
        assertThat(feedbackRepository.count()).isEqualTo(1);

        FeedbackResponse second = feedbackService.save(req("2026-08-21", 7.5, true, "22:00", 8, 5));
        assertThat(second.isSuccess()).isTrue();
        assertThat(feedbackRepository.count()).isEqualTo(1); // row가 늘지 않고 갱신됨

        Feedback saved = feedbackRepository.findByFeedbackDate(LocalDate.parse("2026-08-21")).orElseThrow();
        assertThat(saved.getActualSleepDuration()).isEqualTo(7.5);
        assertThat(saved.isCaffeineTaken()).isTrue();
        assertThat(saved.getLastCaffeineTime().toString()).isEqualTo("22:00");
        assertThat(saved.getPostShiftFatigue()).isEqualTo(8);
        assertThat(saved.getRoutineHelpfulness()).isEqualTo(5);
    }

    @Test
    void taken_false면_lastTime이_와도_null로_정규화된다() {
        FeedbackResponse res = feedbackService.save(req("2026-08-22", 6.0, false, "09:00", 5, 3));
        assertThat(res.isSuccess()).isTrue();

        Feedback saved = feedbackRepository.findByFeedbackDate(LocalDate.parse("2026-08-22")).orElseThrow();
        assertThat(saved.isCaffeineTaken()).isFalse();
        assertThat(saved.getLastCaffeineTime()).isNull();
    }

    @Test
    void taken_true_lastTime_null이면_필수값_누락으로_실패한다() {
        // 프론트에 taken=true일 때 lastTime 입력 UI가 있음이 확인되어 이제 필수로 강제한다.
        FeedbackResponse res = feedbackService.save(req("2026-08-23", 6.0, true, null, 5, 3));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("필수 피드백 항목이 누락되었습니다.");
        assertThat(feedbackRepository.findByFeedbackDate(LocalDate.parse("2026-08-23"))).isEmpty();
    }

    @Test
    void request_자체가_null이면_실패() {
        FeedbackResponse res = feedbackService.save(null);
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("필수 피드백 항목이 누락되었습니다.");
        assertThat(feedbackRepository.count()).isEqualTo(0);
    }

    @Test
    void feedbackDate_누락시_실패() {
        FeedbackResponse res = feedbackService.save(req(null, 6.0, false, null, 5, 3));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("필수 피드백 항목이 누락되었습니다.");
    }

    @Test
    void actualSleepDuration_누락시_실패() {
        FeedbackResponse res = feedbackService.save(req("2026-08-24", null, false, null, 5, 3));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("필수 피드백 항목이 누락되었습니다.");
    }

    @Test
    void caffeineIntake_자체가_없으면_실패() {
        FeedbackResponse res = feedbackService.save(reqNoCaffeine("2026-08-25", 6.0, 5, 3));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("필수 피드백 항목이 누락되었습니다.");
    }

    @Test
    void postShiftFatigue_누락시_실패() {
        FeedbackResponse res = feedbackService.save(req("2026-08-26", 6.0, false, null, null, 3));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("필수 피드백 항목이 누락되었습니다.");
    }

    @Test
    void routineHelpfulness_누락시_실패() {
        FeedbackResponse res = feedbackService.save(req("2026-08-27", 6.0, false, null, 5, null));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("필수 피드백 항목이 누락되었습니다.");
    }

    @Test
    void feedbackDate_형식_오류시_실패() {
        FeedbackResponse res = feedbackService.save(req("2026/08/17", 6.0, false, null, 5, 3));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("날짜 또는 시간 형식이 올바르지 않습니다.");
    }

    @Test
    void lastTime_형식_오류시_실패() {
        FeedbackResponse res = feedbackService.save(req("2026-08-17", 6.0, true, "3시30분", 5, 3));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("날짜 또는 시간 형식이 올바르지 않습니다.");
    }

    @Test
    void lastTime에_초가_포함되면_실패한다() {
        // API 명세는 HH:mm만 허용하므로 HH:mm:ss처럼 초가 포함된 값은 거부한다.
        FeedbackResponse res = feedbackService.save(req("2026-08-31", 6.0, true, "15:30:00", 5, 3));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("날짜 또는 시간 형식이 올바르지 않습니다.");
    }

    @Test
    void taken_true_lastTime_빈문자열이면_필수값_누락으로_실패한다() {
        // blank("")도 null과 동일하게 미입력으로 취급하므로 taken=true면 똑같이 실패해야 한다.
        FeedbackResponse res = feedbackService.save(req("2026-09-01", 6.0, true, "", 5, 3));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("필수 피드백 항목이 누락되었습니다.");
        assertThat(feedbackRepository.findByFeedbackDate(LocalDate.parse("2026-09-01"))).isEmpty();
    }

    @Test
    void taken_false면_lastTime이_빈문자열이어도_정상_저장되고_null로_정규화된다() {
        // taken=false면 lastTime은 필수가 아니므로 blank("")도 그대로 허용되고 null로 정규화된다.
        FeedbackResponse res = feedbackService.save(req("2026-09-02", 6.0, false, "", 5, 3));
        assertThat(res.isSuccess()).isTrue();

        Feedback saved = feedbackRepository.findByFeedbackDate(LocalDate.parse("2026-09-02")).orElseThrow();
        assertThat(saved.isCaffeineTaken()).isFalse();
        assertThat(saved.getLastCaffeineTime()).isNull();
    }

    @Test
    void postShiftFatigue_범위_초과시_실패() {
        assertThat(feedbackService.save(req("2026-08-28", 6.0, false, null, 0, 3)).isSuccess()).isFalse();
        FeedbackResponse res = feedbackService.save(req("2026-08-28", 6.0, false, null, 11, 3));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("피로도 점수는 1에서 10 사이여야 합니다.");
    }

    @Test
    void routineHelpfulness_범위_초과시_실패() {
        assertThat(feedbackService.save(req("2026-08-29", 6.0, false, null, 5, 0)).isSuccess()).isFalse();
        FeedbackResponse res = feedbackService.save(req("2026-08-29", 6.0, false, null, 5, 6));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("루틴 도움 정도 점수는 1에서 5 사이여야 합니다.");
    }

    @Test
    void 검증_실패시_저장되지_않는다() {
        feedbackService.save(req("2026-08-30", 6.0, false, null, 99, 3));
        assertThat(feedbackRepository.findByFeedbackDate(LocalDate.parse("2026-08-30"))).isEmpty();
    }
}
