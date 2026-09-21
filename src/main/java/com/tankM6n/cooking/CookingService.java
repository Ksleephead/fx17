// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.cooking;

import com.tankM6n.game.GameRobot;
import com.tankM6n.nearby.DetectionResult;
import com.tankM6n.nearby.ItemMatch;
import com.tankM6n.nearby.ItemType;
import com.tankM6n.nearby.NearbyItemDetector;
import com.tankM6n.nearby.NearbyItemDetectorConfig;
import com.tankM6n.nearby.SlotSimilarity;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/** Owns the complete lifecycle of a multi-round cooking request. */
public final class CookingService implements AutoCloseable {
    private NearbyItemDetector nearbyItemDetector;
    private volatile CookingWorker currentWorker;
    private volatile Thread controllerThread;

    public synchronized void start(int cookCount, String cookType) {
        if (controllerThread != null && controllerThread.isAlive()) {
            System.out.println("烤玉米任务正在运行");
            return;
        }

        Thread task = new Thread(
                () -> runCooking(Math.max(0, cookCount), cookType),
                "scum-cook-controller");
        controllerThread = task;
        task.start();
    }

    private void runCooking(int cookCount, String cookType) {
        try {
            prepareCookingPanels();
            for (int i = 0; i < cookCount && !Thread.currentThread().isInterrupted(); i++) {
                System.out.println(i + "    cornCookCount    " + cookCount);
                List<ItemMatch> matches = detectNearbyItemsOnce();
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (!matches.isEmpty()) {
                    CookingWorker worker = new CookingWorker(matches, cookType);
                    currentWorker = worker;
                    worker.start();
                    try {
                        worker.join();
                    } catch (InterruptedException e) {
                        worker.requestStop();
                        Thread.currentThread().interrupt();
                        return;
                    } finally {
                        if (currentWorker == worker) {
                            currentWorker = null;
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted()) {
                System.err.println("做饭任务执行失败: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            synchronized (this) {
                if (controllerThread == Thread.currentThread()) {
                    controllerThread = null;
                }
            }
        }
    }

    /** 保留原有打开 Tab、切换面板和点击折叠按钮的操作顺序与延时。 */
    private void prepareCookingPanels() {
        try {
            GameRobot robot = new GameRobot();
            robot.keyPress(KeyEvent.VK_TAB);
            robot.delayInterruptibly(50);
            robot.keyRelease(KeyEvent.VK_TAB);
            robot.delayInterruptibly(500);

            robot.keyPress(KeyEvent.VK_1);
            robot.delayInterruptibly(50);
            robot.keyRelease(KeyEvent.VK_1);
            robot.delayInterruptibly(500);
            if (robot.getPixelColor(330, 58).getRed() > 180) {
                robot.mouseMove(330, 58);
                robot.delayInterruptibly(200);
                robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                robot.delayInterruptibly(50);
                robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
                robot.delayInterruptibly(500);
            }

            robot.keyPress(KeyEvent.VK_2);
            robot.delayInterruptibly(50);
            robot.keyRelease(KeyEvent.VK_2);
            robot.delayInterruptibly(300);
            robot.mouseMove(965, 24);
            robot.delayInterruptibly(300);
            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            robot.delayInterruptibly(50);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
        } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted()) {
                System.err.println("初始化做饭面板失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private synchronized List<ItemMatch> detectNearbyItemsOnce() {
        try {
            if (nearbyItemDetector == null) {
                NearbyItemDetectorConfig config = NearbyItemDetectorConfig.load(
                        Path.of("nearby-item-detector.properties"));
                nearbyItemDetector = new NearbyItemDetector(config);
            }

            DetectionResult result = nearbyItemDetector.detectDetailedOnce();
            EnumSet<ItemType> detectedTypes = EnumSet.noneOf(ItemType.class);
            for (SlotSimilarity slot : result.slotSimilarities()) {
                if (slot.detectedType() != null) {
                    detectedTypes.add(slot.detectedType());
                }
                System.out.printf(
                        Locale.ROOT,
                        "SLOT -> row=%d col=%d x=%d y=%d "
                                + "panSimilarity=%.3f stoneFireSimilarity=%.3f "
                                + "riceSimilarity=%.3f waterSimilarity=%.3f "
                                + "cornSimilarity=%.3f fishSimilarity=%.3f detected=%s%n",
                        slot.row(), slot.col(), slot.screenX(), slot.screenY(),
                        slot.similarity(ItemType.PAN),
                        slot.similarity(ItemType.STONE_FIRE),
                        slot.similarity(ItemType.RICE),
                        slot.similarity(ItemType.WATER),
                        slot.similarity(ItemType.CORN),
                        slot.similarity(ItemType.FISH),
                        slot.detectedType() == null ? "NONE" : slot.detectedType());
            }
            for (ItemType type : ItemType.values()) {
                if (!detectedTypes.contains(type)) {
                    System.out.printf("%s -> NOT_DETECTED%n", type);
                }
            }
            return result.matches();
        } catch (Exception e) {
            System.err.println("附近物品识别失败: " + e.getMessage());
            return List.of();
        }
    }

    /** Stops the outer loop and the current Robot worker. */
    public void stop() {
        Thread task = controllerThread;
        if (task != null) {
            task.interrupt();
        }
        stopCurrentWorker();
    }

    /** Compatibility hook for the old training-stop behavior. */
    public void stopCurrentWorker() {
        CookingWorker worker = currentWorker;
        if (worker != null) {
            worker.requestStop();
        }
    }

    public boolean isRunning() {
        Thread task = controllerThread;
        return task != null && task.isAlive();
    }

    @Override
    public void close() {
        stop();
    }
}
