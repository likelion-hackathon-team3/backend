package com.likeLion.backend.aiserver.symbol;

import java.util.Set;

public class ShiftSymbolDictionary {

    public static final Set<String> DAY_SYMBOLS = Set.of(
            "D", "d", "주", "주간", "DAY", "Day", "day", "낮"
    );

    public static final Set<String> EVENING_SYMBOLS = Set.of(
            "E", "e", "석", "夕", "EVENING", "Evening", "evening", "저녁"
    );

    public static final Set<String> NIGHT_SYMBOLS = Set.of(
            "N", "n", "야", "야간", "夜", "NIGHT", "Night", "night", "밤"
    );

    public static final Set<String> OFF_SYMBOLS = Set.of(
            "OFF", "Off", "off", "오프", "O", "o", "/", "-", "비", "비번", "휴", "연", "연차", "월차", "반차", "휴가"
    );

    public static final Set<String> DE_SYMBOLS = Set.of(
            "DE", "de", "De", "D/E", "d/e"
    );

    public static final Set<String> EN_SYMBOLS = Set.of(
            "EN", "en", "En", "E/N", "e/n"
    );

    public static final Set<String> ND_SYMBOLS = Set.of(
            "ND", "nd", "Nd", "N/D", "n/d"
    );

    public static final Set<String> MD_SYMBOLS = Set.of(
            "MD", "md", "Md", "MID", "Mid", "mid"
    );

    public static final Set<String> DAY_COLORS = Set.of("YELLOW", "ORANGE", "LIGHT_YELLOW");
    public static final Set<String> EVENING_COLORS = Set.of("GREEN", "LIGHT_GREEN", "LIME");
    public static final Set<String> NIGHT_COLORS = Set.of("BLUE", "CYAN", "LIGHT_BLUE", "PURPLE", "DARK_BLUE");
    public static final Set<String> OFF_COLORS = Set.of("RED", "PINK", "LIGHT_RED", "GRAY", "GREY");

    private ShiftSymbolDictionary() {}
}
