package com.hackathon.backend.wearable;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

// 웨어러블 목업 데이터의 "단일 데이터 소스".
// 스프링 싱글톤 빈이라 서버 시작 시 딱 한 번 생성된다.
//  → 그 생성자에서 프리셋 하나를 랜덤 선택하고, 작은 noise를 적용해 최종 데이터 한 세트를 만든다.
//  → 만든 값은 final 필드(current)에 보관하고, 실행 중에는 계속 같은 값을 반환한다.
// 요청(GET /api/wearable-data)마다 새 랜덤을 만들지 않는다.
// 향후 AnalysisService도 이 서비스를 주입받아 같은 current 데이터를 재사용한다.
//
// 주의: 여기서는 목업 원천값(수면/활동/심박)만 만든다.
//       riskLevel / recoveryStatus / fatigueLevel 은 계산하지 않는다.
//       DB에 저장하지 않는다(Entity/Repository 없음).
@Service
public class WearableService {

    // ── 전체 허용 범위 (noise 적용 후 반드시 이 안으로 제한한다) ──
    private static final double SLEEP_MIN = 4.0;
    private static final double SLEEP_MAX = 9.0;
    private static final int ACTIVITY_MIN = 2000;
    private static final int ACTIVITY_MAX = 15000;
    private static final int HEART_MIN = 55;
    private static final int HEART_MAX = 95;

    // ── noise 범위 (프리셋 기준 ± 값) ──
    private static final double SLEEP_NOISE = 0.3;
    private static final int ACTIVITY_NOISE = 500;
    private static final int HEART_NOISE = 3;

    // ── 프리셋 10개 (Preset1~10). 순번일 뿐, 정상/위험 등 어떤 의미도 부여하지 않는다. ──
    // 1~7: 중심 범위(sleep 5.5~8.0 / activity 4000~10000 / heart 58~78) 안에서 값을 골고루 분산.
    // 8~10: 중심 범위 밖의 다양한 조합을 포함하기 위한 목업(전체 허용 범위 내). 의학적 의미 없음.
    private static final List<WearableData> PRESETS = List.of(
            new WearableData(6.5, 6000, 66),
            new WearableData(7.0, 8000, 62),
            new WearableData(5.8, 5000, 70),
            new WearableData(7.5, 9500, 60),
            new WearableData(6.0, 7000, 68),
            new WearableData(8.0, 10000, 58),
            new WearableData(5.5, 4500, 74),
            new WearableData(4.5, 3000, 85),   // 중심 범위 밖 조합 (전체 허용 범위 내)
            new WearableData(8.7, 13000, 57),  // 중심 범위 밖 조합 (전체 허용 범위 내)
            new WearableData(6.8, 12000, 78)   // 중심 범위 밖 조합 (전체 허용 범위 내)
    );

    // 서버 실행 동안 유지되는 최종 데이터 한 세트.
    private final WearableData current;

    // 빈 생성 시(=서버 시작 시) 딱 한 번 실행된다.
    public WearableService() {
        this.current = generate();
    }

    // 프리셋 선택 → noise 적용 → 허용 범위 제한 → sleepHours 소수 한 자리 정리 → 최종 데이터.
    // seed는 고정하지 않는다. 서버를 재시작하면 프리셋/noise가 달라져 새 값이 나올 수 있다.
    private WearableData generate() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        WearableData preset = PRESETS.get(random.nextInt(PRESETS.size()));

        // 프리셋 기준 ± 범위의 단순 균등(uniform) noise. Gaussian 등 복잡한 분포는 쓰지 않는다.
        double sleep = preset.sleepHours() + random.nextDouble(-SLEEP_NOISE, SLEEP_NOISE);
        int activity = preset.activityLevel() + random.nextInt(-ACTIVITY_NOISE, ACTIVITY_NOISE + 1);
        int heart = preset.heartRate() + random.nextInt(-HEART_NOISE, HEART_NOISE + 1);

        // noise 후 값이 전체 허용 범위를 벗어나면 범위 안으로 제한한다.
        sleep = clamp(sleep, SLEEP_MIN, SLEEP_MAX);
        activity = clamp(activity, ACTIVITY_MIN, ACTIVITY_MAX);
        heart = clamp(heart, HEART_MIN, HEART_MAX);

        // sleepHours는 소수점 한 자리로 정리한다. activity/heart는 정수 그대로.
        sleep = roundToOneDecimal(sleep);

        return new WearableData(sleep, activity, heart);
    }

    // 현재 서버 실행 중 보관 중인 웨어러블 데이터를 반환한다.
    // 같은 실행 안에서는 항상 같은 값이다. (향후 AnalysisService도 이 메서드를 사용)
    public WearableData getCurrent() {
        return current;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double roundToOneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
