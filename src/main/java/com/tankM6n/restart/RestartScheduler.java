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

/**
 * 服务器重启期间的训练调度器。
 * <p>流程：每分钟检查时间 → 在目标小时的第 2 分钟暂停训练并点击游戏界面
 * → 等待指定分钟数 → 自动恢复训练，同时将重启时间推进到下一次。
 * <p>定时等待由后台线程负责；暂停、游戏点击、恢复和推进时间通过 uiDispatcher 执行。
 * stop() 会使旧任务失效，避免用户停止后，已排队的回调又把训练启动起来。
 */
public final class RestartScheduler implements AutoCloseable {
    // 每次检查时读取最新配置，而不是只记住创建调度器时的重启小时。
    private final Supplier<String> restartHour;
    private final Runnable stopTraining;
    private final Runnable resumeTraining;
    private final Runnable advanceRestartHour;
    private final Consumer<Runnable> uiDispatcher;
    private final int restartDelayMinutes;

    // 单线程定时器：负责周期检查和延迟恢复，不在这里长时间阻塞等待。
    private ScheduledExecutorService executor;
    // 两个句柄分别用于取消周期检查和本次重启后的延迟恢复。
    private ScheduledFuture<?> checkTask;
    private ScheduledFuture<?> resumeTask;
    // 定时线程和 UI 线程都会读取这些状态，volatile 保证修改对其他线程可见。
    private volatile boolean enabled;
    // 每次 stop() 递增，相当于任务的“批次号”；旧批次即使还在排队，也不能继续执行。
    private volatile long generation;

    /**
     * @param restartHour 提供当前重启小时的字符串，合法范围为 0～23
     * @param stopTraining 暂停训练的动作；应保留调度器，以便稍后自动恢复
     * @param resumeTraining 恢复训练的动作
     * @param advanceRestartHour 更新下一次重启小时的动作，具体更新规则由调用方决定
     * @param uiDispatcher 将动作交给 UI 线程执行，主程序传入 Platform::runLater
     * @param restartDelayMinutes 游戏点击完成后，等待多少分钟再恢复训练
     */
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

    /** 启用检查；重复调用时复用现有周期任务，停止后也可以重新启动。 */
    public synchronized void start() {
        enabled = true;
        // 回调始终携带创建时的批次号，后续通过 isActive 判断它是否已经过期。
        long activeGeneration = generation;
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadScheduledExecutor();
        }
        if (checkTask != null && !checkTask.isCancelled() && !checkTask.isDone()) {
            return;
        }

        // 立即检查一次，之后每分钟检查一次；使用本机当前时间。
        checkTask = executor.scheduleAtFixedRate(() -> {
            try {
                if (!isActive(activeGeneration)) {
                    return;
                }
                LocalTime now = LocalTime.now();
                Integer targetHour = parseRestartHour(restartHour.get());
                // 触发点是“目标小时的第 2 分钟”；配置无效时跳过本次检查。
                if (targetHour != null && now.getHour() == targetHour && now.getMinute() == 2) {
                    uiDispatcher.accept(() -> handleRestart(activeGeneration));
                }
            } catch (RuntimeException e) {
                // 周期任务若将异常抛出，后续检查会被取消，因此在这里记录并处理异常。
                if (isActive(activeGeneration)) {
                    System.err.println("服务器重启定时检查失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    /** 由 UI 调度器调用：暂停训练、操作游戏界面，再安排延迟恢复。 */
    private void handleRestart(long activeGeneration) {
        // 从后台提交到 UI 实际执行之间，用户可能已经停止了调度器。
        if (!isActive(activeGeneration)) {
            return;
        }

        stopTraining.run();
        try {
            // 按固定屏幕坐标依次左键点击两处；坐标依赖当前游戏界面布局。
            GameRobot robot = new GameRobot();
            robot.mouseMove(513, 403);
            robot.delay(300);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(50);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            // 等待第一次点击后的界面切换，再执行第二次点击。
            robot.delay(3000);
            robot.mouseMove(117, 299);
            robot.delay(300);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(50);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        } catch (Exception e) {
            System.err.println("服务器重启 Robot 创建失败: " + e.getMessage());
            return;
        }

        // 游戏操作期间可能发生 stop()，提交恢复任务前再次检查状态和线程池。
        ScheduledExecutorService currentExecutor = executor;
        if (!isActive(activeGeneration)
                || currentExecutor == null
                || currentExecutor.isShutdown()) {
            return;
        }
        try {
            // 只登记一个延迟任务，等待期间不占用 UI 线程。
            resumeTask = currentExecutor.schedule(() -> {
                if (isActive(activeGeneration)) {
                    uiDispatcher.accept(() -> {
                        // 入队时有效，不代表执行时仍有效；防止排队期间被用户停止。
                        if (isActive(activeGeneration)) {
                            resumeTraining.run();
                        }
                    });
                }
            }, restartDelayMinutes, TimeUnit.MINUTES);
        } catch (RejectedExecutionException e) {
            // 状态检查后线程池仍可能被 stop() 关闭，此时提交任务会遭到拒绝。
            if (isActive(activeGeneration)) {
                System.err.println("提交服务器重启恢复任务失败: " + e.getMessage());
            }
            return;
        }

        // 恢复任务提交成功后就推进重启小时，无需等到训练真正恢复。
        advanceRestartHour.run();
    }

    /** 必须同时处于启用状态、且属于当前批次，才允许回调继续执行。 */
    private boolean isActive(long activeGeneration) {
        return enabled && generation == activeGeneration;
    }

    /** 停用调度器，取消定时任务并释放线程池；与 start() 互斥，避免同时修改生命周期。 */
    public synchronized void stop() {
        enabled = false;
        // 先让旧回调失效：cancel 无法撤回已经交给 UI 线程排队的动作。
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

    /** 将配置解析为 24 小时制小时；空值、非整数或超出范围时返回 null，表示不触发。 */
    static Integer parseRestartHour(String value) {
        try {
            int hour = Integer.parseInt(value);
            return hour >= 0 && hour <= 23 ? hour : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 统一资源释放入口，效果与 stop() 相同。 */
    @Override
    public void close() {
        stop();
    }
}
