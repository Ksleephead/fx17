// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.training;

import com.tankM6n.nearby.ScreenTemplateMatch;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Owns the storage-destruction sequence and all state carried between hits/cycles. */
final class StorageService {
    private final TrainingRobot robot;
    private final TrainingDetectionService detectionService;
    private final InventoryService inventoryService;
    private final ExecutorService executor;
    private final double timePerHit;
    private final boolean autoCaffeine;
    private final boolean foodStorageIntoFridge;

    private int lastCoffeeEdge;
    private int lastDestroyTime;
    private boolean shouldSkipFinalDestroy;
    private boolean coffeeCheck = true;

    StorageService(
            TrainingRobot robot,
            TrainingDetectionService detectionService,
            InventoryService inventoryService,
            ExecutorService executor,
            double timePerHit,
            boolean autoCaffeine,
            boolean foodStorageIntoFridge) {
        this.robot = robot;
        this.detectionService = detectionService;
        this.inventoryService = inventoryService;
        this.executor = executor;
        this.timePerHit = timePerHit;
        this.autoCaffeine = autoCaffeine;
        this.foodStorageIntoFridge = foodStorageIntoFridge;
    }

    void destroyForCycle() throws Exception {
        for (int hit = 0; hit < 4; hit++) {
            if (hit == 3 && shouldSkipFinalDestroy) {
                System.out.println("检测到体力已经达到最大值,不再进行最后一次砸箱子"
                        + LocalDateTime.now());
                break;
            }
            DestroyResult result = destroy(lastCoffeeEdge, hit);
            lastCoffeeEdge = result.lastCoffeeEdge();
            if (result.nextHitIndex() != 0) {
                coffeeCheck = false;
                hit = result.nextHitIndex();
            }
        }
        coffeeCheck = true;
    }

    private DestroyResult destroy(int previousCoffeeEdge, int hit) throws Exception {
        robot.ensureRunning();
        tapKey(KeyEvent.VK_1, 50);
        robot.safeDelay(500);

        StorageItemMatch storagePosition = null;
        if (foodStorageIntoFridge) {
            Optional<StorageItemMatch> position = findCaseOrFridge("case");
            if (position.isPresent()) {
                storagePosition = position.get();
                robot.mouseMove(storagePosition.screenX(), storagePosition.screenY());
            }
        } else {
            robot.mouseMove(380, 100);
        }
        robot.safeDelay(500);
        robot.ensureRunning();
        robot.click(InputEvent.BUTTON3_DOWN_MASK);
        robot.safeDelay(500);

        if (foodStorageIntoFridge) {
            ScreenTemplateMatch destroyMatch = findDestroyButton(storagePosition);
            if (destroyMatch != null) {
                robot.mouseMove(destroyMatch.screenX(), destroyMatch.screenY());
            } else {
                robot.mouseMove(390, 195);
            }
        } else {
            robot.mouseMove(390, 195);
        }
        robot.safeDelay(500);
        robot.ensureRunning();
        robot.click(InputEvent.BUTTON1_DOWN_MASK);
        robot.safeDelay(500);

        robot.ensureRunning();
        tapKey(KeyEvent.VK_4, 50);
        Future<Integer> coffeeEdge = checkCoffeeEdge();
        Future<Boolean> coffeeContains = coffeeStatus();
        if (hit < 2) {
            robot.safeDelay(Math.max(1L, Math.round(timePerHit * 1_000.0)));
        } else if (lastDestroyTime == 0 || hit == 2) {
            detectLastDestroyTime(hit);
        } else {
            System.out.println("使用检测最后一次砸箱子时间" + lastDestroyTime
                    + "/" + LocalDateTime.now());
            robot.safeDelay(lastDestroyTime * 1_000L);
        }

        robot.mouseMove(1024 / 2, 768 / 2);
        robot.safeDelay(200);
        robot.ensureRunning();
        robot.click(InputEvent.BUTTON1_DOWN_MASK);
        robot.safeDelay(50);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.safeDelay(300);
        robot.ensureRunning();

        int currentCoffeeEdge = coffeeEdge.get();
        for (int retry = 0; retry < 2; retry++) {
            if (previousCoffeeEdge != 0) {
                int difference = Math.abs(currentCoffeeEdge - previousCoffeeEdge);
                if (difference > 8) {
                    System.out.println("错误，两次咖啡因差值检测超出5个像素");
                    currentCoffeeEdge = checkCoffeeEdge().get();
                } else {
                    break;
                }
            }
        }

        tapKey(KeyEvent.VK_1, 50);
        robot.safeDelay(500);
        int nextHitIndex = 0;
        boolean shouldEatCoffee = (previousCoffeeEdge != 0
                && currentCoffeeEdge < previousCoffeeEdge) || !coffeeContains.get();
        if (shouldEatCoffee) {
            nextHitIndex = hit + 1;
        }
        if (shouldEatCoffee && coffeeCheck) {
            tapKey(KeyEvent.VK_4, 50);
            robot.safeDelay(500);
            moveMouseForInfoWithRetry(0);
            robot.safeDelay(500);

            boolean areaChanged = coffeeDoubleCheckAreaChanged();
            System.out.println("咖啡识别区域在10秒内"
                    + (areaChanged ? "累计至少发生2次变化" : "累计变化不足2次"));

            if (areaChanged) {
                coffeeCheck = false;
                robot.ensureRunning();
                robot.tabSwitch();
                robot.safeDelay(500);
                tapKey(KeyEvent.VK_C, 300);
                eatCoffee();
                robot.ensureRunning();
                robot.tabSwitch();
            }else {
                System.out.println("咖啡含量存在误判！！！！！！！！！！！！！！" + LocalDateTime.now());
            }

        }
        return new DestroyResult(currentCoffeeEdge, nextHitIndex);
    }
    private boolean coffeeDoubleCheckAreaChanged() throws InterruptedException {
        Rectangle area = new Rectangle(410, 522, 445 - 410, 545 - 522);
        BufferedImage previousImage = robot.createScreenCapture(area);
        int changeCount = 0;
        for (int sample = 0; sample < 5; sample++) {
            robot.safeDelay(2_000);
            BufferedImage currentImage = robot.createScreenCapture(area);
            if (!imagesAreEqual(previousImage, currentImage)) {
                changeCount++;
            }
            previousImage = currentImage;
        }
        return changeCount >= 2;
    }

    public void moveMouseCoffeeInfo() {
        robot.delay(300);
        robot.mouseMove(345, 471);
        robot.delay(10);
        robot.mouseMove(344, 470);
        robot.delay(10);
        robot.mouseMove(343, 471);
        robot.delay(10);
        robot.mouseMove(342, 470);
        robot.delay(10);
        robot.mouseMove(341, 471);
        robot.delay(10);
        robot.mouseMove(340, 471);
        robot.delay(1500);
    }
    public void moveMouseForInfoWithRetry(int retryCount) {
        // 移动鼠标
        moveMouseCoffeeInfo();
        // 检测移动后，信息框是否正确打开
        Color dazi = robot.getPixelColor(351, 515);
        boolean needRetry = dazi.getGreen() < 40;
        if (needRetry) {
            if (retryCount >= 5) {
                System.out.println("已达到最大重试次数，停止移动鼠标");
                return;
            }
            System.out.println("信息框未正确打开，第 "
                    + retryCount
                    + " 次重试");
            moveMouseForInfoWithRetry(retryCount + 1);
        }
    }

    private Future<Integer> checkCoffeeEdge() throws InterruptedException {
        Future<Integer> result = executor.submit(() -> {
            long startedAt = System.currentTimeMillis();
            final int startX = 182;
            final int endX = 319;
            int y = 464;
            final int width = endX - startX + 1;
            robot.mouseMove(0, 0);
            robot.safeDelay(50);
            tapKey(KeyEvent.VK_4, 50);
            robot.safeDelay(500);
            BufferedImage image = robot.createScreenCapture(new Rectangle(startX, y, width, 1));
            int left = 0;
            int right = width - 1;
            while (left < right) {
                int middle = (left + right) >>> 1;
                int rgb = image.getRGB(middle, 0);
                int green = (rgb >>> 8) & 0xFF;
                int red = (rgb >>> 16) & 0xFF;
                if (green >= 100 || red >= 100) {
                    left = middle + 1;
                    y = 463;
                } else {
                    right = middle;
                }
            }
            int boundaryX = startX + left - 1;
            System.out.println(boundaryX + " 是绿色边界值，检测时间：" + LocalDateTime.now());
            System.out.println("检测咖啡因含量耗时："
                    + (System.currentTimeMillis() - startedAt) + "ms");
            return boundaryX;
        });
        robot.safeDelay(1_000);
        return result;
    }

    private Future<Boolean> coffeeStatus() {
        return executor.submit(() -> {
            Color color = robot.getDelayedPixelColor(183, 468);
            if (color.getGreen() > 180 && color.getRed() > 180 && color.getBlue() > 180) {
                System.out.println("检测到咖啡粉已耗尽" + LocalDateTime.now());
                return false;
            }
            return true;
        });
    }

    private void eatCoffee() throws InterruptedException {
        if (!autoCaffeine) {
            return;
        }
        System.out.println(LocalDateTime.now() + "吃一次咖啡");
        robot.safeDelay(1_000);
        robot.ensureRunning();
        tapKey(KeyEvent.VK_8, 50);
        robot.safeDelay(5_000);
        robot.ensureRunning();
        tapKey(KeyEvent.VK_8, 50);
        robot.safeDelay(5_000);
        robot.ensureRunning();
        tapKey(KeyEvent.VK_8, 50);
        robot.safeDelay(5_000);
        robot.ensureRunning();
        tapKey(KeyEvent.VK_3, 50);
        robot.safeDelay(3_000);
        tapKey(KeyEvent.VK_3, 50);
        robot.safeDelay(3_000);
    }

    private ScreenTemplateMatch findDestroyButton(StorageItemMatch storagePosition)
            throws InterruptedException {
        if (storagePosition == null) {
            return null;
        }
        try {
            ScreenTemplateMatch match = detectionService.detectDestroyOnce(
                    storagePosition.screenX(), storagePosition.screenY());
            if (match == null) {
                System.out.println("未识别到摧毁菜单");
            } else {
                System.out.printf(
                        "DESTROY -> similarity=%.3f x=%d y=%d%n",
                        match.similarity(), match.screenX(), match.screenY());
            }
            return match;
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("识别摧毁菜单失败: " + e.getMessage());
            return null;
        }
    }

    public Optional<StorageItemMatch> findCaseOrFridge(String type)
            throws InterruptedException {
        inventoryService.ensureItemPanelPosition();
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

    private void detectLastDestroyTime(int hit) throws InterruptedException {
        long startedAt = System.currentTimeMillis();
        Rectangle monitoredArea = new Rectangle(128, 317, 30, 7);
        BufferedImage previousImage = robot.createScreenCapture(monitoredArea);
        long comparisonStartedAt = System.nanoTime();
        long limit = TimeUnit.MILLISECONDS.toNanos(
                Math.max(1L, Math.round(timePerHit * 1_000.0)));
        for (int i = 0; i < 20; i++) {
            long elapsed = System.nanoTime() - comparisonStartedAt;
            if (elapsed >= limit) {
                System.out.println("检测区域等待达到上限，继续运行" + LocalDateTime.now());
                break;
            }
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(limit - elapsed);
            robot.safeDelay(Math.min(2_000L, remainingMillis));
            if (System.nanoTime() - comparisonStartedAt >= limit) {
                System.out.println("检测区域等待达到32秒上限，停止运行" + LocalDateTime.now());
                break;
            }
            BufferedImage currentImage = robot.createScreenCapture(monitoredArea);
            if (imagesAreEqual(previousImage, currentImage)) {
                System.out.println("检测区域已稳定，停止运行" + LocalDateTime.now());
                if (lastDestroyTime == 0) {
                    lastDestroyTime = (int) (Math.floor(
                            System.currentTimeMillis() - startedAt) / 1_000) - 2;
                    System.out.println("最后一次砸箱子耗时" + (System.currentTimeMillis() - startedAt) / 1_000 + "/" + LocalDateTime.now());
                }
                if (hit != 3) {
                    shouldSkipFinalDestroy = true;
                }
                System.out.println("最后一次砸箱子时间已设置为" + lastDestroyTime
                        + "/" + LocalDateTime.now());
                break;
            }
            System.out.println("检测区域仍在变化，继续等待" + LocalDateTime.now());
            previousImage = currentImage;
        }
    }

    private static boolean imagesAreEqual(BufferedImage first, BufferedImage second) {
        if (first == null || second == null
                || first.getWidth() != second.getWidth()
                || first.getHeight() != second.getHeight()) {
            return false;
        }
        int pixelCount = first.getWidth() * first.getHeight();
        if (first.getRaster().getDataBuffer() instanceof DataBufferInt firstBuffer
                && second.getRaster().getDataBuffer() instanceof DataBufferInt secondBuffer
                && firstBuffer.getNumBanks() == 1
                && secondBuffer.getNumBanks() == 1
                && firstBuffer.getSize() == pixelCount
                && secondBuffer.getSize() == pixelCount) {
            return Arrays.equals(firstBuffer.getData(), secondBuffer.getData());
        }
        int[] firstPixels = first.getRGB(
                0, 0, first.getWidth(), first.getHeight(), null, 0, first.getWidth());
        int[] secondPixels = second.getRGB(
                0, 0, second.getWidth(), second.getHeight(), null, 0, second.getWidth());
        return Arrays.equals(firstPixels, secondPixels);
    }

    private void tapKey(int keyCode, long heldMillis) throws InterruptedException {
        robot.keyPress(keyCode);
        robot.safeDelay(heldMillis);
        robot.keyRelease(keyCode);
    }

    private record DestroyResult(int lastCoffeeEdge, int nextHitIndex) {
    }
}
