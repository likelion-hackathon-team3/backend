package com.hackathon.backend.feedback;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// 사용자가 실제 근무 전환 후 제출한 피드백을 저장하는 Entity.
// transitionType은 요청으로 받지 않고 feedbackDate/feedbackDate+1의 Schedule로 계산해서 저장한다.
// (Schedule 중 하나라도 없으면 OFF로 추론하지 않고 null로 저장한다.)
// 외부 API에는 transitionType/submittedAt을 노출하지 않는다.
@Entity
@Getter
@NoArgsConstructor // JPA가 객체를 만들 때 기본 생성자가 필요해서 넣는다.
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 같은 feedbackDate의 Feedback은 하나만 유지한다(upsert). DB 레벨에서도 unique로 막는다.
    @Column(nullable = false, unique = true)
    private LocalDate feedbackDate;

    // 계산 결과. Schedule이 하나라도 없으면 null.
    private String transitionType;

    @Column(nullable = false)
    private Double actualSleepDuration;

    @Column(nullable = false)
    private boolean caffeineTaken;

    // taken=false면 요청에 값이 있어도 정규화해서 null로 저장한다(Service에서 처리).
    private LocalTime lastCaffeineTime;

    @Column(nullable = false)
    private Integer postShiftFatigue;

    @Column(nullable = false)
    private Integer routineHelpfulness;

    // 가장 최근에 이 feedbackDate로 제출된 시각. upsert 때마다 갱신된다.
    @Column(nullable = false)
    private LocalDateTime submittedAt;

    public Feedback(LocalDate feedbackDate, String transitionType, Double actualSleepDuration,
                     boolean caffeineTaken, LocalTime lastCaffeineTime, Integer postShiftFatigue,
                     Integer routineHelpfulness, LocalDateTime submittedAt) {
        this.feedbackDate = feedbackDate;
        this.transitionType = transitionType;
        this.actualSleepDuration = actualSleepDuration;
        this.caffeineTaken = caffeineTaken;
        this.lastCaffeineTime = lastCaffeineTime;
        this.postShiftFatigue = postShiftFatigue;
        this.routineHelpfulness = routineHelpfulness;
        this.submittedAt = submittedAt;
    }

    // upsert(같은 feedbackDate로 재제출) 시 기존 row의 값을 최신 내용으로 덮어쓴다.
    public void update(String transitionType, Double actualSleepDuration, boolean caffeineTaken,
                        LocalTime lastCaffeineTime, Integer postShiftFatigue, Integer routineHelpfulness,
                        LocalDateTime submittedAt) {
        this.transitionType = transitionType;
        this.actualSleepDuration = actualSleepDuration;
        this.caffeineTaken = caffeineTaken;
        this.lastCaffeineTime = lastCaffeineTime;
        this.postShiftFatigue = postShiftFatigue;
        this.routineHelpfulness = routineHelpfulness;
        this.submittedAt = submittedAt;
    }
}
