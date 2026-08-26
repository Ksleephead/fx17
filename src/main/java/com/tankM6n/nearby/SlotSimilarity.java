// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

/** 每个槽位的完整调试分数；detectedType 为 null 表示两种模板都未过阈值。 */
public record SlotSimilarity(
        int row,
        int col,
        double panSimilarity,
        double stoneFireSimilarity,
        ItemType detectedType,
        int screenX,
        int screenY) {
}
