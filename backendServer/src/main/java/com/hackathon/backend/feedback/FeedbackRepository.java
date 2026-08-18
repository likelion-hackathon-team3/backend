package com.hackathon.backend.feedback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

// JpaRepository를 상속하면 기본 CRUD(save/findAll/delete 등)는 자동으로 제공된다.
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    // 같은 feedbackDate의 Feedback이 이미 있는지 찾는다 (upsert 판단에 사용).
    Optional<Feedback> findByFeedbackDate(LocalDate feedbackDate);
}
