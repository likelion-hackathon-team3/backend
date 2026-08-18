package com.hackathon.backend.timeline;

import java.time.LocalDateTime;

// TimelineItemPlacer가 만드는 배치 결과 하나. title/description 문구는
// TimelineDescriptionGenerator가 kind를 보고 만든다(외부 category는 kind.category()로 매핑).
record TimelineItemDraft(LocalDateTime start, LocalDateTime end, ActivityKind kind) {
    ActivityCategory category() {
        return kind.category();
    }
}
