// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

import java.util.List;

/** 一轮识别的最终匹配集合和逐槽位调试信息。 */
public record DetectionResult(
        List<ItemMatch> matches,
        List<SlotSimilarity> slotSimilarities) {

    public DetectionResult {
        matches = List.copyOf(matches);
        slotSimilarities = List.copyOf(slotSimilarities);
    }
}
