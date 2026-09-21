// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.training;

import com.tankM6n.nearby.ScreenTemplateMatch;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.time.LocalDateTime;

/** Owns glove and shoe inspection/repair actions. */
final class RepairService {
    private final TrainingRobot robot;
    private final TrainingDetectionService detectionService;

    RepairService(TrainingRobot robot, TrainingDetectionService detectionService) {
        this.robot = robot;
        this.detectionService = detectionService;
    }

    void repairGloves(int cycle) throws InterruptedException {
        robot.ensureRunning();
        robot.tabSwitch();
        robot.safeDelay(300);
        robot.ensureRunning();
        robot.keyPress(KeyEvent.VK_1);
        robot.safeDelay(50);
        robot.keyRelease(KeyEvent.VK_1);
        robot.safeDelay(300);
        Color gloves = robot.getDelayedPixelColor(748, 758);
        if (gloves.getRed() > 200) {
            robot.mouseMove(715, 724);
            robot.safeDelay(500);
            robot.ensureRunning();
            robot.click(InputEvent.BUTTON3_DOWN_MASK);
            robot.safeDelay(500);
            try {
                ScreenTemplateMatch repairMatch = detectionService.detectRepairOnce();
                if (repairMatch == null) {
                    System.out.println("未识别到修理菜单，跳过手套修理");
                } else {
                    System.out.printf(
                            "XIULI -> similarity=%.3f x=%d y=%d%n",
                            repairMatch.similarity(), repairMatch.screenX(), repairMatch.screenY());
                    robot.mouseMove(repairMatch.screenX(), repairMatch.screenY());
                    robot.safeDelay(500);
                    robot.ensureRunning();
                    robot.click(InputEvent.BUTTON1_DOWN_MASK);
                    robot.safeDelay(8_000);
                    robot.ensureRunning();
                }
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                System.err.println("识别修理菜单失败: " + e.getMessage());
            }
        } else {
            System.out.println("手套状态良好，不用修" + LocalDateTime.now());
        }

        this.repairShoes(cycle);

        robot.tabSwitch();
    }

    void repairShoes(int cycle) throws InterruptedException {
        if (cycle % 5 != 0) {
            return;
        }
        robot.keyPress(KeyEvent.VK_1);
        robot.safeDelay(50);
        robot.keyRelease(KeyEvent.VK_1);
        robot.safeDelay(500);
        Color shoes = robot.getDelayedPixelColor(1017, 671);
        if (shoes.getRed() > 200) {
            System.out.println("修鞋子" + LocalDateTime.now());
            robot.mouseMove(981, 634);
            robot.safeDelay(300);
            robot.click(InputEvent.BUTTON3_DOWN_MASK);
            robot.safeDelay(300);
            robot.mouseMove(942, 715);
            robot.safeDelay(300);
            robot.click(InputEvent.BUTTON1_DOWN_MASK);
            robot.safeDelay(6_000);
        } else {
            System.out.println("鞋子状态良好，不用修" + LocalDateTime.now());
        }
    }
}
