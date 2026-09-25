// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.training;

import com.tankM6n.nearby.ScreenTemplateMatch;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Owns inventory positioning and the storage/hand water-container transfers. */
final class InventoryService {
    private final TrainingRobot robot;
    private final TrainingDetectionService detectionService;

    InventoryService(TrainingRobot robot, TrainingDetectionService detectionService) {
        this.robot = robot;
        this.detectionService = detectionService;
    }

    void ensureItemPanelPosition() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            Color color = robot.getDelayedPixelColor(370, 132);
            if (color.getRed() > 200 && color.getBlue() > 200 && color.getGreen() > 200) {
                break;
            }
            moveItemPanel();
        }
    }

    private void moveItemPanel() throws InterruptedException {
        try {
            ScreenTemplateMatch arrowMatch = detectionService.detectArrowOnce();
            if (arrowMatch == null) {
                System.out.println("未识别到物品栏箭头，跳过物品栏上移");
                return;
            }
            int arrowX = arrowMatch.screenX();
            int arrowY = arrowMatch.screenY();
            System.out.printf(
                    "ARROW -> similarity=%.3f x=%d y=%d%n",
                    arrowMatch.similarity(), arrowX, arrowY);

            robot.mouseMove(arrowX, arrowY);
            robot.safeDelay(1_000);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.safeDelay(500);
            robot.mouseMove(arrowX, 66);
            robot.safeDelay(500);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.safeDelay(500);
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("识别物品栏箭头失败: " + e.getMessage());
        }
    }

    void moveWaterToHands(int tryTimes) throws InterruptedException {
        if (tryTimes > 20) {
            return;
        }
        robot.mouseMove(444, 347);
        robot.safeDelay(500);
        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
        robot.safeDelay(500);
        robot.mouseMove(865, 135);
        robot.safeDelay(500);
        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
        robot.safeDelay(500);
        Color color = robot.getDelayedPixelColor(850, 130);
        if (color.getRed() < 90) {
            System.out.println(LocalDateTime.now()
                    + "从箱子移动到手上失败，重试，重试次数：" + tryTimes);
            moveWaterToHands(tryTimes + 1);
        }
    }

    void moveWaterBackToStorage(int tryTimes) throws InterruptedException {
        if (tryTimes > 20) {
            return;
        }
        Optional<StorageItemMatch> position = findCaseOrFridge("case");
        if (position.isEmpty()) {
            return;
        }
        StorageItemMatch fridge = position.get();

        robot.mouseMove(865, 135);
        robot.safeDelay(500);
        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
        robot.safeDelay(500);
        robot.mouseMove(fridge.screenX(), fridge.screenY());
        robot.safeDelay(500);
        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
        robot.safeDelay(500);
        Color color = robot.getDelayedPixelColor(435, 345);
        if (color.getRed() < 90) {
            System.out.println(LocalDateTime.now()
                    + "从手上移动到箱子失败，重试，重试次数：" + tryTimes);
            moveWaterBackToStorage(tryTimes + 1);
        }
    }

    void moveExtraWaterBackToStorage(int tryTimes) throws InterruptedException {
        if (tryTimes > 20) {
            return;
        }
        robot.mouseMove(448, 80);
        robot.safeDelay(500);
        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
        robot.safeDelay(500);
        robot.mouseMove(400, 80);
        robot.safeDelay(500);
        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
        robot.safeDelay(500);
        Color color = robot.getDelayedPixelColor(435, 345);
        if (color.getRed() < 90) {
            System.out.println(LocalDateTime.now()
                    + "从物品栏移动到箱子失败，重试，重试次数：" + tryTimes);
            moveExtraWaterBackToStorage(tryTimes + 1);
        }
    }

    Optional<StorageItemMatch> findCaseOrFridge(String type)
            throws InterruptedException {
        ensureItemPanelPosition();
        try {
            List<StorageItemMatch> matches = detectionService.detectStorageItemsOnce();
            if (matches.isEmpty()) {
                System.out.println("未识别到满足匹配度要求的箱子或冰箱");
            }
            for (StorageItemMatch match : matches) {
                System.out.printf(
                        "STORAGE_ITEM -> type=%s similarity=%.3f x=%d y=%d%n",
                        match.type(), match.similarity(), match.screenX(), match.screenY());
            }
            return matches.stream().filter(match -> type.equals(match.type())).findFirst();
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("识别箱子或冰箱失败: " + e.getMessage());
            return Optional.empty();
        }
    }
}
