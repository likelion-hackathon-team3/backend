package com.hackathon.backend.timeline;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// TimelineTodayFilter 검증.
// TimelineItemPlacer는 now()를 전혀 모르는 순수 생성기이고, "오늘"의 현재시각 필터링은
// 이 클래스가 전담한다. 규칙: date != now.toLocalDate() 이면 아무것도 걸러내지 않는다.
class TimelineTodayFilterTest {

    private final TimelineTodayFilter filter = new TimelineTodayFilter();
    private final LocalDate date = LocalDate.of(2026, 8, 17);

    @Test
    void 오늘_20시_조회는_완전히_끝난_항목을_제거하고_시작전_항목은_유지한다() {
        LocalDateTime now = date.atTime(20, 0);

        TimelineItemDraft pastMeal = new TimelineItemDraft(
                date.atTime(15, 30), date.atTime(16, 0), ActivityKind.POST_WORK_MEAL);
        TimelineItemDraft pastRest = new TimelineItemDraft(
                date.atTime(16, 0), date.atTime(16, 30), ActivityKind.POST_WORK_REST);
        TimelineItemDraft futureSleepPrep = new TimelineItemDraft(
                date.atTime(22, 30), date.atTime(22, 50), ActivityKind.SLEEP_PREP);

        List<TimelineItemDraft> result = filter.apply(
                List.of(pastMeal, pastRest, futureSleepPrep), date, now);

        assertThat(result).containsExactly(futureSleepPrep);
    }

    @Test
    void 종료시각이_현재시각과_같으면_제거된다() {
        LocalDateTime now = date.atTime(16, 0);

        TimelineItemDraft item = new TimelineItemDraft(
                date.atTime(15, 30), date.atTime(16, 0), ActivityKind.POST_WORK_MEAL);

        List<TimelineItemDraft> result = filter.apply(List.of(item), date, now);

        assertThat(result).isEmpty();
    }

    @Test
    void 시작시각이_현재시각과_같으면_유지된다() {
        LocalDateTime now = date.atTime(22, 30);

        TimelineItemDraft item = new TimelineItemDraft(
                date.atTime(22, 30), date.atTime(22, 50), ActivityKind.SLEEP_PREP);

        List<TimelineItemDraft> result = filter.apply(List.of(item), date, now);

        assertThat(result).containsExactly(item);
    }

    @Test
    void 자정을_넘겨_진행중인_항목은_원래_시작시각을_유지한_채_남는다() {
        // date==now.toLocalDate() (오늘, 23:30) 이고, sleep.start < now < sleep.end 이므로
        // TODAY 진행중-유지 로직을 실제로 태운다.
        LocalDateTime now = date.atTime(23, 30);

        TimelineItemDraft sleep = new TimelineItemDraft(
                date.atTime(22, 50), date.plusDays(1).atTime(7, 50), ActivityKind.NORMAL_SLEEP);

        List<TimelineItemDraft> result = filter.apply(List.of(sleep), date, now);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).start()).isEqualTo(date.atTime(22, 50));
        assertThat(result.get(0).end()).isEqualTo(date.plusDays(1).atTime(7, 50));
    }

    @Test
    void 향후_WORK_마커는_시각과_무관하게_유지된다() {
        LocalDateTime now = date.atTime(20, 0);

        TimelineItemDraft futureMarker = new TimelineItemDraft(
                date.plusDays(1).atTime(7, 0), date.plusDays(1).atTime(7, 0), ActivityKind.WORK_START);

        List<TimelineItemDraft> result = filter.apply(List.of(futureMarker), date, now);

        assertThat(result).containsExactly(futureMarker);
    }

    @Test
    void 미래_날짜_조회는_아무것도_걸러내지_않는다() {
        LocalDate futureDate = date.plusDays(3);
        LocalDateTime now = date.atTime(20, 0);

        TimelineItemDraft item1 = new TimelineItemDraft(
                futureDate.atTime(7, 0), futureDate.atTime(7, 30), ActivityKind.BREAKFAST);
        TimelineItemDraft item2 = new TimelineItemDraft(
                futureDate.atTime(23, 0), futureDate.plusDays(1).atTime(7, 0), ActivityKind.NORMAL_SLEEP);

        List<TimelineItemDraft> result = filter.apply(List.of(item1, item2), futureDate, now);

        assertThat(result).containsExactly(item1, item2);
    }

    @Test
    void 과거_날짜_조회도_필터_자체는_아무것도_걸러내지_않는다() {
        // filter의 규칙은 "date==오늘"일 때만 걸러내는 것이므로, 과거 날짜 조회는
        // 그대로 반환된다(AiServer 단계의 PAST 정책은 별도 문제이며 여기서 다루지 않는다).
        LocalDate pastDate = date.minusDays(3);
        LocalDateTime now = date.atTime(20, 0);

        TimelineItemDraft item = new TimelineItemDraft(
                pastDate.atTime(7, 0), pastDate.atTime(7, 30), ActivityKind.BREAKFAST);

        List<TimelineItemDraft> result = filter.apply(List.of(item), pastDate, now);

        assertThat(result).containsExactly(item);
    }
}
