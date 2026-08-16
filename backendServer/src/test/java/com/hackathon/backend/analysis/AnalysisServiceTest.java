package com.hackathon.backend.analysis;

import com.hackathon.backend.analysis.dto.AnalysisResponse;
import com.hackathon.backend.analysis.dto.CurrentConditionResponse;
import com.hackathon.backend.dailystatus.DailyStatus;
import com.hackathon.backend.dailystatus.DailyStatusRepository;
import com.hackathon.backend.dailystatus.FatigueLevel;
import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.environment.EnvironmentRepository;
import com.hackathon.backend.schedule.Schedule;
import com.hackathon.backend.schedule.ScheduleRepository;
import com.hackathon.backend.schedule.ShiftType;
import com.hackathon.backend.wearable.WearableData;
import com.hackathon.backend.wearable.WearableService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

// AnalysisService(GET /api/analysis 조립) 통합 테스트.
// AnalysisService는 내부에서 LocalDateTime.now()를 직접 호출하므로(Clock 주입 구조 없음),
// 테스트는 절대 날짜/시각을 하드코딩하지 않고 LocalDate.now() 기준 상대 날짜만 사용한다.
//
// 근무환경(env())은 하루를 DAY 00:00~06:00 / EVENING 06:00~20:00 / NIGHT 20:00~23:00으로
// 정확히 나눠서 NIGHT가 자정을 넘기지 않게 만든다.
// 그래서 "어제 NIGHT"는 항상 어제 23:00에 끝나 있고(오늘 어느 시각에 테스트를 돌려도 이미 종료),
// referenceDate가 항상 "오늘"로 결정된다 — 실행 시각과 무관하게 안정적이다.
//
// availableHours/nextShiftMinutes처럼 실행 시각에 따라 절대값이 달라지는 필드는
// 하드코딩된 숫자와 비교하지 않는다. 대신 응답 자신의 nextShiftMinutes로부터
// (AvailableHoursCalculator와 NextShiftMinutesCalculator가 이번 시나리오에서는 같은 now,
//  같은 다음 실제 근무 시작시각을 기준으로 계산하므로) 기대 availableHours/riskLevel을
// "같은 계산기"로 역산해서 비교한다. 그래서 실행 시각이 몇 시든 flaky하지 않다.
@SpringBootTest
@Transactional
class AnalysisServiceTest {

    @Autowired
    AnalysisService analysisService;

    @Autowired
    EnvironmentRepository environmentRepository;

    @Autowired
    ScheduleRepository scheduleRepository;

    @Autowired
    DailyStatusRepository dailyStatusRepository;

    @Autowired
    WearableService wearableService;

    private final RiskLevelCalculator riskLevelCalculator = new RiskLevelCalculator();

    private final LocalDate today = LocalDate.now();
    private final LocalDate yesterday = today.minusDays(1);
    private final LocalDate tomorrow = today.plusDays(1);

    private Environment env() {
        return new Environment(
                LocalTime.of(0, 0), LocalTime.of(6, 0),   // DAY 00:00~06:00
                LocalTime.of(6, 0), LocalTime.of(20, 0),  // EVENING 06:00~20:00
                LocalTime.of(20, 0), LocalTime.of(23, 0), // NIGHT 20:00~23:00 (자정 안 넘김)
                30
        );
    }

    private void saveEnvironment() {
        environmentRepository.save(env());
    }

    private void saveSchedule(LocalDate date, ShiftType shift) {
        scheduleRepository.save(new Schedule(date, shift));
    }

    private void saveDailyStatus(FatigueLevel level, LocalDateTime createdAt) {
        dailyStatusRepository.save(new DailyStatus(level, createdAt));
    }

    // 성공 경로 공통 시나리오: 어제 NIGHT -> 오늘 OFF -> 내일 DAY.
    // transitionType은 (오늘 OFF, 내일 DAY) 단순 비교 = OFF_TO_DAY.
    // risk 내부 pattern은 (어제 NIGHT) -> [OFF 1일] -> (내일 DAY)로 서로 다른 개념임을 검증하는 데도 쓰인다.
    private void saveOffToDayScenario() {
        saveEnvironment();
        saveSchedule(yesterday, ShiftType.NIGHT);
        saveSchedule(today, ShiftType.OFF);
        saveSchedule(tomorrow, ShiftType.DAY);
    }

    // ================= 1. Environment 없음 =================
    @Test
    void Environment_없으면_실패응답() {
        AnalysisResponse res = analysisService.analyze();

        assertThat(res.getSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("근무 시간이 설정되지 않았습니다.");
        assertThat(res.getTransitionType()).isNull();
        assertThat(res.getRiskLevel()).isNull();
        assertThat(res.getCurrentCondition()).isNull();
    }

    // ================= 2. 다음 실제 근무 없음 =================
    @Test
    void 다음_실제_근무_없으면_실패응답() {
        saveEnvironment();
        saveSchedule(today, ShiftType.OFF);
        saveSchedule(tomorrow, ShiftType.OFF);
        // DAY/EVENING/NIGHT가 근무표 전체에 하나도 없음.

        AnalysisResponse res = analysisService.analyze();

        assertThat(res.getSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("다음 근무 일정이 없습니다.");
    }

    // ================= 3. OFF 뒤 실제 근무 탐색 =================
    @Test
    void OFF가_연속돼도_그_이후_실제_근무를_찾는다() {
        saveEnvironment();
        saveSchedule(today, ShiftType.OFF);
        saveSchedule(tomorrow, ShiftType.OFF);
        saveSchedule(tomorrow.plusDays(1), ShiftType.DAY); // 모레 DAY
        saveDailyStatus(FatigueLevel.LOW, LocalDateTime.now());

        AnalysisResponse res = analysisService.analyze();

        // OFF에서 실패하지 않고 모레 DAY를 다음 근무로 찾아 성공 응답이어야 한다.
        assertThat(res.getSuccess()).isNull();
        assertThat(res.getNextShiftMinutes()).isNotNull();
        assertThat(res.getNextShiftMinutes()).isPositive();
    }

    // ================= 4. DailyStatus 최신값 사용 =================
    @Test
    void DailyStatus는_createdAt_기준_최신_1건을_쓴다() {
        saveOffToDayScenario();
        LocalDateTime now = LocalDateTime.now();
        saveDailyStatus(FatigueLevel.MEDIUM, now.minusMinutes(10)); // 더 과거
        saveDailyStatus(FatigueLevel.HIGH, now);                   // 더 최신

        AnalysisResponse res = analysisService.analyze();

        assertThat(res.getCurrentCondition().getFatigueLevel()).isEqualTo("높음");
    }

    // ================= 5. WearableService 현재값 재사용 =================
    @Test
    void Wearable은_WearableService_getCurrent_값을_그대로_재사용한다() {
        saveOffToDayScenario();
        saveDailyStatus(FatigueLevel.LOW, LocalDateTime.now());

        WearableData wearable = wearableService.getCurrent();
        RecoveryStatus expectedStatus = new RecoveryStatusCalculator().calculate(
                FatigueLevel.LOW, wearable.sleepHours(), wearable.activityLevel(), wearable.heartRate());
        String expectedRecoveryKorean =
                CurrentConditionResponse.of(FatigueLevel.LOW, wearable.sleepHours(), expectedStatus).getRecoveryStatus();

        AnalysisResponse res = analysisService.analyze();

        // sleepHours가 GET /api/wearable-data와 동일한 값 그대로여야 한다(새 랜덤 생성 없음).
        assertThat(res.getCurrentCondition().getSleepHours()).isEqualTo(wearable.sleepHours());
        assertThat(res.getCurrentCondition().getRecoveryStatus()).isEqualTo(expectedRecoveryKorean);
    }

    // ================= 6/7/8. 성공 응답 필드 조립 + transitionType/pattern 분리 + availableHours 반올림 =================
    @Test
    void 성공_응답_전체_필드와_transitionType_pattern_분리_availableHours_반올림_검증() {
        saveOffToDayScenario();
        saveDailyStatus(FatigueLevel.LOW, LocalDateTime.now());

        AnalysisResponse res = analysisService.analyze();

        // --- 6. 성공 응답 기본 필드 조립 ---
        assertThat(res.getSuccess()).isNull();
        assertThat(res.getMessage()).isNull();
        assertThat(res.getTransitionType()).isNotNull();
        assertThat(res.getConsecutiveDays()).isNotNull();
        assertThat(res.getAvailableHours()).isNotNull();
        assertThat(res.getNextShiftMinutes()).isNotNull();
        assertThat(res.getRiskLevel()).isNotNull();
        assertThat(res.getCurrentCondition()).isNotNull();
        assertThat(res.getCurrentCondition().getFatigueLevel()).isEqualTo("낮음");

        // --- 7. transitionType(단순 다음날 비교) vs risk 내부 pattern(실제 근무 기준) 분리 ---
        // 오늘 OFF -> 내일 DAY 이므로 transitionType은 OFF_TO_DAY.
        assertThat(res.getTransitionType()).isEqualTo("OFF_TO_DAY");
        // consecutiveDays는 오늘이 OFF이므로 0.
        assertThat(res.getConsecutiveDays()).isEqualTo(0);

        // risk 내부 pattern은 (어제 NIGHT) -> [OFF 1일] -> (내일 DAY)로 transitionType과 다른 개념이다.
        // 날짜 기반이라 실행 시각과 무관하게 항상 동일하다.
        WorkTransitionPattern expectedPattern =
                new WorkTransitionPattern(ShiftType.NIGHT, yesterday, ShiftType.DAY, tomorrow, 1);

        // --- 8. availableHours 반올림: risk 계산에는 raw, 응답에는 소수 첫째 자리 반올림 ---
        // 이 시나리오에서는 AvailableHoursCalculator/NextShiftMinutesCalculator 둘 다
        // "지금 ~ 내일 DAY 시작"을 같은 now로 계산하므로,
        // rawAvailableHours = max(0, nextShiftMinutes - commuteMinutes) / 60.0 로 정확히 역산할 수 있다.
        long nextShiftMinutes = res.getNextShiftMinutes();
        double rawAvailableHours = Math.max(0, nextShiftMinutes - 30) / 60.0;
        double expectedRounded = Math.round(rawAvailableHours * 10) / 10.0;
        assertThat(res.getAvailableHours()).isEqualTo(expectedRounded);

        RiskLevel expectedRiskLevel = riskLevelCalculator.calculate(expectedPattern, 0, rawAvailableHours);
        assertThat(res.getRiskLevel()).isEqualTo(expectedRiskLevel.name());
    }
}
