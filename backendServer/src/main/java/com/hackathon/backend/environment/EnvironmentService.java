package com.hackathon.backend.environment;

import com.hackathon.backend.environment.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

// 근무환경 설정의 실제 로직을 담는 계층. Controller와 Repository 사이에 있다.
@Service
public class EnvironmentService {

    // "HH:mm" 형식만 허용한다(예: "07:00").
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    // 유효한 시간만 통과시키는 정규식(정규식으로 범위를 먼저 막아 파서의 SMART 보정을 피한다).
    // hour: 00~23, minute: 00~59, 두 자리 고정.
    // 통과: "00:00", "23:59" / 실패: "24:00", "25:00", "12:60", "7:00", "25:99"
    private static final Pattern HHMM_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private final EnvironmentRepository environmentRepository;

    public EnvironmentService(EnvironmentRepository environmentRepository) {
        this.environmentRepository = environmentRepository;
    }

    // POST /api/environment : 근무환경 저장/수정(갱신)
    // 원칙: 3교대 시간(D/E/N)과 통근시간이 모두 있어야 하며, 하나라도 없으면 저장하지 않는다.
    @Transactional
    public EnvironmentSaveResponse save(EnvironmentSaveRequest request) {
        if (request == null) {
            return EnvironmentSaveResponse.invalid();
        }

        // 문자열 시간을 LocalTime으로 변환한다. 값이 없거나 형식이 틀리면 null이 된다.
        LocalTime dayStart = parseTime(startOf(request.getDayShift()));
        LocalTime dayEnd = parseTime(endOf(request.getDayShift()));
        LocalTime eveningStart = parseTime(startOf(request.getEveningShift()));
        LocalTime eveningEnd = parseTime(endOf(request.getEveningShift()));
        LocalTime nightStart = parseTime(startOf(request.getNightShift()));
        LocalTime nightEnd = parseTime(endOf(request.getNightShift()));
        Integer commuteMinutes = request.getCommuteMinutes();

        // 필수값 누락 또는 시간 형식 오류 → 명세의 필수값 누락 응답으로 안전하게 처리한다.
        // (명세에 별도 시간 형식 오류 응답이 없으므로 새 오류 계약을 만들지 않는다.)
        boolean anyMissing =
                dayStart == null || dayEnd == null ||
                eveningStart == null || eveningEnd == null ||
                nightStart == null || nightEnd == null ||
                commuteMinutes == null;
        if (anyMissing) {
            return EnvironmentSaveResponse.invalid();
        }

        // 기존 설정이 있으면 새 행을 추가하지 않고 값만 갱신한다(단일 사용자 MVP).
        LocalTime fDayStart = dayStart, fDayEnd = dayEnd;
        LocalTime fEveningStart = eveningStart, fEveningEnd = eveningEnd;
        LocalTime fNightStart = nightStart, fNightEnd = nightEnd;
        Integer fCommute = commuteMinutes;

        environmentRepository.findTopByOrderByIdAsc()
                .ifPresentOrElse(
                        existing -> existing.update(fDayStart, fDayEnd,
                                fEveningStart, fEveningEnd, fNightStart, fNightEnd, fCommute),
                        () -> environmentRepository.save(new Environment(fDayStart, fDayEnd,
                                fEveningStart, fEveningEnd, fNightStart, fNightEnd, fCommute))
                );

        return EnvironmentSaveResponse.ok();
    }

    // GET /api/environment : 저장된 근무환경 조회
    // 설정이 없으면 명세대로 모든 값을 null로 반환한다.
    @Transactional(readOnly = true)
    public EnvironmentResponse get() {
        return environmentRepository.findTopByOrderByIdAsc()
                .map(EnvironmentResponse::from)
                .orElseGet(EnvironmentResponse::empty);
    }

    private String startOf(ShiftTimeRequest shift) {
        return shift == null ? null : shift.getStart();
    }

    private String endOf(ShiftTimeRequest shift) {
        return shift == null ? null : shift.getEnd();
    }

    // "HH:mm" 문자열을 LocalTime으로 바꾼다. 값이 없거나 형식/범위가 틀리면 null.
    // 먼저 정규식으로 hour(00~23)/minute(00~59)를 검사해 "24:00" 같은 값이
    // SMART 파싱으로 "00:00"이 되어버리는 문제를 원천 차단한다.
    // 23:00 같은 값은 정상이며, 시간대 자체가 자정을 넘는지는 여기서 판단하지 않는다.
    private LocalTime parseTime(String value) {
        if (value == null) return null;
        if (!HHMM_PATTERN.matcher(value).matches()) return null;
        try {
            return LocalTime.parse(value, HHMM);
        } catch (Exception e) {
            return null;
        }
    }
}
