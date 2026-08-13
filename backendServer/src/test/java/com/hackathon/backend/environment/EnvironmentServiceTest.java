package com.hackathon.backend.environment;

import com.hackathon.backend.environment.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// EnvironmentService 로직 테스트.
// @Transactional : 각 테스트가 끝나면 DB 변경을 자동으로 되돌려 서로 영향을 주지 않게 한다.
@SpringBootTest
@Transactional
class EnvironmentServiceTest {

    @Autowired
    EnvironmentService environmentService;

    @Autowired
    EnvironmentRepository environmentRepository;

    // 요청 DTO를 편하게 만들기 위한 헬퍼 (DTO에 setter가 없어 리플렉션으로 값 주입)
    private ShiftTimeRequest shift(String start, String end) {
        ShiftTimeRequest s = new ShiftTimeRequest();
        ReflectionTestUtils.setField(s, "start", start);
        ReflectionTestUtils.setField(s, "end", end);
        return s;
    }

    private EnvironmentSaveRequest req(ShiftTimeRequest day, ShiftTimeRequest evening,
                                       ShiftTimeRequest night, Integer commute) {
        EnvironmentSaveRequest r = new EnvironmentSaveRequest();
        ReflectionTestUtils.setField(r, "dayShift", day);
        ReflectionTestUtils.setField(r, "eveningShift", evening);
        ReflectionTestUtils.setField(r, "nightShift", night);
        ReflectionTestUtils.setField(r, "commuteMinutes", commute);
        return r;
    }

    // 명세 예시와 동일한 정상 요청
    private EnvironmentSaveRequest validReq() {
        return req(shift("07:00", "15:00"),
                shift("15:00", "23:00"),
                shift("23:00", "07:00"),
                30);
    }

    @Test
    void 정상_저장() {
        EnvironmentSaveResponse res = environmentService.save(validReq());
        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getMessage()).isNull(); // 성공 시 message 없음
        assertThat(environmentRepository.count()).isEqualTo(1);
    }

    @Test
    void 저장후_GET_조회() {
        environmentService.save(validReq());

        EnvironmentResponse res = environmentService.get();
        assertThat(res.getDayShift().getStart()).isEqualTo("07:00");
        assertThat(res.getDayShift().getEnd()).isEqualTo("15:00");
        assertThat(res.getEveningShift().getStart()).isEqualTo("15:00");
        assertThat(res.getEveningShift().getEnd()).isEqualTo("23:00");
        assertThat(res.getNightShift().getStart()).isEqualTo("23:00");
        assertThat(res.getNightShift().getEnd()).isEqualTo("07:00");
        assertThat(res.getCommuteMinutes()).isEqualTo(30);
    }

    @Test
    void 다시_POST하면_기존설정_갱신되고_행이_늘지_않는다() {
        environmentService.save(validReq());
        // 다른 값으로 재저장
        environmentService.save(req(shift("08:00", "16:00"),
                shift("16:00", "00:00"),
                shift("00:00", "08:00"),
                45));

        // 행은 그대로 1개
        assertThat(environmentRepository.count()).isEqualTo(1);
        // 값은 갱신된 최신 값
        EnvironmentResponse res = environmentService.get();
        assertThat(res.getDayShift().getStart()).isEqualTo("08:00");
        assertThat(res.getCommuteMinutes()).isEqualTo(45);
    }

    @Test
    void 야간근무_자정넘김_정상처리() {
        environmentService.save(validReq()); // nightShift 23:00 -> 07:00
        EnvironmentResponse res = environmentService.get();
        assertThat(res.getNightShift().getStart()).isEqualTo("23:00");
        assertThat(res.getNightShift().getEnd()).isEqualTo("07:00");
    }

    @Test
    void 필수값_누락이면_실패하고_저장안됨() {
        // eveningShift 자체가 없는 요청
        EnvironmentSaveResponse res = environmentService.save(
                req(shift("07:00", "15:00"), null, shift("23:00", "07:00"), 30));

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("근무 시간을 모두 입력해주세요.");
        assertThat(environmentRepository.count()).isEqualTo(0); // 저장되지 않음
    }

    @Test
    void 시간형식_오류도_필수값누락_응답으로_처리() {
        EnvironmentSaveResponse res = environmentService.save(
                req(shift("25:99", "15:00"),
                        shift("15:00", "23:00"),
                        shift("23:00", "07:00"),
                        30));

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("근무 시간을 모두 입력해주세요.");
        assertThat(environmentRepository.count()).isEqualTo(0);
    }

    // 유효한 시간 범위 경계값(dayShift.start 자리에 넣어 검증) — 정상이면 저장 성공
    @Test
    void 경계값_00_00_정상() {
        EnvironmentSaveResponse res = environmentService.save(
                req(shift("00:00", "15:00"),
                        shift("15:00", "23:00"),
                        shift("23:00", "07:00"),
                        30));
        assertThat(res.isSuccess()).isTrue();
        assertThat(environmentRepository.count()).isEqualTo(1);
    }

    @Test
    void 경계값_23_59_정상() {
        EnvironmentSaveResponse res = environmentService.save(
                req(shift("23:59", "15:00"),
                        shift("15:00", "23:00"),
                        shift("23:00", "07:00"),
                        30));
        assertThat(res.isSuccess()).isTrue();
        assertThat(environmentRepository.count()).isEqualTo(1);
    }

    // 잘못된 시간 값들은 모두 필수값 누락 응답으로 처리되고 저장되지 않는다.
    @Test
    void 시간_24_00_실패() {
        assertInvalidTime("24:00");
    }

    @Test
    void 시간_25_00_실패() {
        assertInvalidTime("25:00");
    }

    @Test
    void 시간_12_60_실패() {
        assertInvalidTime("12:60");
    }

    @Test
    void 형식_7_00_실패() {
        assertInvalidTime("7:00");
    }

    // 주어진 잘못된 시간값을 dayShift.start에 넣었을 때 실패 응답 + 미저장을 확인하는 헬퍼
    private void assertInvalidTime(String badTime) {
        EnvironmentSaveResponse res = environmentService.save(
                req(shift(badTime, "15:00"),
                        shift("15:00", "23:00"),
                        shift("23:00", "07:00"),
                        30));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("근무 시간을 모두 입력해주세요.");
        assertThat(environmentRepository.count()).isEqualTo(0);
    }

    @Test
    void GET_미설정시_모든값_null() {
        EnvironmentResponse res = environmentService.get();
        assertThat(res.getDayShift()).isNull();
        assertThat(res.getEveningShift()).isNull();
        assertThat(res.getNightShift()).isNull();
        assertThat(res.getCommuteMinutes()).isNull();
    }
}
