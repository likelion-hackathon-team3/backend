package com.hackathon.backend.analysis;

import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.schedule.ShiftType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// ReferenceDateResolver 단위 테스트.
// 순수 계산 클래스라 Spring 컨텍스트나 DB(H2/MySQL) 없이 바로 테스트한다.
class ReferenceDateResolverTest {

    private final ReferenceDateResolver resolver = new ReferenceDateResolver();

    // 시간대를 자유롭게 지정할 수 있는 Environment 빌더(통근시간은 여기선 무관하므로 0).
    private Environment env(String dayStart, String dayEnd,
                           String eveningStart, String eveningEnd,
                           String nightStart, String nightEnd) {
        return new Environment(
                LocalTime.parse(dayStart), LocalTime.parse(dayEnd),
                LocalTime.parse(eveningStart), LocalTime.parse(eveningEnd),
                LocalTime.parse(nightStart), LocalTime.parse(nightEnd),
                0
        );
    }

    private Map<LocalDate, ShiftType> schedules(Object... pairs) {
        Map<LocalDate, ShiftType> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(LocalDate.parse((String) pairs[i]), (ShiftType) pairs[i + 1]);
        }
        return map;
    }

    private LocalDate d(String date) {
        return LocalDate.parse(date);
    }

    private LocalDateTime t(String dateTime) {
        return LocalDateTime.parse(dateTime);
    }

    @Test
    void case1_전날NIGHT_자정시작_진행중이면_전날() {
        // NIGHT 00:00~07:00, evening 16:00 => 8/19 NIGHT 실제 = 8/20 00:00~07:00
        Environment e = env("08:00", "16:00", "16:00", "00:00", "00:00", "07:00");
        Map<LocalDate, ShiftType> s = schedules("2026-08-19", ShiftType.NIGHT);
        assertThat(resolver.resolve(s, e, t("2026-08-20T02:00"))).isEqualTo(d("2026-08-19"));
    }

    @Test
    void case2_전날NIGHT_자정시작_이미종료면_오늘() {
        Environment e = env("08:00", "16:00", "16:00", "00:00", "00:00", "07:00");
        Map<LocalDate, ShiftType> s = schedules("2026-08-19", ShiftType.NIGHT);
        assertThat(resolver.resolve(s, e, t("2026-08-20T10:00"))).isEqualTo(d("2026-08-20"));
    }

    @Test
    void case3_전날NIGHT_23시시작_진행중이면_전날() {
        // NIGHT 23:00~07:00 => 8/19 NIGHT 실제 = 8/19 23:00 ~ 8/20 07:00
        Environment e = env("08:00", "16:00", "16:00", "23:00", "23:00", "07:00");
        Map<LocalDate, ShiftType> s = schedules("2026-08-19", ShiftType.NIGHT);
        assertThat(resolver.resolve(s, e, t("2026-08-20T02:00"))).isEqualTo(d("2026-08-19"));
    }

    @Test
    void case4_now가_actualStart와_같으면_전날_시작포함() {
        // NIGHT 00:00~07:00 => actualStart 8/20 00:00. now == 8/20 00:00 => 진행 중 => 전날
        Environment e = env("08:00", "16:00", "16:00", "00:00", "00:00", "07:00");
        Map<LocalDate, ShiftType> s = schedules("2026-08-19", ShiftType.NIGHT);
        assertThat(resolver.resolve(s, e, t("2026-08-20T00:00"))).isEqualTo(d("2026-08-19"));
    }

    @Test
    void case5_now가_actualEnd와_같으면_오늘_종료제외() {
        // NIGHT 00:00~07:00 => actualEnd 8/20 07:00. now == 8/20 07:00 => 종료 => 오늘
        Environment e = env("08:00", "16:00", "16:00", "00:00", "00:00", "07:00");
        Map<LocalDate, ShiftType> s = schedules("2026-08-19", ShiftType.NIGHT);
        assertThat(resolver.resolve(s, e, t("2026-08-20T07:00"))).isEqualTo(d("2026-08-20"));
    }

    @Test
    void case6_전날EVENING_자정초과_진행중이면_전날() {
        // EVENING 16:00~00:30 => 8/19 EVENING 실제 = 8/19 16:00 ~ 8/20 00:30
        Environment e = env("08:00", "16:00", "16:00", "00:30", "01:00", "07:00");
        Map<LocalDate, ShiftType> s = schedules("2026-08-19", ShiftType.EVENING);
        assertThat(resolver.resolve(s, e, t("2026-08-20T00:15"))).isEqualTo(d("2026-08-19"));
    }

    @Test
    void case7_전날EVENING_자정초과_종료시각이면_오늘() {
        // 위와 동일하나 now == 8/20 00:30(=actualEnd) => 종료 => 오늘
        Environment e = env("08:00", "16:00", "16:00", "00:30", "01:00", "07:00");
        Map<LocalDate, ShiftType> s = schedules("2026-08-19", ShiftType.EVENING);
        assertThat(resolver.resolve(s, e, t("2026-08-20T00:30"))).isEqualTo(d("2026-08-20"));
    }

    @Test
    void case8_전날DAY가_자정넘겨_진행중이면_전날_일반규칙재사용() {
        // DAY 20:00~04:00(자정 넘김) => 8/19 DAY 실제 = 8/19 20:00 ~ 8/20 04:00
        // now 8/20 02:00 => 진행 중 => 전날 (NIGHT 전용이 아니라 일반 규칙으로 처리됨을 확인)
        Environment e = env("20:00", "04:00", "05:00", "12:00", "13:00", "19:00");
        Map<LocalDate, ShiftType> s = schedules("2026-08-19", ShiftType.DAY);
        assertThat(resolver.resolve(s, e, t("2026-08-20T02:00"))).isEqualTo(d("2026-08-19"));
    }

    @Test
    void case9_전날OFF면_오늘() {
        Environment e = env("08:00", "16:00", "16:00", "23:00", "23:00", "07:00");
        Map<LocalDate, ShiftType> s = schedules("2026-08-19", ShiftType.OFF);
        assertThat(resolver.resolve(s, e, t("2026-08-20T02:00"))).isEqualTo(d("2026-08-20"));
    }

    @Test
    void case10_전날_미등록이면_오늘() {
        Environment e = env("08:00", "16:00", "16:00", "23:00", "23:00", "07:00");
        Map<LocalDate, ShiftType> s = schedules(); // 8/19 미등록
        assertThat(resolver.resolve(s, e, t("2026-08-20T02:00"))).isEqualTo(d("2026-08-20"));
    }

    @Test
    void case11_오늘Schedule이_있어도_진행중인_전날NIGHT가_우선() {
        // 8/19 NIGHT 실제 = 8/20 00:00~07:00, 8/20 DAY도 등록됨, now 8/20 02:00.
        // 오늘(8/20) Schedule이 존재하지만 전날 NIGHT가 진행 중이므로 referenceDate = 8/19.
        Environment e = env("08:00", "16:00", "16:00", "00:00", "00:00", "07:00");
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-19", ShiftType.NIGHT,
                "2026-08-20", ShiftType.DAY
        );
        assertThat(resolver.resolve(s, e, t("2026-08-20T02:00"))).isEqualTo(d("2026-08-19"));
    }
}
