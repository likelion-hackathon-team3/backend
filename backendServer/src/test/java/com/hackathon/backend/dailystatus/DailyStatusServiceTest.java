package com.hackathon.backend.dailystatus;

import com.hackathon.backend.dailystatus.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// DailyStatusService 로직 테스트.
// @Transactional : 각 테스트가 끝나면 DB 변경을 자동으로 되돌려 서로 영향을 주지 않게 한다.
@SpringBootTest
@Transactional
class DailyStatusServiceTest {

    @Autowired
    DailyStatusService dailyStatusService;

    @Autowired
    DailyStatusRepository dailyStatusRepository;

    // 요청 DTO를 편하게 만들기 위한 헬퍼 (DTO에 setter가 없어 리플렉션으로 값 주입)
    private DailyStatusRequest req(String fatigueLevel) {
        DailyStatusRequest r = new DailyStatusRequest();
        ReflectionTestUtils.setField(r, "fatigueLevel", fatigueLevel);
        return r;
    }

    @Test
    void 높음_정상_저장() {
        DailyStatusResponse res = dailyStatusService.save(req("높음"));
        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getMessage()).isNull(); // 성공 시 message 없음
        assertThat(dailyStatusRepository.count()).isEqualTo(1);
        assertThat(dailyStatusRepository.findAll().get(0).getFatigueLevel())
                .isEqualTo(FatigueLevel.HIGH);
    }

    @Test
    void 낮음_보통_정상_저장() {
        assertThat(dailyStatusService.save(req("낮음")).isSuccess()).isTrue();
        assertThat(dailyStatusService.save(req("보통")).isSuccess()).isTrue();
        assertThat(dailyStatusRepository.count()).isEqualTo(2);
    }

    @Test
    void 잘못된_값이면_실패하고_저장안됨() {
        DailyStatusResponse res = dailyStatusService.save(req("매우높음"));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("올바른 피로도 값을 선택해주세요.");
        assertThat(dailyStatusRepository.count()).isEqualTo(0);
    }

    @Test
    void fatigueLevel_null이면_실패() {
        DailyStatusResponse res = dailyStatusService.save(req(null));
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("올바른 피로도 값을 선택해주세요.");
        assertThat(dailyStatusRepository.count()).isEqualTo(0);
    }

    @Test
    void request_자체가_null이면_실패() {
        DailyStatusResponse res = dailyStatusService.save(null);
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("올바른 피로도 값을 선택해주세요.");
        assertThat(dailyStatusRepository.count()).isEqualTo(0);
    }

    @Test
    void createdAt은_서버에서_자동_기록된다() {
        dailyStatusService.save(req("보통"));
        DailyStatus saved = dailyStatusRepository.findAll().get(0);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void 같은_값을_여러번_입력하면_각각_별도로_쌓인다_append() {
        dailyStatusService.save(req("높음"));
        dailyStatusService.save(req("높음"));
        dailyStatusService.save(req("높음"));
        // upsert가 아니라 append이므로 3건이 쌓여야 한다.
        assertThat(dailyStatusRepository.count()).isEqualTo(3);
    }
}
