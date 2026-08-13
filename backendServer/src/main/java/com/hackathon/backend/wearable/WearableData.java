package com.hackathon.backend.wearable;

// 서버 실행 중 유지되는 웨어러블 목업 데이터의 내부 표현.
// - API 응답 전용 DTO(WearableResponse)와 분리한다.
//   (내부 계산/보관용이고, 향후 AnalysisService도 이 값을 재사용한다.)
// - 필드는 명세와 동일하게 sleepHours / activityLevel / heartRate 3개만 둔다.
// record(불변 데이터 묶음): 생성 후 값이 바뀌지 않아, 실행 중 같은 값을 유지하기에 적합하다.
public record WearableData(
        double sleepHours,   // 시간 (소수점 한 자리로 정리된 값)
        int activityLevel,   // 걸음 수
        int heartRate        // 안정 시 심박수(bpm)
) {
}
