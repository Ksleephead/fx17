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
    @Override
    public ItemType type() {
        return type;
    }

    @Override
    public int row() {
        return row;
    }

    @Override
    public int col() {
        return col;
    }

    @Override
    public double similarity() {
        return similarity;
    }

    @Override
    public int screenX() {
        return screenX;
    }

    @Override
    public int screenY() {
        return screenY;
    }
}
