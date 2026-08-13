package com.hackathon.backend.environment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

// 근무환경(3교대 시간대 + 통근시간) 설정을 저장하는 Entity(=DB 테이블과 매핑되는 클래스).
// MVP는 3교대만 지원하므로 DAY/EVENING/NIGHT 시간을 모두 저장한다.
// 외부 API JSON에서는 시간이 "HH:mm" 문자열이지만
// 내부(DB)에서는 계산에 유리한 LocalTime을 사용한다.
@Entity
@Getter
@NoArgsConstructor // JPA가 객체를 만들 때 기본 생성자가 필요해서 넣는다.
public class Environment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // DAY 근무 시작/종료
    @Column(nullable = false)
    private LocalTime dayStart;
    @Column(nullable = false)
    private LocalTime dayEnd;

    // EVENING 근무 시작/종료
    @Column(nullable = false)
    private LocalTime eveningStart;
    @Column(nullable = false)
    private LocalTime eveningEnd;

    // NIGHT 근무 시작/종료 (23:00 -> 07:00 처럼 날짜를 넘겨도 정상 값으로 취급)
    @Column(nullable = false)
    private LocalTime nightStart;
    @Column(nullable = false)
    private LocalTime nightEnd;

    // 평균 편도 통근시간(분)
    @Column(nullable = false)
    private Integer commuteMinutes;

    public Environment(LocalTime dayStart, LocalTime dayEnd,
                       LocalTime eveningStart, LocalTime eveningEnd,
                       LocalTime nightStart, LocalTime nightEnd,
                       Integer commuteMinutes) {
        this.dayStart = dayStart;
        this.dayEnd = dayEnd;
        this.eveningStart = eveningStart;
        this.eveningEnd = eveningEnd;
        this.nightStart = nightStart;
        this.nightEnd = nightEnd;
        this.commuteMinutes = commuteMinutes;
    }

    // 기존 설정을 갱신할 때 사용한다(새 행을 추가하지 않고 값만 바꿈).
    public void update(LocalTime dayStart, LocalTime dayEnd,
                       LocalTime eveningStart, LocalTime eveningEnd,
                       LocalTime nightStart, LocalTime nightEnd,
                       Integer commuteMinutes) {
        this.dayStart = dayStart;
        this.dayEnd = dayEnd;
        this.eveningStart = eveningStart;
        this.eveningEnd = eveningEnd;
        this.nightStart = nightStart;
        this.nightEnd = nightEnd;
        this.commuteMinutes = commuteMinutes;
    }
}
