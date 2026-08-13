package com.hackathon.backend.dailystatus;

import com.hackathon.backend.dailystatus.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 현재 피로도 입력의 실제 로직을 담는 계층. Controller와 Repository 사이에 있다.
@Service
public class DailyStatusService {

    private final DailyStatusRepository dailyStatusRepository;

    public DailyStatusService(DailyStatusRepository dailyStatusRepository) {
        this.dailyStatusRepository = dailyStatusRepository;
    }

    // POST /api/daily-status : 현재 피로도 기록 저장(append)
    // 원칙: 허용된 한글 값이 아니면 저장하지 않는다. 같은 값도 매번 새 기록으로 쌓인다.
    @Transactional
    public DailyStatusResponse save(DailyStatusRequest request) {
        // request/fatigueLevel 누락도 잘못된 값과 동일하게 실패 처리한다(새 오류 메시지는 만들지 않음).
        String raw = (request == null) ? null : request.getFatigueLevel();
        FatigueLevel level = FatigueLevel.fromKorean(raw);

        if (level == null) {
            return DailyStatusResponse.invalid();
        }

        // createdAt은 서버에서 자동 기록한다(요청/응답에는 노출하지 않음).
        dailyStatusRepository.save(new DailyStatus(level, LocalDateTime.now()));
        return DailyStatusResponse.ok();
    }
}
