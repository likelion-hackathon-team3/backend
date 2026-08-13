package com.likeLion.backend.aiserver.mapper;

import com.likeLion.backend.aiserver.dto.ShiftType;
import com.likeLion.backend.aiserver.symbol.ShiftSymbolDictionary;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Set;
import java.util.Map;

@Component
public class ShiftMapper {

    private static final Map<ShiftType, Set<String>> SYMBOL_MAP = new HashMap<>();

    static {
        SYMBOL_MAP.put(ShiftType.DE, ShiftSymbolDictionary.DE_SYMBOLS);
        SYMBOL_MAP.put(ShiftType.EN, ShiftSymbolDictionary.EN_SYMBOLS);
        SYMBOL_MAP.put(ShiftType.ND, ShiftSymbolDictionary.ND_SYMBOLS);
        SYMBOL_MAP.put(ShiftType.MD, ShiftSymbolDictionary.MD_SYMBOLS);
        SYMBOL_MAP.put(ShiftType.DAY, ShiftSymbolDictionary.DAY_SYMBOLS);
        SYMBOL_MAP.put(ShiftType.EVENING, ShiftSymbolDictionary.EVENING_SYMBOLS);
        SYMBOL_MAP.put(ShiftType.NIGHT, ShiftSymbolDictionary.NIGHT_SYMBOLS);
        SYMBOL_MAP.put(ShiftType.OFF, ShiftSymbolDictionary.OFF_SYMBOLS);
    }

    public record MappedShift(ShiftType type, int confidence) {}

    private record FuzzyResult(ShiftType type, int distance) {}

    public MappedShift mapToShiftType(String rawText, String color) {
        // 1. 텍스트 우선 매핑 (정확도 100%)
        if (rawText != null && !rawText.trim().isEmpty()) {
            String trimmed = rawText.trim();
            ShiftType type = findByText(trimmed);
            if (type != null) return new MappedShift(type, 100);

            ShiftType upperType = findByText(trimmed.toUpperCase());
            if (upperType != null) return new MappedShift(upperType, 100);
            
            // 2. 레벤슈타인 거리 기반 퍼지 매칭 (Fuzzy Matching)
            FuzzyResult fuzzyResult = findByFuzzyText(trimmed.toUpperCase());
            if (fuzzyResult != null) {
                // 거리 비례 신뢰도 계산: 길이 대비 차이
                // 거리가 1이면 약 80%, 거리가 2면 약 60% 등으로 단순화 (퍼지 허용치는 최대 2로 이미 필터링 됨)
                int confidence = 100 - (fuzzyResult.distance() * 20);
                if (confidence < 0) confidence = 0;
                return new MappedShift(fuzzyResult.type(), confidence);
            }
        }

        // 3. 색상 Fallback 매핑 (정확도 70%)
        if (color != null && !color.trim().isEmpty()) {
            String upperColor = color.trim().toUpperCase();
            ShiftType colorType = findByColor(upperColor);
            if (colorType != null) {
                return new MappedShift(colorType, 70);
            }
        }

        return null;
    }

    private ShiftType findByText(String text) {
        for (Map.Entry<ShiftType, Set<String>> entry : SYMBOL_MAP.entrySet()) {
            if (entry.getValue().contains(text)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    private FuzzyResult findByFuzzyText(String text) {
        ShiftType bestType = null;
        int minDistance = Integer.MAX_VALUE;

        for (Map.Entry<ShiftType, Set<String>> entry : SYMBOL_MAP.entrySet()) {
            for (String symbol : entry.getValue()) {
                // 한 글자 기호는 오인식 확률이 높으므로 퍼지 매칭에서 제외하거나 0일때만 허용 (사실상 정확 매칭)
                if (symbol.length() <= 1) {
                    continue;
                }
                
                int distance = calculateLevenshteinDistance(text, symbol);
                
                // 허용 거리: 문자열 길이에 따라 동적 조절 (예: 길이 3 이하는 1글자 틀림 허용, 그 이상은 2글자)
                int maxAllowedDistance = symbol.length() <= 3 ? 1 : 2;
                
                if (distance <= maxAllowedDistance && distance < minDistance) {
                    minDistance = distance;
                    bestType = entry.getKey();
                }
            }
        }
        
        if (bestType != null) {
            return new FuzzyResult(bestType, minDistance);
        }
        return null;
    }

    private int calculateLevenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                    dp[i][j] = Math.min(dp[i - 1][j - 1] + cost, 
                               Math.min(dp[i - 1][j] + 1, 
                                        dp[i][j - 1] + 1));
                }
            }
        }
        return dp[a.length()][b.length()];
    }

    private ShiftType findByColor(String color) {
        if (ShiftSymbolDictionary.DAY_COLORS.contains(color)) return ShiftType.DAY;
        if (ShiftSymbolDictionary.EVENING_COLORS.contains(color)) return ShiftType.EVENING;
        if (ShiftSymbolDictionary.NIGHT_COLORS.contains(color)) return ShiftType.NIGHT;
        if (ShiftSymbolDictionary.OFF_COLORS.contains(color)) return ShiftType.OFF;
        return null;
    }
}
