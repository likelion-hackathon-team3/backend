package com.hackathon.backend.timeline.ai;

import com.hackathon.backend.analysis.RecoveryStatus;
import com.hackathon.backend.analysis.dto.AnalysisResponse;
import com.hackathon.backend.analysis.dto.CurrentConditionResponse;
import com.hackathon.backend.dailystatus.FatigueLevel;
import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.schedule.ShiftType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

// Spring -> AiServer POST /api/timeline/generate 요청 조립(AiTimelineRequestBuilder) 검증.
// 팀에서 확정한 계약(TODAY/FUTURE 분기, transitionType 직접 생성, OFF null 처리,
// NIGHT roster date, HH:mm currentTime, Analysis 한글->영문 토큰 매핑)을 그대로 확인한다.
class AiTimelineRequestBuilderTest {

    private final AiTimelineRequestBuilder builder = new AiTimelineRequestBuilder();
    private final LocalDate date = LocalDate.of(2026, 8, 17);

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
    void TODAY_DAY_TO_NIGHT_요청은_기본_필드가_모두_채워진다() {
        Environment env = standardEnv();
        LocalDateTime nextWorkActualStart = LocalDateTime.of(2026, 8, 18, 23, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 9, 5, 30);
        AnalysisResponse analysisResponse = AnalysisResponse.ok("DAY_TO_DAY", 1, 8.0, 60L, "NORMAL",
                CurrentConditionResponse.of(FatigueLevel.LOW, 7.0, RecoveryStatus.GOOD));

        AiTimelineRequest request = builder.build(date, ShiftType.DAY, ShiftType.NIGHT,
                nextWorkActualStart, env, true, now, analysisResponse);

        assertThat(request.targetDate()).isEqualTo("2026-08-17");
        assertThat(request.currentTime()).isEqualTo("09:05");
        assertThat(request.currentShift()).isEqualTo("DAY");
        assertThat(request.nextShift()).isEqualTo("NIGHT");
        assertThat(request.transitionType()).isEqualTo("DAY_TO_NIGHT");
        assertThat(request.currentWorkEnd()).isEqualTo("2026-08-17T15:00");
        assertThat(request.nextWorkStart()).isEqualTo("2026-08-18T23:00");
        assertThat(request.commuteMinutes()).isEqualTo(30);
        assertThat(request.userNotes()).isNull();
        assertThat(request.analysisResult()).isNotNull();

        AiTimelineRequest.ShiftTimes shiftTimes = request.shiftTimes();
        assertThat(shiftTimes.dayStart()).isEqualTo("07:00");
        assertThat(shiftTimes.dayEnd()).isEqualTo("15:00");
        assertThat(shiftTimes.eveningStart()).isEqualTo("15:00");
        assertThat(shiftTimes.eveningEnd()).isEqualTo("23:00");
        assertThat(shiftTimes.nightStart()).isEqualTo("23:00");
        assertThat(shiftTimes.nightEnd()).isEqualTo("07:00");
    }

    @Test
    void FUTURE_요청은_currentTime과_analysisResult가_강제로_null이다() {
        Environment env = standardEnv();
        LocalDateTime nextWorkActualStart = LocalDateTime.of(2026, 8, 18, 7, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);
        AnalysisResponse analysisResponse = AnalysisResponse.ok("DAY_TO_DAY", 1, 8.0, 60L, "NORMAL",
                CurrentConditionResponse.of(FatigueLevel.LOW, 7.0, RecoveryStatus.GOOD));

        // isToday=false인데 analysisResponse가 실수로 전달된 경우까지 방어하는지 확인한다.
        AiTimelineRequest request = builder.build(date, ShiftType.DAY, ShiftType.DAY,
                nextWorkActualStart, env, false, now, analysisResponse);

        assertThat(request.currentTime()).isNull();
        assertThat(request.analysisResult()).isNull();
    }

    @Test
    void currentShift가_OFF면_currentWorkEnd가_null이다() {
        Environment env = standardEnv();
        // nextShift(DAY)의 Schedule 날짜는 date+1(2026-08-18)이므로 nextWorkActualStart도 그 날짜여야 한다.
        LocalDateTime nextWorkActualStart = LocalDateTime.of(2026, 8, 18, 7, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);

        AiTimelineRequest request = builder.build(date, ShiftType.OFF, ShiftType.DAY,
                nextWorkActualStart, env, false, now, null);

        assertThat(request.currentWorkEnd()).isNull();
        assertThat(request.nextWorkStart()).isEqualTo("2026-08-18T07:00");
        assertThat(request.transitionType()).isEqualTo("OFF_TO_DAY");
    }

    @Test
    void nextShift가_OFF면_nextWorkStart가_null이다() {
        Environment env = standardEnv();
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);

        AiTimelineRequest request = builder.build(date, ShiftType.DAY, ShiftType.OFF,
                null, env, false, now, null);

        assertThat(request.nextWorkStart()).isNull();
        assertThat(request.currentWorkEnd()).isEqualTo("2026-08-17T15:00");
        assertThat(request.transitionType()).isEqualTo("DAY_TO_OFF");
    }

    @Test
    void NIGHT_currentWorkEnd는_ShiftDateTimeResolver의_roster_date_규칙을_그대로_따른다() {
        // nightStart(01:30) < eveningStart(15:00) -> 실제 시작은 scheduleDate+1일.
        Environment env = new Environment(
                LocalTime.of(7, 0), LocalTime.of(15, 0),
                LocalTime.of(15, 0), LocalTime.of(23, 0),
                LocalTime.of(1, 30), LocalTime.of(9, 30),
                30
        );
        LocalDateTime nextWorkActualStart = LocalDateTime.of(2026, 8, 18, 15, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);

        AiTimelineRequest request = builder.build(date, ShiftType.NIGHT, ShiftType.EVENING,
                nextWorkActualStart, env, false, now, null);

        assertThat(request.currentWorkEnd()).isEqualTo("2026-08-18T09:30");
        assertThat(request.transitionType()).isEqualTo("NIGHT_TO_EVENING");
    }

    @Test
    void currentTime은_HHmm_형식으로_초없이_포맷된다() {
        Environment env = standardEnv();
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 0, 15, 45);

        AiTimelineRequest request = builder.build(date, ShiftType.DAY, ShiftType.NIGHT,
                LocalDateTime.of(2026, 8, 18, 23, 0), env, true, now, null);

        assertThat(request.currentTime()).isEqualTo("00:15");
    }

    @ParameterizedTest
    @EnumSource(FatigueLevel.class)
    void fatigueLevel은_한글에서_영문_토큰으로_역매핑된다(FatigueLevel level) {
        Environment env = standardEnv();
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);
        AnalysisResponse analysisResponse = AnalysisResponse.ok("DAY_TO_DAY", 1, 8.0, 60L, "NORMAL",
                CurrentConditionResponse.of(level, 7.0, RecoveryStatus.GOOD));

        AiTimelineRequest request = builder.build(date, ShiftType.DAY, ShiftType.DAY,
                LocalDateTime.of(2026, 8, 18, 7, 0), env, true, now, analysisResponse);

        assertThat(request.analysisResult().fatigueLevel()).isEqualTo(level.name());
    }

    @ParameterizedTest
    @EnumSource(RecoveryStatus.class)
    void recoveryStatus는_한글에서_영문_토큰으로_역매핑된다(RecoveryStatus status) {
        Environment env = standardEnv();
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);
        AnalysisResponse analysisResponse = AnalysisResponse.ok("DAY_TO_DAY", 1, 8.0, 60L, "NORMAL",
                CurrentConditionResponse.of(FatigueLevel.LOW, 7.0, status));

        AiTimelineRequest request = builder.build(date, ShiftType.DAY, ShiftType.DAY,
                LocalDateTime.of(2026, 8, 18, 7, 0), env, true, now, analysisResponse);

        assertThat(request.analysisResult().recoveryStatus()).isEqualTo(status.name());
    }

    @Test
    void analysisResult의_riskLevel_availableHours_consecutiveDays는_그대로_전달된다() {
        Environment env = standardEnv();
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);
        AnalysisResponse analysisResponse = AnalysisResponse.ok("DAY_TO_DAY", 3, 6.25, 120L, "DANGER",
                CurrentConditionResponse.of(FatigueLevel.HIGH, 4.0, RecoveryStatus.RECOVERY_PRIORITY));

        AiTimelineRequest request = builder.build(date, ShiftType.DAY, ShiftType.DAY,
                LocalDateTime.of(2026, 8, 18, 7, 0), env, true, now, analysisResponse);

        AiTimelineRequest.AnalysisResult result = request.analysisResult();
        assertThat(result.riskLevel()).isEqualTo("DANGER");
        assertThat(result.availableHours()).isEqualTo(6.25);
        assertThat(result.consecutiveDays()).isEqualTo(3);
    }

    @Test
    void isToday가_true여도_analysisResponse가_null이면_analysisResult는_null이다() {
        Environment env = standardEnv();
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);

        AiTimelineRequest request = builder.build(date, ShiftType.DAY, ShiftType.DAY,
                LocalDateTime.of(2026, 8, 18, 7, 0), env, true, now, null);

        assertThat(request.analysisResult()).isNull();
    }
}
