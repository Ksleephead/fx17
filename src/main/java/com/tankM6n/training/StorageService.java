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
import java.util.Objects;
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
            boolean autoCaffeine) {
        this.robot = robot;
        this.detectionService = detectionService;
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
        this.executor = executor;
        this.timePerHit = timePerHit;
        this.autoCaffeine = autoCaffeine;
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
        Optional<StorageItemMatch> position = findCaseOrFridge("case");
        if (position.isPresent()) {
            storagePosition = position.get();
            robot.mouseMove(storagePosition.screenX(), storagePosition.screenY());
        }
        robot.safeDelay(500);
        robot.ensureRunning();
        robot.click(InputEvent.BUTTON3_DOWN_MASK);
        robot.safeDelay(500);

        ScreenTemplateMatch destroyMatch = findDestroyButton(storagePosition);
        if (destroyMatch != null) {
            robot.mouseMove(destroyMatch.screenX(), destroyMatch.screenY());
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
            System.out.println("咖啡提示框检测结果: " + areaChanged);

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
    /**
     * 先按提示框内的红绿文字决定是否需要等待，再比较绿色文字的变化。
     * 无绿字（仅红字、无红绿字或未找到提示框）立即返回 true；有绿字则在
     * 10 秒内每 2 秒采样一次，相邻采样累计至少两次不同才返回 true。
     */
    public boolean coffeeDoubleCheckAreaChanged() throws InterruptedException {
        // 覆盖三张截图中提示框可能达到的宽高；下方会在此范围内寻找实际边界。
        Rectangle searchArea = new Rectangle(348, 476, 140, 120);
        CoffeeTooltipFrame previous = analyzeCoffeeTooltip(
                robot.createScreenCapture(searchArea));
        if (previous.box() != null) {
            showCoffeeTooltipCorners(searchArea, previous.box());

            previous = analyzeCoffeeTooltip(robot.createScreenCapture(searchArea));
        }

        // 无绿字的两种情况均按需求直接返回 true，红字只用于区分日志。
        if (!previous.hasGreen()) {
            System.out.println(previous.hasRed()
                    ? "咖啡提示框仅有红色异常文字"
                    : "咖啡提示框没有红色或绿色文字");
            return true;
        }
        if (previous.hasRed()) {
            System.out.println("咖啡提示框同时有红色和绿色文字，仅统计绿色文字变化");
        } else {
            System.out.println("咖啡提示框仅有绿色文字，检测其变化");
        }

        int changeCount = 0;
        for (int sample = 0; sample < 5; sample++) {
            robot.safeDelay(2_000);
            CoffeeTooltipFrame current = analyzeCoffeeTooltip(
                    robot.createScreenCapture(searchArea));
            if (!current.hasGreen()) {
                return true;
            }
            // greenText 是同尺寸的黑底图，仅保留绿字的原始 RGB 和屏幕位置。
            // 因此红字、背景和由红字导致的方框宽高变化都不会产生差异。
            // previous 每轮更新，所以比较对象是前两秒的截图，而不是最初截图。
            if (!imagesAreEqual(previous.greenText(), current.greenText())) {
                changeCount++;
            }
            previous = current;
        }
        System.out.println("绿色文字在10秒内变化次数: " + changeCount);
        return changeCount >= 2;
    }

    private void showCoffeeTooltipCorners(Rectangle searchArea, Rectangle box)
            throws InterruptedException {
        int left = searchArea.x + box.x;
        int top = searchArea.y + box.y;
        int right = left + box.width - 1;
        int bottom = top + box.height - 1;
        System.out.printf("咖啡提示框角点: 左上=(%d,%d) 右下=(%d,%d)%n",
                left, top, right, bottom);
        robot.safeDelay(500);
    }

    /** 定位提示框并提取绿字；用于比较的图像始终保持搜索区域的固定尺寸。 */
    static CoffeeTooltipFrame analyzeCoffeeTooltip(BufferedImage screenshot) {
        Rectangle box = findCoffeeTooltipBox(screenshot);
        BufferedImage greenText = new BufferedImage(
                screenshot.getWidth(), screenshot.getHeight(), BufferedImage.TYPE_INT_RGB);
        if (box == null) {
            // 边框未出现时按“没有红绿文字”处理。
            return new CoffeeTooltipFrame(null, false, false, greenText);
        }

        int greenCount = 0;
        int redCount = 0;
        // 只扫描动态边框的内部，排除顶边、左边及右下角的亮色边缘。
        for (int y = box.y + 3; y < box.y + box.height - 1; y++) {
            for (int x = box.x + 2; x < box.x + box.width - 2; x++) {
                int rgb = screenshot.getRGB(x, y);
                int red = (rgb >>> 16) & 0xff;
                int green = (rgb >>> 8) & 0xff;
                int blue = rgb & 0xff;
                // 文字需有最低亮度且主色明显强于另外两色，暗色背景不计入。
                if (green >= 40 && green - red >= 10 && green - blue >= 5) {
                    greenCount++;
                    greenText.setRGB(x, y, rgb);
                } else if (red >= 40 && red - green >= 10 && red - blue >= 8) {
                    redCount++;
                }
            }
        }
        // 至少 8 个同类像素才认为有字；参考截图中实际文字有数百个像素。
        return new CoffeeTooltipFrame(box, greenCount >= 8, redCount >= 8, greenText);
    }

    /** 在预期位置找亮色水平顶边，并用它左侧的竖边确定实际高度。 */
    private static Rectangle findCoffeeTooltipBox(BufferedImage screenshot) {
        // 搜索区域从屏幕 (348,476) 开始；顶边在其左上角附近（样本约为 y=482）。
        for (int y = 0; y < Math.min(20, screenshot.getHeight()); y++) {
            for (int x = 2; x < Math.min(16, screenshot.getWidth()); x++) {
                if (!isCoffeeTooltipBorder(screenshot.getRGB(x, y))) {
                    continue;
                }
                int right = x;
                while (right < screenshot.getWidth()
                        && isCoffeeTooltipBorder(screenshot.getRGB(right, y))) {
                    right++;
                }
                // 连续亮线至少 70 像素，避免把单个亮字或游戏背景当成顶边。
                if (right - x < 70 || x == 0) {
                    continue;
                }
                int left = x - 1;
                int bottom = y + 1;
                // 样本中顶边左侧一像素是连续竖边；其终点就是提示框底部。
                while (bottom < screenshot.getHeight()
                        && isCoffeeTooltipBorder(screenshot.getRGB(left, bottom))) {
                    bottom++;
                }
                if (bottom - y >= 25) {
                    // right/bottom 是边界后一像素，Rectangle 的宽高随提示框变化。
                    return new Rectangle(left, y, right - left, bottom - y);
                }
            }
        }
        return null;
    }

    /** 边框是亮灰色：三个通道均至少 48，最大与最小通道相差不超过 20。 */
    private static boolean isCoffeeTooltipBorder(int rgb) {
        int red = (rgb >>> 16) & 0xff;
        int green = (rgb >>> 8) & 0xff;
        int blue = rgb & 0xff;
        return red >= 48 && green >= 48 && blue >= 48
                && Math.max(red, Math.max(green, blue))
                - Math.min(red, Math.min(green, blue)) <= 20;
    }

    record CoffeeTooltipFrame(
            Rectangle box, boolean hasGreen, boolean hasRed, BufferedImage greenText) {
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
        return inventoryService.findCaseOrFridge(type);
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
                            System.currentTimeMillis() - startedAt) / 1_000) - 3;
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

    public void tapKey(int keyCode, long heldMillis) throws InterruptedException {
        robot.keyPress(keyCode);
        robot.safeDelay(heldMillis);
        robot.keyRelease(keyCode);
    }

    private record DestroyResult(int lastCoffeeEdge, int nextHitIndex) {
    }
}
