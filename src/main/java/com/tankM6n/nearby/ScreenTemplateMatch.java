// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

/** 屏幕矩形模板的一条匹配结果，坐标为模板命中区域的屏幕中心点。 */
public record ScreenTemplateMatch(
        double similarity,
        int screenX,
        int screenY) {
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
