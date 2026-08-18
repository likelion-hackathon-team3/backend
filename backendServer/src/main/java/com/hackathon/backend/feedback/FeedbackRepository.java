package com.hackathon.backend.feedback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// JpaRepository를 상속하면 기본 CRUD(save/findAll/delete 등)는 자동으로 제공된다.
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    // 같은 feedbackDate의 Feedback이 이미 있는지 찾는다 (upsert 판단에 사용).
    Optional<Feedback> findByFeedbackDate(LocalDate feedbackDate);

    // 동일 transitionType(=전환유형)의 과거 Feedback을 최신 날짜부터 조회한다.
    // Personalization이 "가장 최근 Feedback"을 찾을 때 정렬을 다시 하지 않아도 되게 DB에서 내림차순으로 받는다.
    List<Feedback> findByTransitionTypeOrderByFeedbackDateDesc(String transitionType);
}
