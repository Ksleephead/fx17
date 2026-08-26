// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

/** 一条识别结果，包含物品类型、槽位、相似度和槽位中心屏幕坐标。 */
public record ItemMatch(
        ItemType type,
        int row,
        int col,
        double similarity,
        int screenX,
        int screenY) {
}
