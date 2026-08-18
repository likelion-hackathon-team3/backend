package com.hackathon.backend.timeline;

import com.hackathon.backend.analysis.AnalysisService;
import com.hackathon.backend.analysis.RecoveryStatus;
import com.hackathon.backend.analysis.dto.AnalysisResponse;
import com.hackathon.backend.analysis.dto.CurrentConditionResponse;
import com.hackathon.backend.dailystatus.FatigueLevel;
import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.environment.EnvironmentRepository;
import com.hackathon.backend.personalization.PersonalizationService;
import com.hackathon.backend.personalization.dto.PersonalizationResponse;
import com.hackathon.backend.schedule.Schedule;
import com.hackathon.backend.schedule.ScheduleRepository;
import com.hackathon.backend.schedule.ShiftType;
import com.hackathon.backend.timeline.ai.AiTimelineClient;
import com.hackathon.backend.timeline.ai.AiTimelineRequest;
import com.hackathon.backend.timeline.ai.AiTimelineResponse;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// TimelineService 검증. Repository/AnalysisService/AiTimelineClient는 Mockito로 대체하고
// 실제 DB나 AiServer 네트워크 호출은 쓰지 않는다.
// PAST/TODAY(Analysis 확보 실패 포함)에서는 AiServer를 호출하지 않고 바로 fallback으로 내려간다.
// AiTimelineClient는 인터페이스가 아니라 Optional을 반환하는 클래스라, 별도 스텁이 없으면
// Mockito 기본 동작으로 Optional.empty()를 반환하므로 fallback 경로로 자연스럽게 이어진다.
@ExtendWith(MockitoExtension.class)
class TimelineServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private AnalysisService analysisService;

    @Mock
    private AiTimelineClient aiTimelineClient;

    @Mock
    private PersonalizationService personalizationService;

    private TimelineService service;

    private final LocalDate futureDate = LocalDate.now().plusYears(1);

    @BeforeEach
    void setUp() {
        service = new TimelineService(scheduleRepository, environmentRepository, analysisService, aiTimelineClient,
                personalizationService);
        // AI 요청 조립 경로(=AiTimelineClient.generate 직전)에 도달하는 테스트는 전부 이 호출을 거친다.
        // 개별 값 검증이 목적이 아닌 테스트에서는 기본값(피드백 없음)이면 충분해서 lenient로 공통 스텁한다.
        // 날짜 필터링 자체(Before 경계 등)는 PersonalizationServiceTest 책임이라 여기서는 검증하지 않는다.
        lenient().when(personalizationService.getPersonalization(any(), any()))
                .thenReturn(PersonalizationResponse.noAccumulatedFeedback());
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

    private AnalysisResponse successfulAnalysis() {
        return AnalysisResponse.ok("DAY_TO_DAY", 1, 8.0, 60L, "NORMAL",
                CurrentConditionResponse.of(FatigueLevel.LOW, 7.0, RecoveryStatus.GOOD));
    }

    @Test
    void date가_null이면_오늘_날짜로_처리된다() {
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        when(scheduleRepository.findByDate(any()))
                .thenAnswer(inv -> Optional.of(new Schedule(inv.getArgument(0), ShiftType.DAY)));
        // 이 테스트의 목적은 date=null이 오늘 날짜로 해석되는지 확인하는 것이라 Analysis는
        // 정상 응답으로 두고, AiTimelineClient만 비워서 fallback으로 내려가게 한다.
        when(analysisService.analyze()).thenReturn(successfulAnalysis());
        when(aiTimelineClient.generate(any())).thenReturn(Optional.empty());

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
        List<TimelineItemDraft> expectedDrafts = itemPlacer.place(ShiftType.DAY, ShiftType.DAY, range, budget, 0);

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

    @Test
    void AI_응답이_유효하면_isFallback_false로_AI_데이터를_반환한다() {
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        when(scheduleRepository.findByDate(futureDate))
                .thenReturn(Optional.of(new Schedule(futureDate, ShiftType.DAY)));
        when(scheduleRepository.findByDate(futureDate.plusDays(1)))
                .thenReturn(Optional.of(new Schedule(futureDate.plusDays(1), ShiftType.DAY)));

        AiTimelineResponse.Item aiItem = new AiTimelineResponse.Item(
                "09:00", "AI 제목", "AI 설명", "MEAL", null);
        AiTimelineResponse aiResponse = new AiTimelineResponse(
                futureDate.toString(), "FUTURE", "AI 페이지 제목", "AI 부제목",
                List.of(aiItem), List.of("AI 추천"));
        when(aiTimelineClient.generate(any())).thenReturn(Optional.of(aiResponse));

        TimelineResponse response = service.getTimeline(futureDate.toString());

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getIsFallback()).isFalse();
        assertThat(response.getData().getPageTitle()).isEqualTo("AI 페이지 제목");
        assertThat(response.getData().getPageSubtitle()).isEqualTo("AI 부제목");
        assertThat(response.getData().getTimelineItems()).hasSize(1);
        assertThat(response.getData().getTimelineItems().get(0).getTitle()).isEqualTo("AI 제목");
        assertThat(response.getData().getRecommendations()).containsExactly("AI 추천");
    }

    @Test
    void TODAY_Analysis_성공이면_AI_요청에_analysisResult가_포함된다() {
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        when(scheduleRepository.findByDate(any()))
                .thenAnswer(inv -> Optional.of(new Schedule(inv.getArgument(0), ShiftType.DAY)));
        when(analysisService.analyze()).thenReturn(
                AnalysisResponse.ok("DAY_TO_DAY", 2, 7.5, 90L, "CAUTION",
                        CurrentConditionResponse.of(FatigueLevel.HIGH, 5.0, RecoveryStatus.RECOVERY_NEEDED)));
        when(aiTimelineClient.generate(any())).thenReturn(Optional.empty());

        service.getTimeline(null);

        ArgumentCaptor<AiTimelineRequest> captor = ArgumentCaptor.forClass(AiTimelineRequest.class);
        verify(aiTimelineClient).generate(captor.capture());

        AiTimelineRequest request = captor.getValue();
        assertThat(request.currentTime()).isNotNull();
        AiTimelineRequest.AnalysisResult analysisResult = request.analysisResult();
        assertThat(analysisResult).isNotNull();
        assertThat(analysisResult.riskLevel()).isEqualTo("CAUTION");
        assertThat(analysisResult.fatigueLevel()).isEqualTo("HIGH");
        assertThat(analysisResult.recoveryStatus()).isEqualTo("RECOVERY_NEEDED");
        assertThat(analysisResult.availableHours()).isEqualTo(7.5);
        assertThat(analysisResult.consecutiveDays()).isEqualTo(2);
    }

    @Test
    void FUTURE_요청은_analysisResult가_null이고_transitionType은_currentShift_nextShift로_결정된다() {
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        when(scheduleRepository.findByDate(futureDate))
                .thenReturn(Optional.of(new Schedule(futureDate, ShiftType.DAY)));
        when(scheduleRepository.findByDate(futureDate.plusDays(1)))
                .thenReturn(Optional.of(new Schedule(futureDate.plusDays(1), ShiftType.NIGHT)));
        when(aiTimelineClient.generate(any())).thenReturn(Optional.empty());

        service.getTimeline(futureDate.toString());

        ArgumentCaptor<AiTimelineRequest> captor = ArgumentCaptor.forClass(AiTimelineRequest.class);
        verify(aiTimelineClient).generate(captor.capture());

        AiTimelineRequest request = captor.getValue();
        assertThat(request.analysisResult()).isNull();
        assertThat(request.currentTime()).isNull();
        assertThat(request.transitionType()).isEqualTo("DAY_TO_NIGHT");
        verifyNoInteractions(analysisService);
    }

    @Test
    void Personalization_값이_동일한_transitionType으로_조회되어_AI_요청에_그대로_담긴다() {
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        when(scheduleRepository.findByDate(futureDate))
                .thenReturn(Optional.of(new Schedule(futureDate, ShiftType.NIGHT)));
        when(scheduleRepository.findByDate(futureDate.plusDays(1)))
                .thenReturn(Optional.of(new Schedule(futureDate.plusDays(1), ShiftType.OFF)));
        when(personalizationService.getPersonalization("NIGHT_TO_OFF", futureDate))
                .thenReturn(new PersonalizationResponse("14:30", 30, true, "안내 문구"));
        when(aiTimelineClient.generate(any())).thenReturn(Optional.empty());

        service.getTimeline(futureDate.toString());

        // 실제 Feedback 날짜 필터링(Before 경계 등)은 PersonalizationServiceTest 책임이라 여기서는
        // TimelineService가 정확한 transitionType/targetDate를 그대로 넘기는지만 검증한다.
        ArgumentCaptor<String> transitionTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDate> targetDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(personalizationService).getPersonalization(transitionTypeCaptor.capture(), targetDateCaptor.capture());
        assertThat(transitionTypeCaptor.getValue()).isEqualTo("NIGHT_TO_OFF");
        assertThat(targetDateCaptor.getValue()).isEqualTo(futureDate);

        ArgumentCaptor<AiTimelineRequest> requestCaptor = ArgumentCaptor.forClass(AiTimelineRequest.class);
        verify(aiTimelineClient).generate(requestCaptor.capture());

        AiTimelineRequest.PersonalizationRequest personalization = requestCaptor.getValue().personalization();
        assertThat(personalization).isNotNull();
        assertThat(personalization.recommendedSleepBuffer()).isEqualTo(30);
        assertThat(personalization.adjustedCaffeineCutoff()).isEqualTo("14:30");
    }

    // ===== AI 실패 -> deterministic fallback 경로에 Personalization이 실제로 반영되는지 =====
    // (AI 성공 경로는 위 테스트에서 이미 확인함. 여기서는 aiTimelineClient.generate가
    //  Optional.empty()를 반환해 runFallback으로 내려가는 경우만 다룬다.)

    @Test
    void personalization_기본값이면_fallback도_기존과_완전히_동일하다() {
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        when(scheduleRepository.findByDate(futureDate))
                .thenReturn(Optional.of(new Schedule(futureDate, ShiftType.DAY)));
        when(scheduleRepository.findByDate(futureDate.plusDays(1)))
                .thenReturn(Optional.of(new Schedule(futureDate.plusDays(1), ShiftType.EVENING)));
        // setUp()의 lenient 기본 스텁이 이미 noAccumulatedFeedback()(buffer=0, cutoff=null)을 반환한다.
        when(aiTimelineClient.generate(any())).thenReturn(Optional.empty());

        TimelineResponse response = service.getTimeline(futureDate.toString());

        TimelineItemResponse sleep = itemByCategory(response, "SLEEP");
        assertThat(sleep.getHighlight()).isEqualTo("수면 목표 9시간 0분"); // 540분, buffer 미반영 상태와 동일
        assertThat(response.getData().getRecommendations())
                .noneMatch(text -> text.contains("카페인 섭취를 중단"));
    }

    @Test
    void AI_실패시_fallback_실제_수면시간에_recommendedSleepBuffer가_반영된다() {
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        when(scheduleRepository.findByDate(futureDate))
                .thenReturn(Optional.of(new Schedule(futureDate, ShiftType.DAY)));
        when(scheduleRepository.findByDate(futureDate.plusDays(1)))
                .thenReturn(Optional.of(new Schedule(futureDate.plusDays(1), ShiftType.EVENING)));
        when(personalizationService.getPersonalization("DAY_TO_EVENING", futureDate))
                .thenReturn(new PersonalizationResponse(null, 30, false, "안내 문구"));
        when(aiTimelineClient.generate(any())).thenReturn(Optional.empty());

        TimelineResponse response = service.getTimeline(futureDate.toString());

        assertThat(response.getIsFallback()).isTrue();
        TimelineItemResponse sleep = itemByCategory(response, "SLEEP");
        // DAY_TO_EVENING은 여유시간이 충분해 기존 9시간 상한이 그대로 30분 늘어난다(TimelineItemPlacerTest에서
        // 동일 전환으로 570분임을 이미 상세 검증함 — 여기서는 실제 API 응답에 반영되는지만 확인).
        assertThat(sleep.getHighlight()).isEqualTo("수면 목표 9시간 30분");
    }

    @Test
    void AI_실패시_fallback_recommendation에_카페인_cutoff가_반영된다() {
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        when(scheduleRepository.findByDate(futureDate))
                .thenReturn(Optional.of(new Schedule(futureDate, ShiftType.DAY)));
        when(scheduleRepository.findByDate(futureDate.plusDays(1)))
                .thenReturn(Optional.of(new Schedule(futureDate.plusDays(1), ShiftType.EVENING)));
        when(personalizationService.getPersonalization("DAY_TO_EVENING", futureDate))
                .thenReturn(new PersonalizationResponse("15:30", 0, false, "안내 문구"));
        when(aiTimelineClient.generate(any())).thenReturn(Optional.empty());

        TimelineResponse response = service.getTimeline(futureDate.toString());

        assertThat(response.getData().getRecommendations())
                .contains("15:30 이후에는 카페인 섭취를 중단해주세요.");
        // 카페인 cutoff는 문구에만 반영되고, 수면 시간(버퍼=0)은 그대로여야 한다.
        TimelineItemResponse sleep = itemByCategory(response, "SLEEP");
        assertThat(sleep.getHighlight()).isEqualTo("수면 목표 9시간 0분");
    }

    @Test
    void AI_실패시_buffer와_cutoff가_동시에_있으면_둘_다_반영된다() {
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        when(scheduleRepository.findByDate(futureDate))
                .thenReturn(Optional.of(new Schedule(futureDate, ShiftType.DAY)));
        when(scheduleRepository.findByDate(futureDate.plusDays(1)))
                .thenReturn(Optional.of(new Schedule(futureDate.plusDays(1), ShiftType.EVENING)));
        when(personalizationService.getPersonalization("DAY_TO_EVENING", futureDate))
                .thenReturn(new PersonalizationResponse("15:30", 30, true, "안내 문구"));
        when(aiTimelineClient.generate(any())).thenReturn(Optional.empty());

        TimelineResponse response = service.getTimeline(futureDate.toString());

        TimelineItemResponse sleep = itemByCategory(response, "SLEEP");
        assertThat(sleep.getHighlight()).isEqualTo("수면 목표 9시간 30분");
        assertThat(response.getData().getRecommendations())
                .contains("15:30 이후에는 카페인 섭취를 중단해주세요.");
    }

    private TimelineItemResponse itemByCategory(TimelineResponse response, String category) {
        return response.getData().getTimelineItems().stream()
                .filter(item -> category.equals(item.getCategory()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("category=" + category + " 항목을 찾지 못함"));
    }

    @Test
    void TODAY_Analysis_success_false이면_AI를_호출하지_않고_fallback을_사용한다() {
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        when(scheduleRepository.findByDate(any()))
                .thenAnswer(inv -> Optional.of(new Schedule(inv.getArgument(0), ShiftType.DAY)));
        when(analysisService.analyze()).thenReturn(AnalysisResponse.fail("근무 시간이 설정되지 않았습니다."));

        TimelineResponse response = service.getTimeline(null);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getIsFallback()).isTrue();
        verifyNoInteractions(aiTimelineClient);
    }

    @Test
    void TODAY_Analysis_예외시_AI를_호출하지_않고_fallback을_사용한다() {
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        when(scheduleRepository.findByDate(any()))
                .thenAnswer(inv -> Optional.of(new Schedule(inv.getArgument(0), ShiftType.DAY)));
        when(analysisService.analyze()).thenThrow(new RuntimeException("DB 오류"));

        TimelineResponse response = service.getTimeline(null);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getIsFallback()).isTrue();
        verifyNoInteractions(aiTimelineClient);
    }

    @Test
    void PAST_요청은_AiServer를_호출하지_않고_fallback을_사용한다() {
        LocalDate pastDate = LocalDate.of(2020, 1, 1);
        when(scheduleRepository.count()).thenReturn(1L);
        when(environmentRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(standardEnv()));
        when(scheduleRepository.findByDate(pastDate))
                .thenReturn(Optional.of(new Schedule(pastDate, ShiftType.DAY)));
        when(scheduleRepository.findByDate(pastDate.plusDays(1)))
                .thenReturn(Optional.of(new Schedule(pastDate.plusDays(1), ShiftType.DAY)));

        TimelineResponse response = service.getTimeline(pastDate.toString());

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getIsFallback()).isTrue();
        verifyNoInteractions(aiTimelineClient, analysisService, personalizationService);
    }
}
