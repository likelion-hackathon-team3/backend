package com.hackathon.backend.schedule;

// 근무 유형을 나타내는 enum(정해진 값들의 집합).
// API 명세의 shift 값과 이름을 똑같이 맞춘다: DAY, EVENING, NIGHT, OFF
// OFF는 실제 근무는 아니지만 유효한 일정이다.
public enum ShiftType {
    DAY,
    EVENING,
    NIGHT,
    OFF
}
