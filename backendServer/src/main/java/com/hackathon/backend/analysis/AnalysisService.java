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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

// GET /api/analysis 실제 로직을 담는 계층.
// 새 계산 규칙은 만들지 않고, 이미 구현된 analysis 패키지의 각 Calculator를 순서대로 조립만 한다.
// (ReferenceDateResolver / TransitionCalculator / ConsecutiveDaysCalculator /
//  NextShiftMinutesCalculator / AvailableHoursCalculator / ShiftPatternCalculator /
//  RiskLevelCalculator / RecoveryStatusCalculator는 전부 수정하지 않는다.)
//
// 이 프로젝트에는 GlobalExceptionHandler/커스텀 예외 구조가 없으므로,
// 다른 Service들과 같은 방식으로 실패 상황도 예외를 던지지 않고
// AnalysisResponse(실패 DTO)를 직접 만들어 반환한다.
@Service
public class AnalysisService {

    private final EnvironmentRepository environmentRepository;
    private final ScheduleRepository scheduleRepository;
    private final DailyStatusRepository dailyStatusRepository;
    private final WearableService wearableService;

    private final ReferenceDateResolver referenceDateResolver = new ReferenceDateResolver();
    private final DisplayTransitionCalculator displayTransitionCalculator = new DisplayTransitionCalculator();
    private final ConsecutiveDaysCalculator consecutiveDaysCalculator = new ConsecutiveDaysCalculator();
    private final NextShiftMinutesCalculator nextShiftMinutesCalculator = new NextShiftMinutesCalculator();
    private final AvailableHoursCalculator availableHoursCalculator = new AvailableHoursCalculator();
    private final ShiftPatternCalculator shiftPatternCalculator = new ShiftPatternCalculator();
    private final RiskLevelCalculator riskLevelCalculator = new RiskLevelCalculator();
    private final RecoveryStatusCalculator recoveryStatusCalculator = new RecoveryStatusCalculator();

    public AnalysisService(EnvironmentRepository environmentRepository,
                           ScheduleRepository scheduleRepository,
                           DailyStatusRepository dailyStatusRepository,
                           WearableService wearableService) {
        this.environmentRepository = environmentRepository;
        this.scheduleRepository = scheduleRepository;
        this.dailyStatusRepository = dailyStatusRepository;
        this.wearableService = wearableService;
    }

    @Transactional(readOnly = true)
    public AnalysisResponse analyze() {
        Environment environment = environmentRepository.findTopByOrderByIdAsc().orElse(null);
        if (environment == null) {
            return AnalysisResponse.fail("근무 시간이 설정되지 않았습니다.");
        }

        Map<LocalDate, ShiftType> schedules = toScheduleMap(scheduleRepository.findAll());

        // 이번 요청 안에서는 now를 한 번만 만들어 모든 Calculator에 동일하게 전달한다.
        LocalDateTime now = LocalDateTime.now();

        LocalDate referenceDate = referenceDateResolver.resolve(schedules, environment, now);

        int consecutiveDays = consecutiveDaysCalculator.calculate(schedules, referenceDate);

        // "다음 실제 근무 없음"의 대표 판정 기준은 NextShiftMinutesCalculator 하나로 통일한다.
        OptionalLong nextShiftMinutesOpt = nextShiftMinutesCalculator.calculate(schedules, environment, now);
        if (nextShiftMinutesOpt.isEmpty()) {
            return AnalysisResponse.fail("다음 근무 일정이 없습니다.");
        }
        long nextShiftMinutes = nextShiftMinutesOpt.getAsLong();

        OptionalDouble availableHoursOpt = availableHoursCalculator.calculate(schedules, environment, referenceDate, now);
        Optional<WorkTransitionPattern> patternOpt = shiftPatternCalculator.find(schedules, environment, referenceDate, now);

        // nextShiftMinutes가 존재하는데 availableHours/pattern이 비어 있으면 세 계산기 사이의
        // 전제가 어긋난 모순 상태다. 새 실패 응답을 만들지 않고 내부 오류로 즉시 알 수 있게 한다.
        if (availableHoursOpt.isEmpty() || patternOpt.isEmpty()) {
            throw new IllegalStateException(
                    "nextShiftMinutes는 존재하지만 availableHours 또는 ShiftPattern이 비어 있습니다. "
                            + "referenceDate=" + referenceDate + ", now=" + now);
        }

        double rawAvailableHours = availableHoursOpt.getAsDouble();
        WorkTransitionPattern pattern = patternOpt.get();

        // 화면 표시용 transitionType. risk용 pattern은 그대로 재사용하고, risk 계산에는 연결하지 않는다.
        String transitionType = displayTransitionCalculator.calculate(schedules, environment, referenceDate, now, pattern);

        // Risk 계산에는 반올림하지 않은 rawAvailableHours를 그대로 사용한다.
        RiskLevel riskLevel = riskLevelCalculator.calculate(pattern, consecutiveDays, rawAvailableHours);

        // 정상 프론트 흐름에서는 GET /api/analysis 호출 전에 DailyStatus가 반드시 저장돼 있다.
        // 없는 경우는 새 API 실패 응답을 만들지 않고 내부 precondition failure로 처리한다.
        DailyStatus latestDailyStatus = dailyStatusRepository.findTopByOrderByCreatedAtDesc()
                .orElseThrow(() -> new IllegalStateException(
                        "DailyStatus가 없는 상태로 GET /api/analysis가 호출되었습니다."));
        FatigueLevel fatigueLevel = latestDailyStatus.getFatigueLevel();

        // GET /api/wearable-data와 동일한 mock 데이터를 그대로 재사용한다(새 랜덤 생성 없음).
        WearableData wearable = wearableService.getCurrent();

        RecoveryStatus recoveryStatus = recoveryStatusCalculator.calculate(
                fatigueLevel, wearable.sleepHours(), wearable.activityLevel(), wearable.heartRate());

        CurrentConditionResponse currentCondition =
                CurrentConditionResponse.of(fatigueLevel, wearable.sleepHours(), recoveryStatus);

        return AnalysisResponse.ok(transitionType, consecutiveDays, round1(rawAvailableHours),
                nextShiftMinutes, riskLevel.name(), currentCondition);
    }

    private Map<LocalDate, ShiftType> toScheduleMap(List<Schedule> schedules) {
        Map<LocalDate, ShiftType> map = new HashMap<>();
        for (Schedule schedule : schedules) {
            map.put(schedule.getDate(), schedule.getShift());
        }
        return map;
    }

    // 표시용으로만 소수 첫째 자리 반올림한다(계산기 내부는 건드리지 않음).
    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
