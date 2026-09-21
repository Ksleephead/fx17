// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.restart;

import com.tankM6n.game.GameRobot;

import java.awt.event.InputEvent;
import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns server-restart polling, reconnect delay and generation-based cancellation. */
public final class RestartScheduler implements AutoCloseable {
    private final Supplier<String> restartHour;
    private final Runnable stopTraining;
    private final Runnable resumeTraining;
    private final Runnable advanceRestartHour;
    private final Consumer<Runnable> uiDispatcher;
    private final int restartDelayMinutes;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> checkTask;
    private ScheduledFuture<?> resumeTask;
    private volatile boolean enabled;
    private volatile long generation;

    public RestartScheduler(
            Supplier<String> restartHour,
            Runnable stopTraining,
            Runnable resumeTraining,
            Runnable advanceRestartHour,
            Consumer<Runnable> uiDispatcher,
            int restartDelayMinutes) {
        this.restartHour = restartHour;
        this.stopTraining = stopTraining;
        this.resumeTraining = resumeTraining;
        this.advanceRestartHour = advanceRestartHour;
        this.uiDispatcher = uiDispatcher;
        this.restartDelayMinutes = restartDelayMinutes;
    }

    public synchronized void start() {
        enabled = true;
        long activeGeneration = generation;
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadScheduledExecutor();
        }
        if (checkTask != null && !checkTask.isCancelled() && !checkTask.isDone()) {
            return;
        }

        checkTask = executor.scheduleAtFixedRate(() -> {
            try {
                if (!isActive(activeGeneration)) {
                    return;
                }
                LocalTime now = LocalTime.now();
                Integer targetHour = parseRestartHour(restartHour.get());
                if (targetHour != null && now.getHour() == targetHour && now.getMinute() == 2) {
                    uiDispatcher.accept(() -> handleRestart(activeGeneration));
                }
            } catch (RuntimeException e) {
                if (isActive(activeGeneration)) {
                    System.err.println("服务器重启定时检查失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    private void handleRestart(long activeGeneration) {
        if (!isActive(activeGeneration)) {
            return;
        }

        stopTraining.run();
        try {
            GameRobot robot = new GameRobot();
            robot.mouseMove(513, 403);
            robot.delay(300);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(50);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.delay(3000);
            robot.mouseMove(119, 396);
            robot.delay(300);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(50);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        } catch (Exception e) {
            System.err.println("服务器重启 Robot 创建失败: " + e.getMessage());
            return;
        }

        ScheduledExecutorService currentExecutor = executor;
        if (!isActive(activeGeneration)
                || currentExecutor == null
                || currentExecutor.isShutdown()) {
            return;
        }
        try {
            resumeTask = currentExecutor.schedule(() -> {
                if (isActive(activeGeneration)) {
                    uiDispatcher.accept(() -> {
                        if (isActive(activeGeneration)) {
                            resumeTraining.run();
                        }
                    });
                }
            }, restartDelayMinutes, TimeUnit.MINUTES);
        } catch (RejectedExecutionException e) {
            if (isActive(activeGeneration)) {
                System.err.println("提交服务器重启恢复任务失败: " + e.getMessage());
            }
            return;
        }

        advanceRestartHour.run();
    }

    private boolean isActive(long activeGeneration) {
        return enabled && generation == activeGeneration;
    }

    public synchronized void stop() {
        enabled = false;
        generation++;
        if (checkTask != null) {
            checkTask.cancel(true);
            checkTask = null;
        }
        if (resumeTask != null) {
            resumeTask.cancel(true);
            resumeTask = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    static Integer parseRestartHour(String value) {
        try {
            int hour = Integer.parseInt(value);
            return hour >= 0 && hour <= 23 ? hour : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void close() {
        stop();
    }
}
