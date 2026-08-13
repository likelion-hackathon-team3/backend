package com.hackathon.backend.schedule;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// 근무표 한 칸(하루의 근무 유형)을 저장하는 Entity(=DB 테이블과 매핑되는 클래스).
// 외부 API JSON에서는 date/shift가 문자열이지만,
// 내부(DB)에서는 계산/검증에 유리한 LocalDate, ShiftType을 사용한다.
@Entity
@Getter
@NoArgsConstructor // JPA가 객체를 만들 때 기본 생성자가 필요해서 넣는다.
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 하루에 하나의 근무만 존재하도록 date에 unique 제약을 건다(DB 레벨 중복 방지).
    @Column(nullable = false, unique = true)
    private LocalDate date;

    // enum을 숫자(0,1,2..)가 아니라 문자열("DAY"...)로 저장한다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShiftType shift;

    public Schedule(LocalDate date, ShiftType shift) {
        this.date = date;
        this.shift = shift;
    }

    // upsert(있으면 수정) 처리 시 근무 유형만 바꿀 때 사용한다.
    public void updateShift(ShiftType shift) {
        this.shift = shift;
    }
}
