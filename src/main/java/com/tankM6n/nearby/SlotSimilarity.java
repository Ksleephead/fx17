// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

import java.util.Map;

/** 每个槽位的完整调试分数；detectedType 为 null 表示所有模板都未过阈值。 */
public record SlotSimilarity(
        int row,
        int col,
        Map<ItemType, Double> similarities,
        ItemType detectedType,
        int screenX,
        int screenY) {

    public SlotSimilarity {
        similarities = Map.copyOf(similarities);
    }

    public double similarity(ItemType type) {
        return similarities.getOrDefault(type, Double.NaN);
    }
}
