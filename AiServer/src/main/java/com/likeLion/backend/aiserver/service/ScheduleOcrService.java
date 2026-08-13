package com.likeLion.backend.aiserver.service;

import com.likeLion.backend.aiserver.dto.ScheduleOcrResponse;
import com.likeLion.backend.aiserver.dto.ScheduleType;
import org.springframework.web.multipart.MultipartFile;

public interface ScheduleOcrService {
    ScheduleOcrResponse processScheduleOcr(MultipartFile file, String userName, ScheduleType scheduleType);
}


