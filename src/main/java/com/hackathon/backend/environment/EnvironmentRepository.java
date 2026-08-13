package com.hackathon.backend.environment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// JpaRepository를 상속하면 기본 CRUD(save/findAll/delete 등)는 자동으로 제공된다.
public interface EnvironmentRepository extends JpaRepository<Environment, Long> {

    // 근무환경 설정은 단일 사용자 MVP 기준 사실상 1건만 존재한다.
    // 저장된 설정이 있으면 그 1건을 찾아 갱신/조회에 사용한다(가장 먼저 만들어진 행).
    Optional<Environment> findTopByOrderByIdAsc();
}
