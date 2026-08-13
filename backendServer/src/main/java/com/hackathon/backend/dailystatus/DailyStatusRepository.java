package com.hackathon.backend.dailystatus;

import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository를 상속하면 기본 CRUD(save/findAll/count 등)는 자동으로 제공된다.
// 현재는 append 저장만 필요하므로 추가 메서드는 두지 않는다.
// (추후 Analysis/개인화 비교에서 조회 메서드를 확장할 수 있다.)
public interface DailyStatusRepository extends JpaRepository<DailyStatus, Long> {
}
