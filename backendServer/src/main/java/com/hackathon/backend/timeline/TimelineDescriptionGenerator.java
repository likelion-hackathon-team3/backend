package com.hackathon.backend.timeline;

import com.hackathon.backend.schedule.ShiftType;
import com.hackathon.backend.timeline.dto.TimelineItemResponse;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// AI 실패 시 사용하는 deterministic fallback 문구 생성기.
// title/description/pageTitle/pageSubtitle/recommendations 모두 카테고리·그룹 기반의
// 고정 한글 문구만 사용한다(복잡한 자연어 개인화 없음). AI 연동 시 이 클래스의 결과를
// AI가 생성한 문구로 교체하는 지점이 된다(연동 자체는 이번 단계 범위 밖).
// Analysis의 riskLevel/recoveryStatus는 fallback 문구 생성에 사용하지 않는다.
class TimelineDescriptionGenerator {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    List<TimelineItemResponse> toItemResponses(List<TimelineItemDraft> drafts, ShiftType nextShift) {
        List<TimelineItemResponse> result = new ArrayList<>();
        for (TimelineItemDraft draft : drafts) {
            result.add(toItemResponse(draft, nextShift));
        }
        return result;
    }

    private TimelineItemResponse toItemResponse(TimelineItemDraft draft, ShiftType nextShift) {
        String time = draft.start().format(TIME_FORMAT);
        return new TimelineItemResponse(
                time,
                title(draft.kind(), nextShift),
                description(draft.kind()),
                draft.category().name(),
                highlight(draft)
        );
    }

    private String highlight(TimelineItemDraft draft) {
        if (draft.kind() != ActivityKind.NORMAL_SLEEP && draft.kind() != ActivityKind.RECOVERY_SLEEP) {
            return null;
        }
        long minutes = Duration.between(draft.start(), draft.end()).toMinutes();
        if (minutes <= 0) {
            return null;
        }
        return "수면 목표 " + (minutes / 60) + "시간 " + (minutes % 60) + "분";
    }

    private String title(ActivityKind kind, ShiftType nextShift) {
        return switch (kind) {
            case POST_WORK_MEAL, POST_WORK_LIGHT_MEAL -> "퇴근 후 식사";
            case POST_WORK_REST -> "휴식";
            case DAY_WAKE_UP -> "기상";
            case BREAKFAST -> "아침 식사";
            case LUNCH -> "점심 식사";
            case DINNER -> "저녁 식사";
            case DAYTIME_EXERCISE -> "가벼운 운동";
            case DAYTIME_REST -> "휴식";
            case DAYTIME_NAP -> "낮잠";
            case SLEEP_PREP -> "취침 준비";
            case NORMAL_SLEEP -> "취침";
            case RECOVERY_SLEEP -> "회복 수면";
            case POST_SLEEP_WAKE_UP -> "기상";
            case POST_SLEEP_MEAL -> "식사";
            case PRE_NIGHT_NAP -> "근무 전 낮잠";
            case POST_NAP_WAKE_UP -> "기상";
            case PRE_WORK_MEAL, PRE_NIGHT_MEAL -> "근무 전 식사";
            case PRE_WORK_PREPARATION -> "출근 준비";
            case WORK_START -> shiftLabel(nextShift) + " 근무 시작";
        };
    }

    private String description(ActivityKind kind) {
        return switch (kind) {
            case POST_WORK_MEAL -> "단백질 위주의 가벼운 식사를 권장해요.";
            case POST_WORK_LIGHT_MEAL -> "무리하지 않는 선에서 가볍게 챙겨 드세요.";
            case POST_WORK_REST -> "잠시 몸과 마음을 쉬어주세요.";
            case DAY_WAKE_UP -> "햇빛을 10분 이상 쬐고 물 한 잔을 마셔요.";
            case BREAKFAST -> "복합탄수화물과 단백질을 섭취하세요.";
            case LUNCH -> "균형 잡힌 식사로 오후 에너지를 채워요.";
            case DINNER -> "소화가 잘 되는 음식으로 가볍게 드세요.";
            case DAYTIME_EXERCISE -> "무리하지 않는 강도로 몸을 움직여요.";
            case DAYTIME_REST -> "편안하게 몸을 쉬어주세요.";
            case DAYTIME_NAP -> "20~30분 이내로 짧게 자는 게 좋아요.";
            case SLEEP_PREP -> "조명 낮추기, 샤워, 디지털 기기 사용 줄이기";
            case NORMAL_SLEEP -> "충분한 수면으로 컨디션을 회복해요.";
            case RECOVERY_SLEEP -> "근무 후 피로 회복을 위한 수면이에요.";
            case POST_SLEEP_WAKE_UP -> "무리하지 않게 천천히 하루를 시작해요.";
            case POST_SLEEP_MEAL -> "가볍게 에너지를 채워주세요.";
            case PRE_NIGHT_NAP -> "야간 근무 전 컨디션 관리를 위한 낮잠이에요.";
            case POST_NAP_WAKE_UP -> "낮잠 후 가볍게 몸을 깨워주세요.";
            case PRE_WORK_MEAL -> "부담스럽지 않은 선에서 든든하게 드세요.";
            case PRE_NIGHT_MEAL -> "야간 근무를 앞두고 에너지를 채워주세요.";
            case PRE_WORK_PREPARATION -> "여유롭게 준비하고 출발해요.";
            case WORK_START -> "파이팅! 오늘도 잘 해내요!";
        };
    }

    private String shiftLabel(ShiftType shift) {
        if (shift == null) {
            return "";
        }
        return switch (shift) {
            case DAY -> "D";
            case EVENING -> "E";
            case NIGHT -> "N";
            case OFF -> "OFF";
        };
    }

    String pageTitle(TimelineRange range, ShiftType nextShift) {
        if (!range.hasUsableWindow()) {
            return "다음 근무까지 회복 시간이 부족해요";
        }
        return switch (range.group()) {
            case WORK_TO_WORK, OFF_TO_WORK -> "다음 " + shiftLabel(nextShift) + " 근무 전까지의 맞춤 계획이에요";
            case WORK_TO_OFF -> "근무 후 회복을 위한 맞춤 계획이에요";
            case OFF_TO_OFF -> "오늘 하루를 위한 맞춤 계획이에요";
        };
    }

    String pageSubtitle(TimelineRange range) {
        if (!range.hasUsableWindow()) {
            return "회복 시간이 매우 부족한 상태예요. 무리하지 않는 것이 가장 중요해요.";
        }
        return "회복을 최우선으로 한 기본 맞춤 루틴입니다.";
    }

    // 우선순위: 회복시간 없음 -> (TIGHT+다음 NIGHT) -> 다음 NIGHT(MODERATE/AMPLE) -> TIGHT
    //          -> NIGHT_TO_OFF -> OFF_TO_OFF -> 기본.
    // TIGHT+다음 NIGHT를 별도로 먼저 처리하는 이유: TimelineItemPlacer는 가용시간이 부족하면
    // PRE_NIGHT_NAP을 생성하지 않을 수 있어서, "계획된 낮잠" 문구를 그대로 쓰면 실제
    // Timeline에는 낮잠이 없는데 recommendation에는 있다고 말하는 모순이 생길 수 있다.
    List<String> recommendations(ShiftType currentShift, ShiftType nextShift,
                                 TimelineRange range, TimelineBudgetLevel budget) {
        if (!range.hasUsableWindow()) {
            return List.of(
                    "다음 근무 전 확보 가능한 회복 시간이 부족합니다.",
                    "가능한 경우 근무 일정 조정 또는 충분한 휴식 확보를 우선해주세요."
            );
        }
        if (nextShift == ShiftType.NIGHT && budget == TimelineBudgetLevel.TIGHT) {
            return List.of(
                    "야간근무 전에는 가능한 수면과 휴식을 가장 우선해 주세요.",
                    "카페인은 근무 초반에 필요한 만큼만 섭취하고, 예정된 수면에 가까운 시간에는 피해주세요.",
                    "시간이 부족한 만큼 식사와 출근 준비는 간단하게 유지해 주세요."
            );
        }
        if (nextShift == ShiftType.NIGHT) {
            return List.of(
                    "야간근무 전 충분한 수면과 계획된 낮잠으로 피로를 줄여보세요.",
                    "카페인은 근무 초반에 필요한 만큼만 섭취하고, 예정된 수면에 가까운 시간에는 피해주세요.",
                    "근무 전에는 부담이 적은 식사로 에너지를 보충해 주세요."
            );
        }
        if (budget == TimelineBudgetLevel.TIGHT) {
            return List.of(
                    "다음 근무 전에는 운동보다 수면과 휴식을 우선해 주세요.",
                    "짧은 시간에는 식사와 출근 준비를 간단하게 유지해 주세요.",
                    "졸음이 크면 카페인보다 짧은 수면을 우선하는 것이 좋아요."
            );
        }
        if (range.group() == TimelineGroup.WORK_TO_OFF && currentShift == ShiftType.NIGHT) {
            return List.of(
                    "야간근무 후에는 회복 수면을 가장 우선해 주세요.",
                    "늦은 카페인 섭취는 회복 수면을 방해할 수 있으니 피해주세요.",
                    "기상 후에는 무리한 운동보다 가벼운 식사와 휴식을 권장해요."
            );
        }
        if (range.group() == TimelineGroup.OFF_TO_OFF) {
            return List.of(
                    "규칙적인 수면과 기상 시간을 유지해 보세요.",
                    "가벼운 운동으로 컨디션을 관리해요.",
                    "늦은 시간 카페인 섭취는 되도록 피해주세요."
            );
        }
        return List.of(
                "균형 잡힌 식사와 휴식을 챙겨보세요.",
                "무리하지 않는 선에서 컨디션을 관리해요."
        );
    }
}
