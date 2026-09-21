// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.game;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.util.concurrent.CancellationException;

/**
 * SCUM 自动化使用的底层 Robot 边界。
 *
 * <p>这里只封装鼠标、键盘、截图和可中断延时，不包含训练或做饭规则。</p>
 */
public final class GameRobot {
    private final Robot robot;

    public GameRobot() throws AWTException {
        this.robot = new Robot();
    }

    public void keyPress(int keyCode) {
        robot.keyPress(keyCode);
    }

    public void keyRelease(int keyCode) {
        robot.keyRelease(keyCode);
    }

    public void mouseMove(int x, int y) {
        robot.mouseMove(x, y);
    }

    public void mousePress(int buttons) {
        robot.mousePress(buttons);
    }

    public void mouseRelease(int buttons) {
        robot.mouseRelease(buttons);
    }

    public Color getPixelColor(int x, int y) {
        return robot.getPixelColor(x, y);
    }

    public BufferedImage capture(Rectangle area) {
        return robot.createScreenCapture(area);
    }

    /** 保持原有 100ms 轮询粒度，并在收到 interrupt 后立即终止调用方流程。 */
    public void delayInterruptibly(long millis) {
        long end = System.currentTimeMillis() + Math.max(0L, millis);
        while (System.currentTimeMillis() < end) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Robot operation stopped");
            }
            long remaining = end - System.currentTimeMillis();
            try {
                Thread.sleep(Math.min(100L, remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Robot operation stopped");
            }
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Robot operation stopped");
        }
    }

    /** 用于保持服务器重连原有的 AWT Robot.delay 行为。 */
    public void delay(int millis) {
        robot.delay(millis);
    }
}
