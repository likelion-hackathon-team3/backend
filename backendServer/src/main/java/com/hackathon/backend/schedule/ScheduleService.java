package com.hackathon.backend.schedule;

import com.hackathon.backend.schedule.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

// 실제 로직을 담는 계층. Controller와 Repository 사이에 있음.
@Service
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;

    public ScheduleService(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    // POST /api/schedules : 근무표 저장/수정(upsert)
    // 원칙: 먼저 전체를 검증하고, 하나라도 잘못되면 아무것도 저장하지 않는다(부분 저장 금지).
    @Transactional
    public ScheduleSaveResponse save(ScheduleSaveRequest request) {
        List<ScheduleItemRequest> items =
                request.getSchedules() == null ? new ArrayList<>() : request.getSchedules();

        // 1단계: 전체 검증 — 잘못된 날짜/근무유형을 가진 날짜를 모두 모은다.
        List<String> invalidDates = new ArrayList<>();
        for (ScheduleItemRequest item : items) {
            if (parseDate(item.getDate()) == null || parseShift(item.getShift()) == null) {
                invalidDates.add(item.getDate());
            }
        }

        // 하나라도 잘못되면 저장하지 않고 실패 응답을 돌려준다.
        if (!invalidDates.isEmpty()) {
            return ScheduleSaveResponse.invalid(invalidDates);
        }

        // 2단계: 모두 정상 → upsert 처리
        int savedCount = 0;
        for (ScheduleItemRequest item : items) {
            LocalDate date = parseDate(item.getDate());
            ShiftType shift = parseShift(item.getShift());

            // 같은 날짜가 이미 있으면 근무 유형만 수정, 없으면 새로 생성 (중복 row 방지)
            scheduleRepository.findByDate(date)
                    .ifPresentOrElse(
                            existing -> existing.updateShift(shift),
                            () -> scheduleRepository.save(new Schedule(date, shift))
                    );
            savedCount++;
        }
        return ScheduleSaveResponse.ok(savedCount);
    }

    // GET /api/schedules?month=YYYY-MM : 해당 월 근무표를 날짜 오름차순으로 조회
    @Transactional(readOnly = true)
    public ScheduleListResponse getByMonth(String month) {
        YearMonth ym = YearMonth.parse(month);         // "2026-08" -> 2026년 8월
        LocalDate start = ym.atDay(1);                  // 2026-08-01
        LocalDate end = ym.atEndOfMonth();              // 2026-08-31

        List<ScheduleItemResponse> result =
                scheduleRepository.findByDateBetweenOrderByDateAsc(start, end).stream()
                        .map(ScheduleItemResponse::from)
                        .toList();

        // 데이터가 없어도 오류가 아니라 빈 배열을 반환한다.
        return new ScheduleListResponse(result);
    }

    // DELETE /api/schedules?date=YYYY-MM-DD : 특정 날짜 근무 삭제
    @Transactional
    public ScheduleDeleteResponse delete(String date) {
        LocalDate target = parseDate(date);
        if (target == null) {
            // 날짜 형식 오류: 명세에 별도 응답이 없으므로 "없음"과 동일하게 처리한다.
            return new ScheduleDeleteResponse(false, "해당 날짜에 등록된 근무표가 없습니다.");
        }

        return scheduleRepository.findByDate(target)
                .map(schedule -> {
                    scheduleRepository.delete(schedule);
                    return new ScheduleDeleteResponse(true, "삭제되었습니다.");
                })
                .orElseGet(() ->
                        new ScheduleDeleteResponse(false, "해당 날짜에 등록된 근무표가 없습니다."));
    }

    // "YYYY-MM-DD" 문자열을 LocalDate로 바꾼다. 형식이 틀리면 null.
    private LocalDate parseDate(String date) {
        if (date == null) return null;
        try {
            return LocalDate.parse(date);
        } catch (Exception e) {
            return null;
        }
    }

    // "DAY" 등 문자열을 ShiftType으로 바꾼다. 허용되지 않은 값이면 null.
    private ShiftType parseShift(String shift) {
        if (shift == null) return null;
        try {
            return ShiftType.valueOf(shift);
        } catch (Exception e) {
            return null;
        }
    }
}
