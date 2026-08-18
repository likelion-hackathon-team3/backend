package com.hackathon.backend.dashboard;

import com.hackathon.backend.dashboard.dto.DashboardAlertResponse;
import com.hackathon.backend.dashboard.dto.DashboardResponse;
import com.hackathon.backend.feedback.Feedback;
import com.hackathon.backend.feedback.FeedbackRepository;
import com.hackathon.backend.schedule.Schedule;
import com.hackathon.backend.schedule.ScheduleRepository;
import com.hackathon.backend.schedule.ShiftType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// DashboardService(GET /api/dashboard 사전예방 알림 조회) 로직 테스트.
// @Transactional : 각 테스트가 끝나면 DB 변경을 자동으로 되돌려 서로 영향을 주지 않게 한다.
// JSON 모양 검증(propertyNames 등)은 AnalysisResponseTest와 동일하게 Jackson 3(tools.jackson.databind) API를 쓴다.
@SpringBootTest
@Transactional
class DashboardServiceTest {

    @Autowired
    DashboardService dashboardService;

    @Autowired
    ScheduleRepository scheduleRepository;

    @Autowired
    FeedbackRepository feedbackRepository;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private void schedule(String date, ShiftType shift) {
        scheduleRepository.save(new Schedule(LocalDate.parse(date), shift));
    }

    private void feedback(String feedbackDate, String transitionType, double sleep,
                           boolean caffeineTaken, String lastCaffeineTime,
                           int fatigue, int routine) {
        feedbackRepository.save(new Feedback(
                LocalDate.parse(feedbackDate),
                transitionType,
                sleep,
                caffeineTaken,
                lastCaffeineTime == null ? null : LocalTime.parse(lastCaffeineTime),
                fatigue,
                routine,
                LocalDateTime.now()));
    }

    // 1. startDate/endDate 정상 조회
    @Test
    void 정상_조회시_success_true와_alert_내용을_반환한다() {
        schedule("2026-08-10", ShiftType.EVENING);
        schedule("2026-08-11", ShiftType.DAY);
        feedback("2026-08-01", "EVENING_TO_DAY", 7.0, false, null, 9, 4);

        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-16");

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getMessage()).isNull();
        assertThat(res.getAlerts()).hasSize(1);

        DashboardAlertResponse alert = res.getAlerts().get(0);
        assertThat(alert.getDate()).isEqualTo("2026-08-10");
        assertThat(alert.getTransitionType()).isEqualTo("EVENING_TO_DAY");
        assertThat(alert.getRiskLevel()).isEqualTo("DANGER");
    }

    // 2. startDate 누락
    @Test
    void startDate_누락이면_실패응답() {
        DashboardResponse res = dashboardService.getDashboard(null, "2026-08-16");
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("조회 시작일과 종료일을 입력해주세요.");
        assertThat(res.getAlerts()).isNull();
    }

    // 3. endDate 누락
    @Test
    void endDate_누락이면_실패응답() {
        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "");
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("조회 시작일과 종료일을 입력해주세요.");
    }

    // 4. 날짜 형식 오류
    @Test
    void 날짜_형식_오류면_실패응답() {
        schedule("2026-08-10", ShiftType.DAY);
        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-13-01");
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("날짜 형식이 올바르지 않습니다.");
    }

    // 5. startDate > endDate
    @Test
    void 시작일이_종료일보다_늦으면_실패응답() {
        schedule("2026-08-10", ShiftType.DAY);
        DashboardResponse res = dashboardService.getDashboard("2026-08-16", "2026-08-10");
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("조회 기간을 확인해주세요.");
    }

    // 6. Schedule 자체가 하나도 없는 경우
    @Test
    void 등록된_근무표가_전혀_없으면_실패응답() {
        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-16");
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("등록된 근무표가 없습니다.");
    }

    // 7. D 또는 D+1 Schedule 누락 -> OFF 추론 없이 해당 날짜만 skip
    @Test
    void 다음날_스케줄이_없으면_해당_날짜는_건너뛴다() {
        schedule("2026-08-10", ShiftType.EVENING);
        // 2026-08-11 Schedule 없음 -> 2026-08-10 후보는 skip
        schedule("2026-08-12", ShiftType.DAY);
        feedback("2026-08-05", "EVENING_TO_DAY", 5.0, false, null, 8, 4);

        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-12");
        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getAlerts()).isEmpty();
    }

    // 8. 동일 transition Feedback 0개 -> alert 없음
    @Test
    void 동일전환_과거Feedback_없으면_alert_없음() {
        schedule("2026-08-10", ShiftType.EVENING);
        schedule("2026-08-11", ShiftType.DAY);

        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-11");
        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getAlerts()).isEmpty();
    }

    // 9. avgFatigue < 5 -> NORMAL -> alert 없음
    @Test
    void avgFatigue_5미만이면_NORMAL이라_alert_없음() {
        schedule("2026-08-10", ShiftType.EVENING);
        schedule("2026-08-11", ShiftType.DAY);
        feedback("2026-08-01", "EVENING_TO_DAY", 7.0, false, null, 4, 4);

        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-11");
        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getAlerts()).isEmpty();
    }

    // 10. avgFatigue == 5.0 -> CAUTION (경계값)
    @Test
    void avgFatigue_5점이면_CAUTION() {
        schedule("2026-08-10", ShiftType.EVENING);
        schedule("2026-08-11", ShiftType.DAY);
        feedback("2026-08-01", "EVENING_TO_DAY", 7.0, false, null, 5, 4);

        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-11");
        assertThat(res.getAlerts()).hasSize(1);
        assertThat(res.getAlerts().get(0).getRiskLevel()).isEqualTo("CAUTION");
    }

    // 11. avgFatigue == 8.0 -> DANGER (경계값)
    @Test
    void avgFatigue_8점이면_DANGER() {
        schedule("2026-08-10", ShiftType.EVENING);
        schedule("2026-08-11", ShiftType.DAY);
        feedback("2026-08-01", "EVENING_TO_DAY", 7.0, false, null, 8, 4);

        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-11");
        assertThat(res.getAlerts()).hasSize(1);
        assertThat(res.getAlerts().get(0).getRiskLevel()).isEqualTo("DANGER");
    }

    // 12. Feedback 1개 + 수면 < 6 -> "확인되었습니다" 문구
    @Test
    void 수면부족_기록_1개면_확인_문구() {
        schedule("2026-08-10", ShiftType.EVENING);
        schedule("2026-08-11", ShiftType.DAY);
        feedback("2026-08-01", "EVENING_TO_DAY", 5.0, false, null, 6, 4);

        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-11");
        DashboardAlertResponse alert = res.getAlerts().get(0);
        assertThat(alert.getMessage()).isEqualTo("이전 동일 전환에서 수면 부족이 확인되었습니다.");
        assertThat(alert.getRecommendation()).isEqualTo("오늘은 다른 활동보다 충분한 수면 시간을 확보해주세요.");
    }

    // 13. Feedback 2개 이상 + 수면 < 6(둘 다) -> "반복되었습니다" 문구
    @Test
    void 수면부족_기록_2개이상이면_반복_문구() {
        schedule("2026-08-10", ShiftType.EVENING);
        schedule("2026-08-11", ShiftType.DAY);
        feedback("2026-08-01", "EVENING_TO_DAY", 5.0, false, null, 6, 4);
        feedback("2026-08-03", "EVENING_TO_DAY", 5.5, false, null, 6, 4);

        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-11");
        DashboardAlertResponse alert = res.getAlerts().get(0);
        assertThat(alert.getMessage()).isEqualTo("이전 동일 전환에서 수면 부족이 반복되었습니다.");
    }

    // 14. DANGER + 수면 부족 동시 -> 수면 부족 recommendation 우선
    @Test
    void DANGER와_수면부족이_동시면_수면부족이_우선() {
        schedule("2026-08-10", ShiftType.EVENING);
        schedule("2026-08-11", ShiftType.DAY);
        feedback("2026-08-01", "EVENING_TO_DAY", 5.0, false, null, 9, 4);

        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-11");
        DashboardAlertResponse alert = res.getAlerts().get(0);
        assertThat(alert.getRiskLevel()).isEqualTo("DANGER");
        assertThat(alert.getMessage()).contains("수면 부족");
    }

    // 15. 높은 피로 recommendation
    @Test
    void 높은피로_패턴_recommendation() {
        schedule("2026-08-10", ShiftType.EVENING);
        schedule("2026-08-11", ShiftType.DAY);
        feedback("2026-08-01", "EVENING_TO_DAY", 7.0, false, null, 9, 4);

        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-11");
        DashboardAlertResponse alert = res.getAlerts().get(0);
        assertThat(alert.getMessage()).isEqualTo("이전 동일 전환에서 높은 피로도가 확인되었습니다.");
        assertThat(alert.getRecommendation())
                .isEqualTo("다음 근무 전 충분한 휴식 시간을 확보하고 무리한 활동은 줄여주세요.");
    }

    // 16. 늦은 카페인 recommendation
    @Test
    void 늦은카페인_패턴_recommendation() {
        schedule("2026-08-10", ShiftType.EVENING);
        schedule("2026-08-11", ShiftType.DAY);
        feedback("2026-08-01", "EVENING_TO_DAY", 7.0, true, "19:00", 5, 4);

        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-11");
        DashboardAlertResponse alert = res.getAlerts().get(0);
        assertThat(alert.getMessage()).isEqualTo("이전 동일 전환에서 늦은 시간 카페인 섭취가 확인되었습니다.");
        assertThat(alert.getRecommendation()).isEqualTo("예정된 수면에 가까운 시간에는 카페인 섭취를 줄여주세요.");
    }

    // 17. 낮은 routineHelpfulness recommendation
    @Test
    void 낮은루틴도움_패턴_recommendation() {
        schedule("2026-08-10", ShiftType.EVENING);
        schedule("2026-08-11", ShiftType.DAY);
        feedback("2026-08-01", "EVENING_TO_DAY", 7.0, false, null, 5, 2);

        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-11");
        DashboardAlertResponse alert = res.getAlerts().get(0);
        assertThat(alert.getMessage()).isEqualTo("이전 동일 전환에서 기존 루틴의 도움 정도가 낮게 나타났습니다.");
        assertThat(alert.getRecommendation()).isEqualTo("기존 루틴의 강도를 낮추고 수면과 휴식 중심으로 조정해보세요.");
    }

    // 18. 세부 패턴 없음 + CAUTION/DANGER -> generic recommendation.
    // 반복 여부는 postShiftFatigue >= 5인 개별 발생 횟수로 판단(전체 개수 아님).
    @Test
    void 세부패턴_없을때_generic_문구는_고위험_발생횟수_기준() {
        schedule("2026-08-10", ShiftType.EVENING);
        schedule("2026-08-11", ShiftType.DAY);
        // 평균 fatigue = (6+4)/2 = 5.0 -> CAUTION. postShiftFatigue>=5인 기록은 1개뿐(6).
        feedback("2026-08-01", "EVENING_TO_DAY", 7.0, false, null, 6, 4);
        feedback("2026-08-03", "EVENING_TO_DAY", 7.0, false, null, 4, 4);

        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-11");
        DashboardAlertResponse alert = res.getAlerts().get(0);
        assertThat(alert.getRiskLevel()).isEqualTo("CAUTION");
        assertThat(alert.getMessage()).isEqualTo("이전 동일 전환에서 피로도가 높게 나타났습니다.");
        assertThat(alert.getRecommendation()).isEqualTo("이번 전환 전에는 회복 시간을 충분히 확보해주세요.");
    }

    // 18-b. generic 반복 문구: postShiftFatigue >= 5인 기록이 2개 이상이면 반복 표현.
    @Test
    void 세부패턴_없을때_고위험_발생_2회면_반복_문구() {
        schedule("2026-08-10", ShiftType.EVENING);
        schedule("2026-08-11", ShiftType.DAY);
        feedback("2026-08-01", "EVENING_TO_DAY", 7.0, false, null, 6, 4);
        feedback("2026-08-03", "EVENING_TO_DAY", 7.0, false, null, 5, 4);

        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-11");
        DashboardAlertResponse alert = res.getAlerts().get(0);
        assertThat(alert.getMessage()).isEqualTo("이전 동일 전환에서 피로도가 반복적으로 높게 나타났습니다.");
    }

    // 19. candidate date 이후의 Feedback은 과거 데이터로 사용하지 않음
    @Test
    void alert_날짜_이후_Feedback은_과거데이터로_사용하지_않는다() {
        schedule("2026-08-10", ShiftType.EVENING);
        schedule("2026-08-11", ShiftType.DAY);
        feedback("2026-08-10", "EVENING_TO_DAY", 5.0, false, null, 9, 4);
        feedback("2026-08-12", "EVENING_TO_DAY", 5.0, false, null, 9, 4);

        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-11");
        assertThat(res.getAlerts()).isEmpty();
    }

    // 20. transitionType == null Feedback 무시
    @Test
    void transitionType_null_Feedback은_무시한다() {
        schedule("2026-08-10", ShiftType.EVENING);
        schedule("2026-08-11", ShiftType.DAY);
        feedback("2026-08-01", null, 5.0, false, null, 9, 4);

        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-11");
        assertThat(res.getAlerts()).isEmpty();
    }

    // 21. alerts date 오름차순
    @Test
    void alerts는_date_오름차순으로_반환된다() {
        schedule("2026-08-10", ShiftType.EVENING);
        schedule("2026-08-11", ShiftType.DAY);
        schedule("2026-08-12", ShiftType.EVENING);
        schedule("2026-08-13", ShiftType.DAY);
        feedback("2026-08-01", "EVENING_TO_DAY", 7.0, false, null, 9, 4);

        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-13");
        List<DashboardAlertResponse> alerts = res.getAlerts();
        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0).getDate()).isEqualTo("2026-08-10");
        assertThat(alerts.get(1).getDate()).isEqualTo("2026-08-12");
    }

    // 22. isRead 항상 false
    @Test
    void isRead는_항상_false() {
        schedule("2026-08-10", ShiftType.EVENING);
        schedule("2026-08-11", ShiftType.DAY);
        feedback("2026-08-01", "EVENING_TO_DAY", 7.0, false, null, 9, 4);

        DashboardResponse res = dashboardService.getDashboard("2026-08-10", "2026-08-11");
        assertThat(res.getAlerts().get(0).isRead()).isFalse();
    }

    // 23. 외부 응답에 명세 외 임의 필드 없음 (JSON 직렬화 키 검사, isRead 키 포함)
    @Test
    void 응답_JSON에_명세_외_필드가_없다() {
        schedule("2026-08-10", ShiftType.EVENING);
        schedule("2026-08-11", ShiftType.DAY);
        feedback("2026-08-01", "EVENING_TO_DAY", 7.0, false, null, 9, 4);

        DashboardResponse success = dashboardService.getDashboard("2026-08-10", "2026-08-11");
        JsonNode successNode = jsonMapper.valueToTree(success);
        assertThat(Set.copyOf(successNode.propertyNames())).isEqualTo(Set.of("success", "alerts"));

        JsonNode alertNode = successNode.get("alerts").get(0);
        assertThat(Set.copyOf(alertNode.propertyNames())).isEqualTo(Set.of(
                "date", "transitionType", "riskLevel", "title", "message", "recommendation", "isRead"));

        DashboardResponse failure = dashboardService.getDashboard(null, null);
        JsonNode failureNode = jsonMapper.valueToTree(failure);
        assertThat(Set.copyOf(failureNode.propertyNames())).isEqualTo(Set.of("success", "message"));
    }
}
