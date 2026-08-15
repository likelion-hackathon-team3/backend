package com.hackathon.backend.analysis;

import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.schedule.ShiftType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

// ShiftDateTimeResolver 단위 테스트.
// 순수 계산 클래스라 Spring 컨텍스트나 DB(H2/MySQL) 없이 바로 테스트한다.
class ShiftDateTimeResolverTest {

    private final ShiftDateTimeResolver resolver = new ShiftDateTimeResolver();

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

    private LocalDate d(String date) {
        return LocalDate.parse(date);
    }

    private LocalDateTime t(String dateTime) {
        return LocalDateTime.parse(dateTime);
    }

    @Test
    void A_NIGHT_23시시작_당일시작_다음날종료() {
        // NIGHT 23:00 ~ 07:00, eveningStart 16:00 => nightStart(23:00) >= eveningStart => 당일 시작
        Environment e = env("08:00", "16:00", "16:00", "23:00", "23:00", "07:00");
        assertThat(resolver.actualStart(d("2026-08-19"), ShiftType.NIGHT, e))
                .isEqualTo(t("2026-08-19T23:00"));
        assertThat(resolver.actualEnd(d("2026-08-19"), ShiftType.NIGHT, e))
                .isEqualTo(t("2026-08-20T07:00"));
    }

    @Test
    void B_NIGHT_자정시작_다음날시작_다음날종료() {
        // NIGHT 00:00 ~ 07:00, eveningStart 16:00 => nightStart(00:00) < eveningStart => 다음날 시작
        Environment e = env("08:00", "16:00", "16:00", "00:00", "00:00", "07:00");
        assertThat(resolver.actualStart(d("2026-08-19"), ShiftType.NIGHT, e))
                .isEqualTo(t("2026-08-20T00:00"));
        assertThat(resolver.actualEnd(d("2026-08-19"), ShiftType.NIGHT, e))
                .isEqualTo(t("2026-08-20T07:00"));
    }

    @Test
    void F_동률이면_당일시작() {
        // eveningStart == nightStart(16:00) => nightStart가 eveningStart보다 이르지 않음 => 당일 시작
        Environment e = env("08:00", "16:00", "16:00", "16:00", "16:00", "07:00");
        assertThat(resolver.actualStart(d("2026-08-19"), ShiftType.NIGHT, e))
                .isEqualTo(t("2026-08-19T16:00"));
    }

    @Test
    void DAY는_항상_scheduleDate_당일시작_당일종료() {
        Environment e = env("08:00", "16:00", "16:00", "23:00", "23:00", "07:00");
        assertThat(resolver.actualStart(d("2026-08-19"), ShiftType.DAY, e))
                .isEqualTo(t("2026-08-19T08:00"));
        assertThat(resolver.actualEnd(d("2026-08-19"), ShiftType.DAY, e))
                .isEqualTo(t("2026-08-19T16:00"));
    }

    @Test
    void EVENING이_자정으로_끝나면_종료는_다음날_00시() {
        // EVENING 16:00 ~ 00:00 : 시작은 당일, 종료 00:00 <= 16:00 => 다음날 00:00
        Environment e = env("08:00", "16:00", "16:00", "00:00", "00:00", "07:00");
        assertThat(resolver.actualStart(d("2026-08-19"), ShiftType.EVENING, e))
                .isEqualTo(t("2026-08-19T16:00"));
        assertThat(resolver.actualEnd(d("2026-08-19"), ShiftType.EVENING, e))
                .isEqualTo(t("2026-08-20T00:00"));
    }
}
