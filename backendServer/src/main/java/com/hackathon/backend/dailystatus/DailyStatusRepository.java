package com.hackathon.backend.dailystatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// JpaRepository를 상속하면 기본 CRUD(save/findAll/count 등)는 자동으로 제공된다.
public interface DailyStatusRepository extends JpaRepository<DailyStatus, Long> {

    // Analysis에서 "가장 최근에 저장된 현재 피로도"를 가져오는 데 쓴다.
    // createdAt 기준 최신 1건. 오늘 날짜인지는 별도로 검사하지 않는다.
    Optional<DailyStatus> findTopByOrderByCreatedAtDesc();
}
