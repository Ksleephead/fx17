// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

import java.awt.AWTException;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;

/** 使用 AWT Robot 截取屏幕中的固定区域。 */
public final class ScreenCapture {
    private final Robot robot;
    private final Rectangle virtualScreenBounds;

    public ScreenCapture() throws AWTException {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("当前环境没有可用的图形屏幕");
        }
        this.robot = new Robot();
        this.virtualScreenBounds = findVirtualScreenBounds();
    }

    public BufferedImage capture(Rectangle area) {
        // Robot 不接受宽高小于等于 0 的截图矩形。
        if (area == null || area.width <= 0 || area.height <= 0) {
            throw new IllegalArgumentException("截图 Rectangle 的宽和高必须大于 0");
        }
        // 截图前验证区域，坐标校准错误时给出明确提示，而不是让 Robot 随机失败。
        if (!virtualScreenBounds.contains(area)) {
            throw new IllegalArgumentException(
                    "截图区域超出屏幕范围: capture=" + area
                            + ", screens=" + virtualScreenBounds);
        }
        return robot.createScreenCapture(area);
    }

    private static Rectangle findVirtualScreenBounds() {
        // 合并所有显示器边界，也兼容副显示器位于主显示器左侧的负坐标情况。
        Rectangle result = null;
        for (GraphicsDevice device
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle bounds = device.getDefaultConfiguration().getBounds();
            result = result == null ? new Rectangle(bounds) : result.union(bounds);
        }
        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("无法取得屏幕范围");
        }
        return result;
    }
}
