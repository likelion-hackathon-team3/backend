package com.hackathon.backend.timeline;

import com.hackathon.backend.dailystatus.DailyStatusRepository;
import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.environment.EnvironmentRepository;
import com.hackathon.backend.schedule.Schedule;
import com.hackathon.backend.schedule.ScheduleRepository;
import com.hackathon.backend.schedule.ShiftType;
import com.hackathon.backend.timeline.dto.TimelineResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// TimelineService.getTimeline()(TODAY)이 AnalysisService.analyze()의 IllegalStateException
// (DailyStatus 등 Analysis 확보 실패 신호) 때문에 UnexpectedRollbackException으로 죽지 않는지
// 확인하는 실제 트랜잭션 통합 테스트.
//
// 이 클래스는 의도적으로 @Transactional을 붙이지 않는다. @Transactional을 붙이면 테스트
// 프레임워크가 먼저 트랜잭션을 열어두고 TimelineService.getTimeline()/AnalysisService.analyze()가
// 거기에 참여만(newTransaction=false)하게 되어, getTimeline() 자신이 트랜잭션을 새로 열고
// 커밋을 시도하는 실제 운영 상황(컨트롤러 진입 시점엔 활성 트랜잭션이 없음)을 재현하지 못한다.
// 그래서 여기서는 데이터를 실제로 커밋하고 @AfterEach에서 직접 지운다.
@SpringBootTest
class TimelineServiceTransactionIntegrationTest {

    @Autowired
    TimelineService timelineService;

    @Autowired
    ScheduleRepository scheduleRepository;

    @Autowired
    EnvironmentRepository environmentRepository;

    @Autowired
    DailyStatusRepository dailyStatusRepository;

    @AfterEach
    void cleanUp() {
        scheduleRepository.deleteAll();
        environmentRepository.deleteAll();
        dailyStatusRepository.deleteAll();
    }

    @Test
    void Analysis에_필요한_DailyStatus가_없어도_UnexpectedRollbackException_없이_fallback을_반환한다() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        environmentRepository.save(new Environment(
                LocalTime.of(0, 0), LocalTime.of(6, 0),   // DAY 00:00~06:00
                LocalTime.of(6, 0), LocalTime.of(20, 0),  // EVENING 06:00~20:00
                LocalTime.of(20, 0), LocalTime.of(23, 0), // NIGHT 20:00~23:00
                30
        ));
        scheduleRepository.save(new Schedule(today, ShiftType.EVENING));
        scheduleRepository.save(new Schedule(tomorrow, ShiftType.DAY));
        // DailyStatus는 의도적으로 저장하지 않는다 -> AnalysisService.analyze()가
        // "DailyStatus가 없는 상태로 GET /api/analysis가 호출되었습니다." IllegalStateException을
        // 던지는 경로를 강제로 유발한다.
        assertThat(dailyStatusRepository.count()).isZero();

        TimelineResponse[] captured = new TimelineResponse[1];

        // 수정 전에는 이 호출 자체가 UnexpectedRollbackException을 던졌다(TimelineService가
        // Analysis 예외를 catch해 정상 응답을 반환해도, 공유 트랜잭션이 이미 rollback-only로
        // 표시돼 있어 getTimeline()의 커밋 시점에 터졌다).
        assertThatCode(() -> captured[0] = timelineService.getTimeline(today.toString()))
                .doesNotThrowAnyException();

        assertThat(captured[0].getSuccess()).isTrue();
        assertThat(captured[0].getIsFallback()).isTrue();
    }
}
