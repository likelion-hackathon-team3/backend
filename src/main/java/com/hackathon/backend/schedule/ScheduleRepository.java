package com.hackathon.backend.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// JpaRepository를 상속하면 기본 CRUD(save/findAll/delete 등)는 자동으로 제공된다.
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    // 특정 날짜의 근무표 찾기 (upsert 판단, 삭제 시 존재 여부 확인에 사용)
    Optional<Schedule> findByDate(LocalDate date);

    // 특정 기간(start~end)의 근무표를 날짜 오름차순으로 조회 (월별 조회에 사용)
    List<Schedule> findByDateBetweenOrderByDateAsc(LocalDate start, LocalDate end);
}
