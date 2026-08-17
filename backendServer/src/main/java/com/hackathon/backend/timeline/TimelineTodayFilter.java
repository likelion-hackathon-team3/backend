package com.hackathon.backend.timeline;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// TimelineItemPlacer가 만든 전체(하루 종일) fallback Timeline 중,
// 조회 날짜가 "오늘"일 때만 현재시각(now) 이후의 활동만 남기는 후처리 필터.
// TimelineItemPlacer는 LocalDateTime.now()를 전혀 모르는 순수 결정적 생성기로 유지하고,
// 오늘 여부/현재시각 비교는 이 클래스가 전담한다(AiServer TODAY 모드와 사용자 경험을 맞추기 위함).
//
// 규칙:
// - 조회 date가 오늘이 아니면(미래/과거) 아무 것도 걸러내지 않고 그대로 반환한다.
// - item.end <= now  : 이미 완전히 끝난 활동 -> 제거.
// - item.start >= now: 아직 시작 전인 활동 -> 그대로 유지.
// - item.start < now < item.end : 지금 진행 중인 활동(SLEEP/REST/NAP 등) -> 제거하지 않고
//   원래 시작 시각(item.start)을 그대로 유지한 채 남긴다.
//   (프론트 계약에는 time 필드만 있고 종료시각이 없어서, start를 now로 바꾸면
//    "실제로 언제부터 하고 있었는지" 정보가 사라진다. 계약을 바꾸지 않는 선에서
//    가장 단순하고 정보 손실이 없는 방식은 원래 시작 시각을 그대로 보여주는 것이다.)
// - WORK 마커(start == end, 순간 아이템)는 이 시작/종료 비교와 무관하게 항상 유지한다.
class TimelineTodayFilter {

    List<TimelineItemDraft> apply(List<TimelineItemDraft> items, LocalDate date, LocalDateTime now) {
        if (!date.equals(now.toLocalDate())) {
            return items;
        }

        List<TimelineItemDraft> result = new ArrayList<>();
        for (TimelineItemDraft item : items) {
            if (isWorkMarker(item)) {
                result.add(item);
                continue;
            }
            if (!item.end().isAfter(now)) {
                continue; // 이미 완전히 종료됨
            }
            result.add(item); // 진행 중이거나 아직 시작 전 -> 원래 시각 그대로 유지
        }
        return result;
    }

    private boolean isWorkMarker(TimelineItemDraft item) {
        return item.start().equals(item.end());
    }
}
