// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

import java.awt.Point;
import java.awt.Rectangle;

/** 根据固定行列、槽位尺寸和间距计算附近栏中的位置。 */
public final class NearbyGrid {
    private final NearbyItemDetectorConfig config;

    public NearbyGrid(NearbyItemDetectorConfig config) {
        this.config = config;
    }

    public Rectangle iconArea(
            int row, int col,
            int iconOffsetX, int iconOffsetY,
            int iconWidth, int iconHeight) {
        checkSlot(row, col);
        // 这里得到的是相对于 nearby 截图左上角的槽位坐标。
        int slotX = col * (config.slotWidth() + config.slotGapX());
        int slotY = row * (config.slotHeight() + config.slotGapY());
        return new Rectangle(
                slotX + iconOffsetX,
                slotY + iconOffsetY,
                iconWidth,
                iconHeight);
    }

    public Point screenCenter(int row, int col) {
        checkSlot(row, col);
        int slotX = col * (config.slotWidth() + config.slotGapX());
        int slotY = row * (config.slotHeight() + config.slotGapY());
        // 加上 nearby 的屏幕绝对坐标，转换成该槽位中心的屏幕坐标。
        return new Point(
                config.nearbyX() + slotX + config.slotWidth() / 2,
                config.nearbyY() + slotY + config.slotHeight() / 2);
    }

    private void checkSlot(int row, int col) {
        if (row < 0 || row >= config.rows() || col < 0 || col >= config.cols()) {
            throw new IllegalArgumentException("槽位行列越界: row=" + row + ", col=" + col);
        }
    }
}
