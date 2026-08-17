package com.hackathon.backend.timeline;

import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.environment.EnvironmentRepository;
import com.hackathon.backend.schedule.Schedule;
import com.hackathon.backend.schedule.ScheduleRepository;
import com.hackathon.backend.schedule.ShiftType;
import com.hackathon.backend.timeline.dto.TimelineData;
import com.hackathon.backend.timeline.dto.TimelineItemResponse;
import com.hackathon.backend.timeline.dto.TimelineResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// TimelineService 검증. Repository는 Mockito로 대체하고 실제 DB는 쓰지 않는다.
// 이번 단계는 AiServer 연동 전이므로 성공 응답은 항상 isFallback=true다.
@ExtendWith(MockitoExtension.class)
class TimelineServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    private TimelineService service;

    private final LocalDate futureDate = LocalDate.now().plusYears(1);

    @BeforeEach
    void setUp() {
        service = new TimelineService(scheduleRepository, environmentRepository);
    }

    // DAY 07:00~15:00 / EVENING 15:00~23:00 / NIGHT 23:00~07:00, commute 30분.
    private Environment standardEnv() {
        return new Environment(
                LocalTime.of(7, 0), LocalTime.of(15, 0),
                LocalTime.of(15, 0), LocalTime.of(23, 0),
                LocalTime.of(23, 0), LocalTime.of(7, 0),
                30
        );
    }

    @Test
    void date가_null이면_오늘_날짜로_처리된다() {
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        when(scheduleRepository.findByDate(any()))
                .thenAnswer(inv -> Optional.of(new Schedule(inv.getArgument(0), ShiftType.DAY)));

        // 자정 경계에서 실행 전/후 LocalDate.now()가 달라질 수 있으므로,
        // Service 내부 날짜를 직접 재호출로 비교하지 않고 실행 전/후 범위 안에 있는지로 검증한다.
        LocalDate before = LocalDate.now();
        TimelineResponse response = service.getTimeline(null);
        LocalDate after = LocalDate.now();

        assertThat(response.getSuccess()).isTrue();

        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
        verify(scheduleRepository, atLeastOnce()).findByDate(captor.capture());

        LocalDate firstQueriedDate = captor.getAllValues().get(0);
        assertThat(firstQueriedDate).isBetween(before, after);

        LocalDate secondQueriedDate = captor.getAllValues().get(1);
        assertThat(secondQueriedDate).isEqualTo(firstQueriedDate.plusDays(1));
    }

    @Test
    void 날짜_형식이_올바르지_않으면_실패_메시지를_반환한다() {
        TimelineResponse response = service.getTimeline("2026/08/17");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("날짜 형식이 올바르지 않습니다.");
    }

    @Test
    void 등록된_근무표가_없으면_실패_메시지를_반환한다() {
        when(scheduleRepository.count()).thenReturn(0L);

        TimelineResponse response = service.getTimeline("2026-08-17");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("등록된 근무표가 없습니다.");
    }

    @Test
    void Environment가_없으면_실패_메시지를_반환한다() {
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.empty());

        TimelineResponse response = service.getTimeline("2026-08-17");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("타임라인 정보를 불러오지 못했습니다.");
        assertThat(response.getMessage()).isNotEqualTo("등록된 근무표가 없습니다.");
    }

    @Test
    void 선택한_날짜의_근무가_없으면_실패_메시지를_반환한다() {
        LocalDate date = LocalDate.of(2026, 8, 17);
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        when(scheduleRepository.findByDate(date)).thenReturn(Optional.empty());
        when(scheduleRepository.findByDate(date.plusDays(1)))
                .thenReturn(Optional.of(new Schedule(date.plusDays(1), ShiftType.DAY)));

        TimelineResponse response = service.getTimeline("2026-08-17");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("해당 날짜에 등록된 근무가 없습니다.");
    }

    @Test
    void 다음날_근무가_없으면_실패_메시지를_반환한다() {
        LocalDate date = LocalDate.of(2026, 8, 17);
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        when(scheduleRepository.findByDate(date))
                .thenReturn(Optional.of(new Schedule(date, ShiftType.DAY)));
        when(scheduleRepository.findByDate(date.plusDays(1))).thenReturn(Optional.empty());

        TimelineResponse response = service.getTimeline("2026-08-17");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("해당 날짜에 등록된 근무가 없습니다.");
    }

    @Test
    void 정상_DAY_TO_DAY_요청은_성공_응답_모양을_따른다() {
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        when(scheduleRepository.findByDate(futureDate))
                .thenReturn(Optional.of(new Schedule(futureDate, ShiftType.DAY)));
        when(scheduleRepository.findByDate(futureDate.plusDays(1)))
                .thenReturn(Optional.of(new Schedule(futureDate.plusDays(1), ShiftType.DAY)));

        TimelineResponse response = service.getTimeline(futureDate.toString());

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getIsFallback()).isTrue();
        assertThat(response.getMessage()).isNull();

        TimelineData data = response.getData();
        assertThat(data).isNotNull();
        assertThat(data.getPageTitle()).isNotBlank();
        assertThat(data.getPageSubtitle()).isNotBlank();
        assertThat(data.getTimelineItems()).isNotEmpty();
        assertThat(data.getRecommendations()).isNotEmpty();
    }

    @Test
    void 명시적_OFF_근무는_없는_것으로_처리되지_않는다() {
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        when(scheduleRepository.findByDate(futureDate))
                .thenReturn(Optional.of(new Schedule(futureDate, ShiftType.OFF)));
        when(scheduleRepository.findByDate(futureDate.plusDays(1)))
                .thenReturn(Optional.of(new Schedule(futureDate.plusDays(1), ShiftType.OFF)));

        TimelineResponse response = service.getTimeline(futureDate.toString());

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).isNull();
    }

    @Test
    void 서로_다른_Environment는_서로_다른_실제_시간을_만든다() {
        when(scheduleRepository.count()).thenReturn(1L);
        when(scheduleRepository.findByDate(futureDate))
                .thenReturn(Optional.of(new Schedule(futureDate, ShiftType.DAY)));
        when(scheduleRepository.findByDate(futureDate.plusDays(1)))
                .thenReturn(Optional.of(new Schedule(futureDate.plusDays(1), ShiftType.DAY)));

        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        TimelineResponse first = service.getTimeline(futureDate.toString());

        Environment shortCommute = new Environment(
                LocalTime.of(9, 0), LocalTime.of(18, 0),
                LocalTime.of(18, 0), LocalTime.of(2, 0),
                LocalTime.of(2, 0), LocalTime.of(9, 0),
                10
        );
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(shortCommute));
        TimelineResponse second = service.getTimeline(futureDate.toString());

        String firstTime = first.getData().getTimelineItems().get(0).getTime();
        String secondTime = second.getData().getTimelineItems().get(0).getTime();
        assertThat(firstTime).isNotEqualTo(secondTime);
    }

    @Test
    void 미래_날짜는_TodayFilter의_영향을_받지_않고_전체_Timeline을_돌려준다() {
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        when(scheduleRepository.findByDate(futureDate))
                .thenReturn(Optional.of(new Schedule(futureDate, ShiftType.DAY)));
        when(scheduleRepository.findByDate(futureDate.plusDays(1)))
                .thenReturn(Optional.of(new Schedule(futureDate.plusDays(1), ShiftType.DAY)));

        TimelineResponse response = service.getTimeline(futureDate.toString());

        TimelineRangeCalculator rangeCalculator = new TimelineRangeCalculator();
        TimelineBudgetClassifier budgetClassifier = new TimelineBudgetClassifier();
        TimelineItemPlacer itemPlacer = new TimelineItemPlacer();

        TimelineRange range = rangeCalculator.calculate(futureDate, ShiftType.DAY, ShiftType.DAY, standardEnv());
        TimelineBudgetLevel budget = budgetClassifier.classify(range.timelineStart(), range.timelineEnd());
        List<TimelineItemDraft> expectedDrafts = itemPlacer.place(ShiftType.DAY, ShiftType.DAY, range, budget);

        List<TimelineItemResponse> actualItems = response.getData().getTimelineItems();
        assertThat(actualItems).hasSize(expectedDrafts.size());
    }

    @Test
    void 예상치_못한_RuntimeException은_일반_실패_메시지로_반환된다() {
        when(scheduleRepository.count()).thenThrow(new RuntimeException("DB down"));

        TimelineResponse response = service.getTimeline("2026-08-17");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("타임라인 정보를 불러오지 못했습니다.");
    }
}
