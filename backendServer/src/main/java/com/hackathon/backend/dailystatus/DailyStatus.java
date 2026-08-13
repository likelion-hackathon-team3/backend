package com.hackathon.backend.dailystatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 특정 시점의 현재 피로도 입력을 저장하는 Entity(=DB 테이블과 매핑되는 클래스).
// 같은 날 여러 번 입력해도 각각 별도 기록으로 쌓인다(append). upsert/하루 1건 제한 없음.
// 외부 API는 한글 문자열이지만, 내부에서는 FatigueLevel enum으로 저장한다.
// createdAt은 서버에서 자동 기록하며 요청/응답에는 노출하지 않는다.
@Entity
@Getter
@NoArgsConstructor // JPA가 객체를 만들 때 기본 생성자가 필요해서 넣는다.
public class DailyStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // enum을 숫자가 아니라 문자열("HIGH" 등)로 저장한다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FatigueLevel fatigueLevel;

    // 입력 일시. 저장 시점에 서버가 자동으로 채운다(추후 과거 기록 비교에 활용).
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public DailyStatus(FatigueLevel fatigueLevel, LocalDateTime createdAt) {
        this.fatigueLevel = fatigueLevel;
        this.createdAt = createdAt;
    }
}
