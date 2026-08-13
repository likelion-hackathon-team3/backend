package com.hackathon.backend.wearable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// WearableService 로직 테스트.
// 랜덤 값이라 정확한 숫자를 하드코딩해 비교하지 않는다.
//  → 대신 (1) 허용 범위, (2) 소수 한 자리, (3) 같은 실행 안에서 값이 동일하게 유지되는지를 검증한다.
@SpringBootTest
class WearableServiceTest {

    @Autowired
    WearableService wearableService;

    @Test
    void sleepHours는_전체_허용_범위_4_0에서_9_0_사이다() {
        double sleep = wearableService.getCurrent().sleepHours();
        assertThat(sleep).isBetween(4.0, 9.0);
    }

    @Test
    void activityLevel은_전체_허용_범위_2000에서_15000_사이다() {
        int activity = wearableService.getCurrent().activityLevel();
        assertThat(activity).isBetween(2000, 15000);
    }

    @Test
    void heartRate는_전체_허용_범위_55에서_95_사이다() {
        int heart = wearableService.getCurrent().heartRate();
        assertThat(heart).isBetween(55, 95);
    }

    @Test
    void sleepHours는_소수점_한_자리로_정리된다() {
        double sleep = wearableService.getCurrent().sleepHours();
        // 값에 10을 곱했을 때 정수라면 소수 첫째 자리까지만 있는 것이다. (예: 6.5 → 65.0)
        double scaled = sleep * 10;
        assertThat(scaled).isEqualTo(Math.rint(scaled));
    }

    @Test
    void 같은_실행_안에서_여러번_호출해도_값이_동일하게_유지된다() {
        WearableData first = wearableService.getCurrent();
        WearableData second = wearableService.getCurrent();
        WearableData third = wearableService.getCurrent();

        // 구현 세부(동일 인스턴스 여부)가 아니라 값이 같은지를 검증한다.
        assertThat(second.sleepHours()).isEqualTo(first.sleepHours());
        assertThat(second.activityLevel()).isEqualTo(first.activityLevel());
        assertThat(second.heartRate()).isEqualTo(first.heartRate());

        assertThat(third.sleepHours()).isEqualTo(first.sleepHours());
        assertThat(third.activityLevel()).isEqualTo(first.activityLevel());
        assertThat(third.heartRate()).isEqualTo(first.heartRate());
    }
}
