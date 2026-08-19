package com.hackathon.backend.dashboard;

import com.hackathon.backend.analysis.RiskLevel;
import com.hackathon.backend.dashboard.dto.DashboardAlertResponse;
import com.hackathon.backend.dashboard.dto.DashboardResponse;
import com.hackathon.backend.feedback.Feedback;
import com.hackathon.backend.feedback.FeedbackRepository;
import com.hackathon.backend.schedule.Schedule;
import com.hackathon.backend.schedule.ScheduleRepository;
import com.hackathon.backend.schedule.ShiftType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// GET /api/dashboard(사전예방 알림 조회) 실제 로직을 담는 계층.
// 새 Alert Entity/Table을 만들지 않고, Schedule + 기존 Feedback을 조회해서 요청 시 계산해 반환한다.
// riskLevel 값(NORMAL/CAUTION/DANGER)만 analysis.RiskLevel enum을 재사용하고,
// analysis 패키지의 "현재 시점 기준" 위험도 계산 로직(RiskLevelCalculator 등)은 가져오지 않는다.
// 이 API는 avgFatigue(과거 동일 전환 Feedback의 postShiftFatigue 평균)만으로 riskLevel을 자체 계산한다.
//
// 이 프로젝트에는 GlobalExceptionHandler가 없어 다른 Service들도 실패를 예외로 던지지 않지만,
// 이 API는 명세에 "기타 서버 오류" 고정 응답이 있어서 이 메서드에 한해 try-catch로 감싼다.
@Service
public class DashboardService {

    private static final double CAUTION_FATIGUE_SCORE = 5.0;
    private static final double HIGH_FATIGUE_SCORE = 8.0;
    private static final double SLEEP_SHORTAGE_HOURS = 6.0;
    private static final double LOW_ROUTINE_HELPFULNESS = 3.0;
    private static final LocalTime LATE_CAFFEINE_TIME = LocalTime.of(18, 0);

    private final ScheduleRepository scheduleRepository;
    private final FeedbackRepository feedbackRepository;

    public DashboardService(ScheduleRepository scheduleRepository, FeedbackRepository feedbackRepository) {
        this.scheduleRepository = scheduleRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(String startDateParam, String endDateParam) {
        try {
            if (isBlank(startDateParam) || isBlank(endDateParam)) {
                return DashboardResponse.fail("조회 시작일과 종료일을 입력해주세요.");
            }

            LocalDate startDate = parseDate(startDateParam);
            LocalDate endDate = parseDate(endDateParam);
            if (startDate == null || endDate == null) {
                return DashboardResponse.fail("날짜 형식이 올바르지 않습니다.");
            }

            if (startDate.isAfter(endDate)) {
                return DashboardResponse.fail("조회 기간을 확인해주세요.");
            }

            if (scheduleRepository.count() == 0) {
                return DashboardResponse.fail("등록된 근무표가 없습니다.");
            }

            Map<LocalDate, ShiftType> schedules = toScheduleMap(
                    scheduleRepository.findByDateBetweenOrderByDateAsc(startDate, endDate.plusDays(1)));
            List<Feedback> allFeedback = feedbackRepository.findAll();

            List<DashboardAlertResponse> alerts = buildAlerts(startDate, endDate, schedules, allFeedback);
            return DashboardResponse.ok(alerts);
        } catch (Exception e) {
            return DashboardResponse.fail("대시보드 정보를 불러오지 못했습니다.");
        }
    }

    // [startDate, endDate] 범위를 날짜 오름차순으로 훑으며 전환 후보를 만들고, 조건에 맞는 Alert만 담는다.
    private List<DashboardAlertResponse> buildAlerts(LocalDate startDate, LocalDate endDate,
                                                       Map<LocalDate, ShiftType> schedules,
                                                       List<Feedback> allFeedback) {
        List<DashboardAlertResponse> alerts = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            ShiftType current = schedules.get(date);
            ShiftType next = schedules.get(date.plusDays(1));

            // Schedule이 없는 날짜를 OFF로 추론하지 않는다 — 둘 중 하나라도 없으면 이 날짜는 그냥 건너뛴다.
            if (current == null || next == null) {
                continue;
            }

            String transitionType = current.name() + "_TO_" + next.name();
            LocalDate alertDate = date;

            List<Feedback> pastFeedback = allFeedback.stream()
                    .filter(f -> transitionType.equals(f.getTransitionType()))
                    .filter(f -> f.getFeedbackDate().isBefore(alertDate))
                    .toList();

            if (pastFeedback.isEmpty()) {
                continue;
            }

            double avgFatigue = average(pastFeedback, f -> (double) f.getPostShiftFatigue());
            RiskLevel riskLevel = calculateRiskLevel(avgFatigue);
            if (riskLevel == RiskLevel.NORMAL) {
                continue;
            }

            alerts.add(buildAlert(alertDate, transitionType, current, next, riskLevel, pastFeedback));
        }
        return alerts;
    }

    // 동일 transitionType 과거 Feedback들의 postShiftFatigue 평균으로 riskLevel을 정한다.
    // avgFatigue < 5.0 -> NORMAL / 5.0 <= avgFatigue < 8.0 -> CAUTION / avgFatigue >= 8.0 -> DANGER
    private RiskLevel calculateRiskLevel(double avgFatigue) {
        if (avgFatigue < CAUTION_FATIGUE_SCORE) {
            return RiskLevel.NORMAL;
        }
        if (avgFatigue < HIGH_FATIGUE_SCORE) {
            return RiskLevel.CAUTION;
        }
        return RiskLevel.DANGER;
    }

    private DashboardAlertResponse buildAlert(LocalDate date, String transitionType,
                                               ShiftType current, ShiftType next,
                                               RiskLevel riskLevel, List<Feedback> pastFeedback) {
        String title = buildTitle(current, next);
        Pattern pattern = choosePattern(pastFeedback);

        return new DashboardAlertResponse(date.toString(), transitionType, riskLevel.name(),
                title, pattern.message(), pattern.recommendation());
    }

    // 대표 패턴 우선순위: [1]수면부족 > [2]고피로 > [3]늦은카페인 > [4]낮은루틴도움 > [5]generic.
    // 패턴 선택(대표 패턴 여부)은 과거 Feedback 평균값 기준이지만,
    // 문구의 "확인되었습니다"/"반복되었습니다" 구분은 전체 Feedback 개수가 아니라
    // 그 패턴(또는 generic의 경우 postShiftFatigue >= 5) 조건에 실제로 해당하는
    // 개별 Feedback 발생 횟수로 판단한다.
    private Pattern choosePattern(List<Feedback> pastFeedback) {
        double avgSleep = average(pastFeedback, Feedback::getActualSleepDuration);
        double avgFatigue = average(pastFeedback, f -> (double) f.getPostShiftFatigue());
        double avgRoutine = average(pastFeedback, f -> (double) f.getRoutineHelpfulness());
        long lateCaffeineCount = pastFeedback.stream().filter(this::isLateCaffeine).count();

        if (avgSleep < SLEEP_SHORTAGE_HOURS) {
            long occurrences = pastFeedback.stream()
                    .filter(f -> f.getActualSleepDuration() < SLEEP_SHORTAGE_HOURS)
                    .count();
            return sleepPattern(occurrences);
        }
        if (avgFatigue >= HIGH_FATIGUE_SCORE) {
            long occurrences = pastFeedback.stream()
                    .filter(f -> f.getPostShiftFatigue() >= HIGH_FATIGUE_SCORE)
                    .count();
            return fatiguePattern(occurrences);
        }
        if (lateCaffeineCount > 0) {
            return caffeinePattern(lateCaffeineCount);
        }
        if (avgRoutine < LOW_ROUTINE_HELPFULNESS) {
            long occurrences = pastFeedback.stream()
                    .filter(f -> f.getRoutineHelpfulness() < LOW_ROUTINE_HELPFULNESS)
                    .count();
            return routinePattern(occurrences);
        }

        // 세부 패턴에 해당하지 않지만 riskLevel이 CAUTION/DANGER(avgFatigue >= 5.0)인 경우.
        // "높게 나타났습니다" 문구도 실제 postShiftFatigue >= 5인 개별 발생 횟수로 판단한다.
        long occurrences = pastFeedback.stream()
                .filter(f -> f.getPostShiftFatigue() >= CAUTION_FATIGUE_SCORE)
                .count();
        return genericPattern(occurrences);
    }

    private boolean isLateCaffeine(Feedback feedback) {
        return feedback.isCaffeineTaken()
                && feedback.getLastCaffeineTime() != null
                && !feedback.getLastCaffeineTime().isBefore(LATE_CAFFEINE_TIME);
    }

    private Pattern sleepPattern(long occurrences) {
        String message = occurrences >= 2
                ? "이전 동일 전환에서 수면 부족이 반복되었습니다."
                : "이전 동일 전환에서 수면 부족이 확인되었습니다.";
        return new Pattern(message, "오늘은 다른 활동보다 충분한 수면 시간을 확보해주세요.");
    }

    private Pattern fatiguePattern(long occurrences) {
        String message = occurrences >= 2
                ? "이전 동일 전환에서 높은 피로도가 반복되었습니다."
                : "이전 동일 전환에서 높은 피로도가 확인되었습니다.";
        return new Pattern(message, "다음 근무 전 충분한 휴식 시간을 확보하고 무리한 활동은 줄여주세요.");
    }

    private Pattern caffeinePattern(long occurrences) {
        String message = occurrences >= 2
                ? "이전 동일 전환에서 늦은 시간 카페인 섭취가 반복되었습니다."
                : "이전 동일 전환에서 늦은 시간 카페인 섭취가 확인되었습니다.";
        return new Pattern(message, "예정된 수면에 가까운 시간에는 카페인 섭취를 줄여주세요.");
    }

    private Pattern routinePattern(long occurrences) {
        String message = occurrences >= 2
                ? "이전 동일 전환에서 기존 루틴의 도움 정도가 반복적으로 낮게 나타났습니다."
                : "이전 동일 전환에서 기존 루틴의 도움 정도가 낮게 나타났습니다.";
        return new Pattern(message, "기존 루틴의 강도를 낮추고 수면과 휴식 중심으로 조정해보세요.");
    }

    private Pattern genericPattern(long occurrences) {
        String message = occurrences >= 2
                ? "이전 동일 전환에서 피로도가 반복적으로 높게 나타났습니다."
                : "이전 동일 전환에서 피로도가 높게 나타났습니다.";
        return new Pattern(message, "이번 전환 전에는 회복 시간을 충분히 확보해주세요.");
    }

    // title: 예정된 transition을 설명하는 문구. Shift 한글 표시로 조합한다.
    private String buildTitle(ShiftType current, ShiftType next) {
        boolean currentWork = current != ShiftType.OFF;
        boolean nextWork = next != ShiftType.OFF;

        if (currentWork && nextWork) {
            return koreanName(current) + " → " + koreanName(next) + " 전환이 예정되어 있어요.";
        }
        if (currentWork) {
            return koreanName(current) + " 근무 후 휴무가 예정되어 있어요.";
        }
        if (nextWork) {
            return "휴무 후 " + koreanName(next) + " 근무가 예정되어 있어요.";
        }
        return "연속 휴무가 예정되어 있어요.";
    }

    private String koreanName(ShiftType shift) {
        return switch (shift) {
            case DAY -> "데이";
            case EVENING -> "이브닝";
            case NIGHT -> "나이트";
            case OFF -> "휴무";
        };
    }

    private double average(List<Feedback> feedbackList, java.util.function.ToDoubleFunction<Feedback> extractor) {
        return feedbackList.stream().mapToDouble(extractor).average().orElse(0.0);
    }

    private Map<LocalDate, ShiftType> toScheduleMap(List<Schedule> schedules) {
        Map<LocalDate, ShiftType> map = new HashMap<>();
        for (Schedule schedule : schedules) {
            map.put(schedule.getDate(), schedule.getShift());
        }
        return map;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // "YYYY-MM-DD" 문자열을 LocalDate로 바꾼다. 형식이 틀리면(월/일 자릿수, 존재하지 않는 날짜 포함) null.
    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (Exception e) {
            return null;
        }
    }

    // 대표 패턴 하나의 message/recommendation 쌍.
    private record Pattern(String message, String recommendation) {
    }
}
