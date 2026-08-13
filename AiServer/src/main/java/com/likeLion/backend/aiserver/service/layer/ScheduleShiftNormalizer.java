package com.likeLion.backend.aiserver.service.layer;

import com.likeLion.backend.aiserver.dto.RawExtractionResponse;
import com.likeLion.backend.aiserver.dto.RecognizedScheduleDto;
import com.likeLion.backend.aiserver.dto.ScheduleOcrResponse;
import com.likeLion.backend.aiserver.dto.ShiftType;
import com.likeLion.backend.aiserver.mapper.ShiftMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ScheduleShiftNormalizer {

    private final ShiftMapper shiftMapper;

    public ScheduleShiftNormalizer(ShiftMapper shiftMapper) {
        this.shiftMapper = shiftMapper;
    }

    public ScheduleOcrResponse normalize(RawExtractionResponse rawResponse) {
        if (rawResponse == null || !rawResponse.success()) {
            String msg = (rawResponse != null && rawResponse.message() != null) ? rawResponse.message() : "Vision 추출 실패";
            return ScheduleOcrResponse.failure(msg);
        }

        List<RecognizedScheduleDto> recognizedList = new ArrayList<>();
        List<String> failedDates = new ArrayList<>(rawResponse.failedDates() != null ? rawResponse.failedDates() : List.of());

        if (rawResponse.items() != null) {
            for (RawExtractionResponse.RawScheduleItem item : rawResponse.items()) {
                ShiftMapper.MappedShift mappedShift = shiftMapper.mapToShiftType(item.rawShift(), item.cellColor());
                if (mappedShift != null && mappedShift.type() != null) {
                    recognizedList.add(new RecognizedScheduleDto(item.date(), mappedShift.type().name(), mappedShift.confidence()));
                } else {
                    failedDates.add(item.date());
                }
            }
        }

        return ScheduleOcrResponse.success(recognizedList, failedDates);
    }
}
