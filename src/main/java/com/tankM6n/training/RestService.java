// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.training;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/** Owns game switching, posture changes and stamina-recovery actions. */
final class RestService {
    private final TrainingRobot robot;
    private final double recoveryTime;
    private final String restType;

    RestService(TrainingRobot robot, double recoveryTime, String restType) {
        this.robot = robot;
        this.recoveryTime = recoveryTime;
        this.restType = restType;
    }

    void prepareForTraining(String startMode) throws InterruptedException {
        if ("default".equals(startMode)) {
            switchToGame();
        } else if ("restart".equals(startMode)) {
            recoverSitting();
            robot.ensureRunning();
            robot.keyPress(KeyEvent.VK_W);
            robot.safeDelay(300);
            robot.keyRelease(KeyEvent.VK_W);
            robot.safeDelay(4_000);
        } else if ("inGame".equals(startMode)) {
            robot.safeDelay(1_000);
        }
    }

    void standUp(int cycle) throws InterruptedException {
        if (cycle == 0) {
            return;
        }
        if ("趴下".equals(restType)) {
            robot.ensureRunning();
            robot.keyPress(KeyEvent.VK_X);
            robot.safeDelay(50);
            robot.keyRelease(KeyEvent.VK_X);
            robot.safeDelay(3_000);
        } else {
            robot.ensureRunning();
            robot.keyPress(KeyEvent.VK_W);
            robot.safeDelay(300);
            robot.keyRelease(KeyEvent.VK_W);
            robot.safeDelay(4_000);
            robot.ensureRunning();
        }
    }

    void recover() throws InterruptedException {
        if ("趴下".equals(restType)) {
            recoverLyingDown();
        } else {
            recoverSitting();
        }
    }

    private void switchToGame() throws InterruptedException {
        robot.ensureRunning();
        robot.keyPress(KeyEvent.VK_ALT);
        robot.safeDelay(100);
        robot.keyPress(KeyEvent.VK_TAB);
        robot.safeDelay(100);
        robot.keyRelease(KeyEvent.VK_TAB);
        robot.keyRelease(KeyEvent.VK_ALT);
        robot.safeDelay(3_000);
    }

    private void recoverLyingDown() throws InterruptedException {
        robot.safeDelay(1_000);
        robot.ensureRunning();
        robot.keyPress(KeyEvent.VK_X);
        robot.safeDelay(50);
        robot.keyRelease(KeyEvent.VK_X);
        robot.safeDelay((long) (recoveryTime * 1_000));
    }

    private void recoverSitting() throws InterruptedException {
        robot.safeDelay(1_000);
        robot.ensureRunning();
        robot.keyPress(KeyEvent.VK_TAB);
        robot.safeDelay(900);
        robot.mouseMove(460, 430);
        robot.safeDelay(900);
        robot.ensureRunning();
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.safeDelay(50);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.safeDelay(900);
        robot.mouseMove(592, 488);
        robot.safeDelay(900);
        robot.ensureRunning();
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.safeDelay(50);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.safeDelay(900);
        robot.keyRelease(KeyEvent.VK_TAB);
        robot.safeDelay((long) (recoveryTime * 1_000));
    }
}
