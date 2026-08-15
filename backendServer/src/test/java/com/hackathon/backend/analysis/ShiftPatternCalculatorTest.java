package com.hackathon.backend.analysis;

import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.schedule.ShiftType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// ShiftPatternCalculator 단위 테스트.
// 순수 계산 클래스라 Spring 컨텍스트나 DB(H2/MySQL) 없이 바로 테스트한다.
// 가정 Environment: DAY 07:00~15:00 / EVENING 15:00~23:00 / NIGHT 23:00~07:00 / 통근 30분.
class ShiftPatternCalculatorTest {

    private final ShiftPatternCalculator calculator = new ShiftPatternCalculator();

    private Environment env() {
        return new Environment(
                LocalTime.of(7, 0), LocalTime.of(15, 0),
                LocalTime.of(15, 0), LocalTime.of(23, 0),
                LocalTime.of(23, 0), LocalTime.of(7, 0),
                30
        );
    }

    private Map<LocalDate, ShiftType> schedules(Object... pairs) {
        Map<LocalDate, ShiftType> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(LocalDate.parse((String) pairs[i]), (ShiftType) pairs[i + 1]);
        }
        return map;
    }

    // NIGHT가 01:37에 시작하는 근무환경(EVENING 16:00~23:00, NIGHT 01:37~07:30).
    // nightStart(01:37) < eveningStart(16:00) 이므로 NIGHT 실제 시작은 scheduleDate + 1일.
    // 즉 8/15 NIGHT -> 실제 8/16 01:37 ~ 8/16 07:30.
    private Environment envNightStarts0137() {
        return new Environment(
                LocalTime.of(7, 30), LocalTime.of(16, 0),
                LocalTime.of(16, 0), LocalTime.of(23, 0),
                LocalTime.of(1, 37), LocalTime.of(7, 30),
                30
        );
    }

    // A. 8/13 NIGHT 진행 중(23:30) 조회 -> N(8/13) -> OFF 1일 -> D(8/15)
    @Test
    void A_NIGHT_진행중_조회() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.NIGHT,
                "2026-08-14", ShiftType.OFF,
                "2026-08-15", ShiftType.DAY
        );
        Optional<WorkTransitionPattern> result =
                calculator.find(s, env(), LocalDate.parse("2026-08-13"), LocalDateTime.parse("2026-08-13T23:30:00"));

        assertThat(result).contains(new WorkTransitionPattern(
                ShiftType.NIGHT, LocalDate.parse("2026-08-13"),
                ShiftType.DAY, LocalDate.parse("2026-08-15"),
                1
        ));
    }

    // B. NIGHT 종료 후(다음날 10:00, OFF 상태) 조회해도 패턴이 그대로 유지된다.
    @Test
    void B_NIGHT_종료후_OFF에서_조회해도_패턴_유지() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.NIGHT,
                "2026-08-14", ShiftType.OFF,
                "2026-08-15", ShiftType.DAY
        );
        Optional<WorkTransitionPattern> result =
                calculator.find(s, env(), LocalDate.parse("2026-08-14"), LocalDateTime.parse("2026-08-14T10:00:00"));

        assertThat(result).contains(new WorkTransitionPattern(
                ShiftType.NIGHT, LocalDate.parse("2026-08-13"),
                ShiftType.DAY, LocalDate.parse("2026-08-15"),
                1
        ));
    }

    // C. 다음 DAY(8/15)의 actualStart(07:00) 전에 조회해도 여전히 N-OFF-D 패턴이다.
    @Test
    void C_다음근무_시작_전_조회는_이전패턴_유지() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.NIGHT,
                "2026-08-14", ShiftType.OFF,
                "2026-08-15", ShiftType.DAY
        );
        Optional<WorkTransitionPattern> result =
                calculator.find(s, env(), LocalDate.parse("2026-08-15"), LocalDateTime.parse("2026-08-15T06:30:00"));

        assertThat(result).contains(new WorkTransitionPattern(
                ShiftType.NIGHT, LocalDate.parse("2026-08-13"),
                ShiftType.DAY, LocalDate.parse("2026-08-15"),
                1
        ));
    }

    // D. DAY(8/15)가 실제로 시작된 이후에는 N-OFF-D 패턴이 종료되고,
    //    8/15 DAY 자신이 새 previousWorkShift가 되어 그 다음 실제 근무(8/17 EVENING)를 본다.
    @Test
    void D_다음근무_시작_이후에는_새_패턴으로_교체() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.NIGHT,
                "2026-08-14", ShiftType.OFF,
                "2026-08-15", ShiftType.DAY,
                "2026-08-17", ShiftType.EVENING
        );
        Optional<WorkTransitionPattern> result =
                calculator.find(s, env(), LocalDate.parse("2026-08-15"), LocalDateTime.parse("2026-08-15T09:00:00"));

        assertThat(result).contains(new WorkTransitionPattern(
                ShiftType.DAY, LocalDate.parse("2026-08-15"),
                ShiftType.EVENING, LocalDate.parse("2026-08-17"),
                1
        ));
    }

    // E. 휴무 2일(offDaysBetween=2).
    @Test
    void E_휴무_2일() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.NIGHT,
                "2026-08-14", ShiftType.OFF,
                "2026-08-15", ShiftType.OFF,
                "2026-08-16", ShiftType.DAY
        );
        Optional<WorkTransitionPattern> result =
                calculator.find(s, env(), LocalDate.parse("2026-08-14"), LocalDateTime.parse("2026-08-14T10:00:00"));

        assertThat(result).contains(new WorkTransitionPattern(
                ShiftType.NIGHT, LocalDate.parse("2026-08-13"),
                ShiftType.DAY, LocalDate.parse("2026-08-16"),
                2
        ));
    }

    // F. 휴무 3일 이상이면 offDaysBetween이 정확히 3으로 계산된다(점수 리셋은 PatternRiskCalculator 책임).
    @Test
    void F_휴무_3일_이상() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.NIGHT,
                "2026-08-14", ShiftType.OFF,
                "2026-08-15", ShiftType.OFF,
                "2026-08-16", ShiftType.OFF,
                "2026-08-17", ShiftType.DAY
        );
        Optional<WorkTransitionPattern> result =
                calculator.find(s, env(), LocalDate.parse("2026-08-14"), LocalDateTime.parse("2026-08-14T10:00:00"));

        assertThat(result).contains(new WorkTransitionPattern(
                ShiftType.NIGHT, LocalDate.parse("2026-08-13"),
                ShiftType.DAY, LocalDate.parse("2026-08-17"),
                3
        ));
    }

    // G. 8/13 NIGHT 이후 다음 실제 근무가 10/2 DAY처럼 먼 미래에 있어도
    //    day-by-day 순회 없이 TreeSet으로 정상적으로 찾는다. offDaysBetween은 49.
    @Test
    void G_먼_미래의_다음근무도_정상_탐색() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.NIGHT,
                "2026-10-02", ShiftType.DAY
        );
        Optional<WorkTransitionPattern> result =
                calculator.find(s, env(), LocalDate.parse("2026-08-13"), LocalDateTime.parse("2026-08-13T23:30:00"));

        assertThat(result).contains(new WorkTransitionPattern(
                ShiftType.NIGHT, LocalDate.parse("2026-08-13"),
                ShiftType.DAY, LocalDate.parse("2026-10-02"),
                49
        ));
    }

    // H. 8/14가 명시적 OFF가 아니라 미등록(entry 자체 없음)이어도 B와 완전히 동일한 결과여야 한다.
    @Test
    void H_명시적_OFF와_미등록은_결과가_동일() {
        Map<LocalDate, ShiftType> withOff = schedules(
                "2026-08-13", ShiftType.NIGHT,
                "2026-08-14", ShiftType.OFF,
                "2026-08-15", ShiftType.DAY
        );
        Map<LocalDate, ShiftType> withUnregistered = schedules(
                "2026-08-13", ShiftType.NIGHT,
                "2026-08-15", ShiftType.DAY
        );

        Optional<WorkTransitionPattern> resultWithOff =
                calculator.find(withOff, env(), LocalDate.parse("2026-08-14"), LocalDateTime.parse("2026-08-14T10:00:00"));
        Optional<WorkTransitionPattern> resultWithUnregistered =
                calculator.find(withUnregistered, env(), LocalDate.parse("2026-08-14"), LocalDateTime.parse("2026-08-14T10:00:00"));

        assertThat(resultWithUnregistered).isEqualTo(resultWithOff);
        assertThat(resultWithUnregistered).contains(new WorkTransitionPattern(
                ShiftType.NIGHT, LocalDate.parse("2026-08-13"),
                ShiftType.DAY, LocalDate.parse("2026-08-15"),
                1
        ));
    }

    // I. OFF와 미등록이 섞여 있어도 휴무 일수로 동일하게 합산된다.
    @Test
    void I_OFF와_미등록이_섞여도_휴무일수로_합산() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.NIGHT,
                "2026-08-14", ShiftType.OFF,
                "2026-08-16", ShiftType.DAY
                // 8/15는 미등록
        );
        Optional<WorkTransitionPattern> result =
                calculator.find(s, env(), LocalDate.parse("2026-08-15"), LocalDateTime.parse("2026-08-15T12:00:00"));

        assertThat(result).contains(new WorkTransitionPattern(
                ShiftType.NIGHT, LocalDate.parse("2026-08-13"),
                ShiftType.DAY, LocalDate.parse("2026-08-16"),
                2
        ));
    }

    // J. 이전 실제 근무가 전혀 없으면 previousWorkShift/Date는 null, offDaysBetween=0으로 저장한다.
    @Test
    void J_이전_실제_근무_없음() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.OFF,
                "2026-08-14", ShiftType.DAY
        );
        Optional<WorkTransitionPattern> result =
                calculator.find(s, env(), LocalDate.parse("2026-08-13"), LocalDateTime.parse("2026-08-13T09:00:00"));

        assertThat(result).contains(new WorkTransitionPattern(
                null, null,
                ShiftType.DAY, LocalDate.parse("2026-08-14"),
                0
        ));
    }

    // K. 다음 실제 근무가 없으면 Optional.empty().
    @Test
    void K_다음_실제_근무_없음() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.NIGHT
        );
        Optional<WorkTransitionPattern> result =
                calculator.find(s, env(), LocalDate.parse("2026-08-13"), LocalDateTime.parse("2026-08-13T23:30:00"));

        assertThat(result).isEmpty();
    }

    // L. NIGHT가 01:37에 시작하는 환경에서, 8/15 NIGHT의 실제 시작은 8/16 01:37이다.
    //    now=8/15 23:00은 그 실제 시작 전이므로 8/15 NIGHT는 아직 "시작 안 한 근무"이고,
    //    referenceDate(8/15) 자기 자신이 nextWorkShift로 선택되어야 한다(이전 실제 근무는 없음).
    @Test
    void L_NIGHT_실제시작_전이면_그_근무가_nextWorkShift() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-15", ShiftType.NIGHT
        );
        Optional<WorkTransitionPattern> result = calculator.find(
                s, envNightStarts0137(), LocalDate.parse("2026-08-15"), LocalDateTime.parse("2026-08-15T23:00:00"));

        assertThat(result).contains(new WorkTransitionPattern(
                null, null,
                ShiftType.NIGHT, LocalDate.parse("2026-08-15"),
                0
        ));
    }

    // M. 같은 환경에서 now가 실제 시작 시각(8/16 01:37)과 정확히 같으면 "이미 시작한 근무"로 처리해야 한다.
    //    (actualStart 자체는 진행 중 판정에 포함 -> referenceStarted 판정은 !now.isBefore(actualStart)).
    //    따라서 8/15 NIGHT는 previousWorkShift가 되고, 그 다음 실제 근무(8/17 DAY)를 nextWorkShift로 찾는다.
    @Test
    void M_NIGHT_실제시작_시각과_정확히_같으면_이미_시작한_근무() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-15", ShiftType.NIGHT,
                "2026-08-16", ShiftType.OFF,
                "2026-08-17", ShiftType.DAY
        );
        Optional<WorkTransitionPattern> result = calculator.find(
                s, envNightStarts0137(), LocalDate.parse("2026-08-15"), LocalDateTime.parse("2026-08-16T01:37:00"));

        assertThat(result).contains(new WorkTransitionPattern(
                ShiftType.NIGHT, LocalDate.parse("2026-08-15"),
                ShiftType.DAY, LocalDate.parse("2026-08-17"),
                1
        ));
    }

    // N. 일반 DAY 근무에서도 now == actualStart이면 "이미 시작한 근무"로 처리되어야 한다
    //    (NIGHT 자정 넘김 케이스에 국한된 동작이 아님을 확인).
    @Test
    void N_DAY도_now가_actualStart와_같으면_이미_시작한_근무() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.DAY,
                "2026-08-14", ShiftType.EVENING
        );
        Optional<WorkTransitionPattern> result = calculator.find(
                s, env(), LocalDate.parse("2026-08-13"), LocalDateTime.parse("2026-08-13T07:00:00"));

        assertThat(result).contains(new WorkTransitionPattern(
                ShiftType.DAY, LocalDate.parse("2026-08-13"),
                ShiftType.EVENING, LocalDate.parse("2026-08-14"),
                0
        ));
    }

    // ================= NIGHT roster-date 버그 회귀 =================
    // DAY(8/14) -> NIGHT(8/15, 01:37 시작) -> OFF(8/16).
    // referenceDate가 8/16으로 넘어간 상태에서도(예: ReferenceDateResolver가 그렇게 판단하는 상황),
    // 아직 시작 안 한 전날(8/15) NIGHT를 previousWorkDate가 아니라 nextWorkDate로 재분류해야 한다.

    // O. actualStart(8/16 01:37) 직전이면 재분류되어 DAY(8/14)->NIGHT(8/15), 0일 휴무로 나온다.
    @Test
    void O_referenceDate가_넘어가도_아직_시작_안한_전날NIGHT를_다음근무로_재분류() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-14", ShiftType.DAY,
                "2026-08-15", ShiftType.NIGHT,
                "2026-08-16", ShiftType.OFF
        );
        Optional<WorkTransitionPattern> result = calculator.find(
                s, envNightStarts0137(), LocalDate.parse("2026-08-16"), LocalDateTime.parse("2026-08-16T00:30:00"));

        assertThat(result).contains(new WorkTransitionPattern(
                ShiftType.DAY, LocalDate.parse("2026-08-14"),
                ShiftType.NIGHT, LocalDate.parse("2026-08-15"),
                0
        ));
    }

    // P. actualStart 정각(8/16 01:37)이면 재분류하지 않는다.
    // (NextShiftMinutesCalculator와 동일하게 actualStart.isAfter(now)만 재분류 대상 — 정각은 제외.
    //  실제 파이프라인에서는 이 시각엔 ReferenceDateResolver가 referenceDate를 8/15로 되돌리므로
    //  이 조합(referenceDate=8/16, now=8/16 01:37)은 발생하지 않지만, 재분류 조건의 경계값 자체를
    //  이 계산기 단위에서 직접 고정해 둔다.)
    @Test
    void P_actualStart_정각이면_재분류_안함() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-14", ShiftType.DAY,
                "2026-08-15", ShiftType.NIGHT,
                "2026-08-16", ShiftType.OFF
        );
        Optional<WorkTransitionPattern> result = calculator.find(
                s, envNightStarts0137(), LocalDate.parse("2026-08-16"), LocalDateTime.parse("2026-08-16T01:37:00"));

        assertThat(result).isEmpty();
    }

    // Q. actualStart 이후(진행 중)에도 재분류하지 않는다.
    @Test
    void Q_actualStart_이후에도_재분류_안함() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-14", ShiftType.DAY,
                "2026-08-15", ShiftType.NIGHT,
                "2026-08-16", ShiftType.OFF
        );
        Optional<WorkTransitionPattern> result = calculator.find(
                s, envNightStarts0137(), LocalDate.parse("2026-08-16"), LocalDateTime.parse("2026-08-16T01:38:00"));

        assertThat(result).isEmpty();
    }

    // R. previousWorkDate가 NIGHT가 아니면(DAY 등) 재분류 대상이 아니다(회귀 방지).
    @Test
    void R_이전근무가_NIGHT아니면_재분류_안함() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-14", ShiftType.DAY,
                "2026-08-16", ShiftType.OFF
        );
        Optional<WorkTransitionPattern> result = calculator.find(
                s, envNightStarts0137(), LocalDate.parse("2026-08-16"), LocalDateTime.parse("2026-08-16T00:30:00"));

        assertThat(result).isEmpty();
    }

    // S. 재분류 후 그 NIGHT보다 이전 실제 근무가 없으면 previousWorkShift=null로 처리된다
    //    (기존 J 케이스의 null 처리 로직을 그대로 재사용).
    @Test
    void S_재분류후_이전_실제_근무_없음() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-15", ShiftType.NIGHT,
                "2026-08-16", ShiftType.OFF
        );
        Optional<WorkTransitionPattern> result = calculator.find(
                s, envNightStarts0137(), LocalDate.parse("2026-08-16"), LocalDateTime.parse("2026-08-16T00:30:00"));

        assertThat(result).contains(new WorkTransitionPattern(
                null, null,
                ShiftType.NIGHT, LocalDate.parse("2026-08-15"),
                0
        ));
    }
}
