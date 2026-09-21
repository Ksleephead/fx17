// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.training;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;

/** Robot boundary shared by training actions, with the original cancellation semantics. */
final class TrainingRobot {
    private final Robot robot;
    private final BooleanSupplier running;

    TrainingRobot(BooleanSupplier running) throws AWTException {
        this.robot = new Robot();
        this.running = running;
    }

    void ensureRunning() throws InterruptedException {
        if (!isRunning()) {
            throw new InterruptedException("training stopped");
        }
    }

    boolean isRunning() {
        return running.getAsBoolean() && !Thread.currentThread().isInterrupted();
    }

    void safeDelay(long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + Math.max(0L, millis);
        while (System.currentTimeMillis() < end) {
            ensureRunning();
            Thread.sleep(Math.min(100L, end - System.currentTimeMillis()));
        }
        ensureRunning();
    }

    Color getDelayedPixelColor(int x, int y) {
        long end = System.currentTimeMillis() + 300;
        while (System.currentTimeMillis() < end) {
            if (!running.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                return Color.BLACK;
            }
            try {
                Thread.sleep(Math.min(50L, end - System.currentTimeMillis()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Color.BLACK;
            }
        }
        return robot.getPixelColor(x, y);
    }

    void keyPress(int keyCode) {
        robot.keyPress(keyCode);
    }

    void keyRelease(int keyCode) {
        robot.keyRelease(keyCode);
    }

    void mouseMove(int x, int y) {
        robot.mouseMove(x, y);
    }

    void mousePress(int buttons) {
        robot.mousePress(buttons);
    }

    void mouseRelease(int buttons) {
        robot.mouseRelease(buttons);
    }

    Color getPixelColor(int x, int y) {
        return robot.getPixelColor(x, y);
    }

    BufferedImage createScreenCapture(Rectangle area) {
        return robot.createScreenCapture(area);
    }

    void delay(int millis) {
        robot.delay(millis);
    }

    void click(int buttonMask) throws InterruptedException {
        mousePress(buttonMask);
        safeDelay(50);
        mouseRelease(buttonMask);
    }

    void tabSwitch() throws InterruptedException {
        keyPress(java.awt.event.KeyEvent.VK_TAB);
        safeDelay(50);
        keyRelease(java.awt.event.KeyEvent.VK_TAB);
    }
}
