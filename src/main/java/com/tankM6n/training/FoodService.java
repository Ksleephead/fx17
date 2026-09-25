// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.training;

import com.tankM6n.nearby.ScreenTemplateMatch;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Owns nutrition checks, eating, water supplementation and food-related rest. */
final class FoodService {
    private final TrainingRobot robot;
    private final TrainingDetectionService detectionService;
    private final InventoryService inventoryService;
    private final RestService restService;
    private final String trainingEfficiency;
    private static volatile String restRequirement = "Unnecessary";

    FoodService(
            TrainingRobot robot,
            TrainingDetectionService detectionService,
            InventoryService inventoryService,
            RestService restService,
            String trainingEfficiency) {
        this.robot = robot;
        this.detectionService = detectionService;
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
        this.restService = restService;
        this.trainingEfficiency = trainingEfficiency;
    }

    void checkAndEat() throws InterruptedException {
        robot.safeDelay(1_000);
        robot.ensureRunning();
        robot.tabSwitch();
        robot.safeDelay(500);
        robot.ensureRunning();
        robot.keyPress(KeyEvent.VK_4);
        robot.safeDelay(50);
        robot.keyRelease(KeyEvent.VK_4);
        robot.safeDelay(1_000);
        closeIntelligencePanelIfOpen();

        robot.ensureRunning();
        boolean intestine = canIntestineAcceptFood();
        boolean stomach = canStomachAcceptFood();
        boolean lowAtNinetyPercent = isEnergyBelowNinetyPercent();
        boolean lowAtTwentyPercent = isEnergyBelowTwentyPercent();
        boolean proteinLow = isProteinLow();
        boolean waterLow = isWaterLow();

        robot.safeDelay(500);
        robot.ensureRunning();
        robot.tabSwitch();
        robot.safeDelay(500);

        if (stomach && intestine) {
            if ("效率优先".equals(trainingEfficiency) || lowAtNinetyPercent) {
                System.out.println(stomach + "" + intestine + lowAtNinetyPercent
                        + trainingEfficiency + LocalDateTime.now());
                eatFromHotbar();
            }
        }
        if (lowAtTwentyPercent || proteinLow || waterLow) {
            System.err.println("检测到能量或蛋白质或水分不足！，强制休息" + LocalDateTime.now());
            restRequirement = "necessary";
        }
        if (!lowAtTwentyPercent && !proteinLow && waterLow) {
            restRequirement = "drinkWater";
        }
    }

    void handleRequiredRest() throws InterruptedException {
        if ("drinkWater".equals(restRequirement)) {
            replenishWater();
            restRequirement = "Unnecessary";
        }
        if ("necessary".equals(restRequirement)) {
            waitForNutritionRecovery();
        }
    }

    private void replenishWater() throws InterruptedException {
        robot.safeDelay(2_000);
        tapKey(KeyEvent.VK_3, 100);
        robot.safeDelay(3_000);
        tapKey(KeyEvent.VK_3, 100);
        robot.safeDelay(3_000);
        robot.tabSwitch();
        robot.safeDelay(500);
        tapKey(KeyEvent.VK_4, 50);
        robot.safeDelay(500);

        if (canStomachAcceptFood() && canIntestineAcceptFood()) {
            tapKey(KeyEvent.VK_1, 50);
            robot.safeDelay(500);

            Optional<StorageItemMatch> position = inventoryService.findCaseOrFridge("case");
            if (position.isEmpty()) {
                return;
            }
            StorageItemMatch cases = position.get();
            inventoryService.ensureItemPanelPosition();
            Color caseColor = robot.getPixelColor(cases.screenX() - 24, cases.screenY() - 37);
            if (caseColor.getRed() < 200) {
                robot.mouseMove(cases.screenX(), cases.screenY());
                robot.safeDelay(500);
                robot.click(MouseEvent.BUTTON1_DOWN_MASK);
                robot.safeDelay(100);
                robot.click(MouseEvent.BUTTON1_DOWN_MASK);
            }

            robot.safeDelay(1_000);
            inventoryService.moveWaterToHands(0);
            for (int i = 0; i < 3; i++) {
                robot.safeDelay(500);
                robot.mouseMove(865, 135);
                robot.safeDelay(500);
                robot.click(MouseEvent.BUTTON3_DOWN_MASK);
                robot.safeDelay(500);
                try {
                    ScreenTemplateMatch match = detectionService.detectDrinkOnce();
                    if (match == null) {
                        System.out.println("未识别到喝一次菜单，停止喝水操作");
                        break;
                    }
                    System.out.printf(
                            "DRINK_ONCE -> similarity=%.3f x=%d y=%d%n",
                            match.similarity(), match.screenX(), match.screenY());
                    robot.mouseMove(match.screenX(), match.screenY());
                    robot.safeDelay(500);
                    robot.click(MouseEvent.BUTTON1_DOWN_MASK);
                    robot.safeDelay(3_000);
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
                    System.err.println("识别喝一次菜单失败: " + e.getMessage());
                    break;
                }
            }
            inventoryService.moveWaterBackToStorage(0);
            robot.safeDelay(500);
//            if (robot.getDelayedPixelColor(448, 80).getRed() > 90) {
//                inventoryService.moveExtraWaterBackToStorage(0);
//            }
        }
        robot.safeDelay(500);
        robot.tabSwitch();
    }

    private void waitForNutritionRecovery() throws InterruptedException {
        restService.standUp(1);
        CountDownLatch latch = new CountDownLatch(1);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                robot.ensureRunning();
                robot.tabSwitch();
                tapKey(KeyEvent.VK_4, 50);
                robot.safeDelay(500);
                boolean intestine = canIntestineAcceptFood();
                boolean stomach = canStomachAcceptFood();
                boolean energyLow = true;
                boolean proteinLow = true;

                if (robot.getDelayedPixelColor(751, 30).getBlue() > 90) {
                    System.out.println(LocalDateTime.now() + "能量充足");
                    energyLow = false;
                }
                if (robot.getDelayedPixelColor(700, 150).getBlue() > 90) {
                    System.out.println(LocalDateTime.now() + "蛋白质充足");
                    proteinLow = false;
                }

                robot.tabSwitch();
                robot.safeDelay(1_000);
                System.out.println("nengliang/" + energyLow + "/danBaizhi/" + proteinLow
                        + "/stomach/" + stomach + "/intestine/" + intestine
                        + LocalDateTime.now());
                if (energyLow || proteinLow) {
                    if (stomach && intestine) {
                        eatFromHotbar();
                    }
                } else {
                    restRequirement = "Unnecessary";
                    System.out.println("✅ 条件满足！准备唤醒外面线程...");
                    latch.countDown();
                    return;
                }
                System.out.println("⏳ 条件未满足，继续检测...");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                latch.countDown();
            } catch (Exception e) {
                if (robot.isRunning()) {
                    System.err.println("休息状态定时检测失败: " + e.getMessage());
                    e.printStackTrace();
                } else {
                    latch.countDown();
                }
            }
        }, 1, 20, TimeUnit.SECONDS);

        System.out.println("阻塞等待中...");
        try {
            latch.await();
        } finally {
            scheduler.shutdownNow();
        }
        System.out.println("被唤醒，定时检测已关闭");
    }

    private boolean canIntestineAcceptFood() {
        Color color = robot.getDelayedPixelColor(779, 493);
        return color.getBlue() > 35 && color.getBlue() < 60
                && color.getRed() > 35 && color.getRed() < 60
                && color.getGreen() > 35 && color.getGreen() < 60;
    }

    private boolean canStomachAcceptFood() {
        Color color = robot.getDelayedPixelColor(691, 543);
        return color.getBlue() > 35 && color.getBlue() < 60
                && color.getRed() > 35 && color.getRed() < 60
                && color.getGreen() > 35 && color.getGreen() < 60;
    }

    private boolean isWaterLow() {
        return robot.getDelayedPixelColor(956, 37).getBlue() < 55;
    }

    private boolean isEnergyBelowNinetyPercent() {
        if (robot.getDelayedPixelColor(740, 35).getBlue() > 60) {
            System.out.println(LocalDateTime.now() + "能量充足");
            return false;
        }
        return true;
    }

    private boolean isEnergyBelowTwentyPercent() {
        if (robot.getDelayedPixelColor(783, 38).getBlue() > 60) {
            System.out.println(LocalDateTime.now() + "能量充足");
            return false;
        }
        return true;
    }

    private boolean isProteinLow() {
        if (robot.getDelayedPixelColor(700, 179).getBlue() > 60) {
            System.out.println(LocalDateTime.now() + "蛋白质充足");
            return false;
        }
        return true;
    }

    private void eatFromHotbar() throws InterruptedException {
        tapKey(KeyEvent.VK_0, 100);
        robot.safeDelay(5_000);
        tapKey(KeyEvent.VK_4, 100);
        robot.safeDelay(5_000);
        tapKey(KeyEvent.VK_9, 100);
        robot.safeDelay(6_000);
    }

    private void closeIntelligencePanelIfOpen() throws InterruptedException {
        Color panel = robot.getDelayedPixelColor(298, 117);
        if (panel.getRed() > 200 && panel.getBlue() > 200 && panel.getGreen() > 200) {
            robot.mouseMove(47, 118);
            robot.safeDelay(50);
            robot.click(InputEvent.BUTTON1_DOWN_MASK);
            robot.safeDelay(100);
            robot.click(InputEvent.BUTTON1_DOWN_MASK);
        }
    }

    private void tapKey(int keyCode, long heldMillis) throws InterruptedException {
        robot.keyPress(keyCode);
        robot.safeDelay(heldMillis);
        robot.keyRelease(keyCode);
    }
}
