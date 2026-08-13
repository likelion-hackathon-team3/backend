package com.hackathon.backend.schedule;

import com.hackathon.backend.schedule.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// ScheduleService 로직 테스트.
// @Transactional : 각 테스트가 끝나면 DB 변경을 자동으로 되돌려 서로 영향을 주지 않게 한다.
@SpringBootTest
@Transactional
class ScheduleServiceTest {

    @Autowired
    ScheduleService scheduleService;

    // 요청 DTO를 편하게 만들기 위한 헬퍼
    private ScheduleSaveRequest req(String[]... pairs) {
        List<ScheduleItemRequest> items = new java.util.ArrayList<>();
        for (String[] p : pairs) {
            ScheduleItemRequest item = new ScheduleItemRequest();
            // ScheduleItemRequest에는 setter가 없으므로 리플렉션 대신 JSON 매핑을 흉내내지 않고
            // 테스트 전용으로 필드에 직접 값을 넣는다.
            org.springframework.test.util.ReflectionTestUtils.setField(item, "date", p[0]);
            org.springframework.test.util.ReflectionTestUtils.setField(item, "shift", p[1]);
            items.add(item);
        }
        ScheduleSaveRequest request = new ScheduleSaveRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "schedules", items);
        return request;
    }

    @Test
    void 정상_신규_저장() {
        ScheduleSaveResponse res = scheduleService.save(req(new String[]{"2026-08-10", "DAY"}));
        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getSavedCount()).isEqualTo(1);
    }

    @Test
    void 여러_일정_한번에_저장() {
        ScheduleSaveResponse res = scheduleService.save(req(
                new String[]{"2026-08-10", "DAY"},
                new String[]{"2026-08-11", "EVENING"},
                new String[]{"2026-08-12", "OFF"}
        ));
        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getSavedCount()).isEqualTo(3);
    }

    @Test
    void 월별_조회_오름차순() {
        scheduleService.save(req(
                new String[]{"2026-08-12", "NIGHT"},
                new String[]{"2026-08-10", "DAY"},
                new String[]{"2026-08-11", "EVENING"}
        ));
        ScheduleListResponse res = scheduleService.getByMonth("2026-08");
        assertThat(res.getSchedules()).hasSize(3);
        // 날짜 오름차순으로 정렬되어 나와야 한다.
        assertThat(res.getSchedules().get(0).getDate()).isEqualTo("2026-08-10");
        assertThat(res.getSchedules().get(1).getDate()).isEqualTo("2026-08-11");
        assertThat(res.getSchedules().get(2).getDate()).isEqualTo("2026-08-12");
    }

    @Test
    void 빈_월_조회() {
        ScheduleListResponse res = scheduleService.getByMonth("2030-01");
        assertThat(res.getSchedules()).isEmpty();
    }

    @Test
    void 같은_날짜_다시_저장하면_수정된다_upsert() {
        scheduleService.save(req(new String[]{"2026-08-10", "DAY"}));
        scheduleService.save(req(new String[]{"2026-08-10", "NIGHT"}));

        ScheduleListResponse res = scheduleService.getByMonth("2026-08");
        // 중복 row가 생기지 않고 1개만 있어야 한다.
        assertThat(res.getSchedules()).hasSize(1);
        assertThat(res.getSchedules().get(0).getShift()).isEqualTo("NIGHT");
    }

    @Test
    void 정상_삭제() {
        scheduleService.save(req(new String[]{"2026-08-10", "DAY"}));
        ScheduleDeleteResponse res = scheduleService.delete("2026-08-10");
        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getMessage()).isEqualTo("삭제되었습니다.");
    }

    @Test
    void 없는_날짜_삭제() {
        ScheduleDeleteResponse res = scheduleService.delete("2026-08-10");
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("해당 날짜에 등록된 근무표가 없습니다.");
    }

    @Test
    void 잘못된_shift_하나면_저장안됨() {
        ScheduleSaveResponse res = scheduleService.save(req(
                new String[]{"2026-08-10", "DAY"},
                new String[]{"2026-08-11", "WRONG"}
        ));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getInvalidDates()).containsExactly("2026-08-11");
        // 부분 저장 금지: 정상 데이터도 저장되면 안 된다.
        assertThat(scheduleService.getByMonth("2026-08").getSchedules()).isEmpty();
    }

    @Test
    void 잘못된_shift_여러개() {
        ScheduleSaveResponse res = scheduleService.save(req(
                new String[]{"2026-08-10", "WRONG1"},
                new String[]{"2026-08-11", "EVENING"},
                new String[]{"2026-08-12", "WRONG2"}
        ));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getInvalidDates()).containsExactly("2026-08-10", "2026-08-12");
    }
}
