// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

import java.awt.AWTException;
import java.awt.Robot;
import java.util.List;
import java.util.Objects;

/**
 * 接收一轮附近物品识别结果，并在独立线程中执行后续 Robot 操作。
 * 当前只搭建通用遍历入口，尚未加入鼠标移动或点击行为。
 */
public final class NearbyItemRobotThread extends Thread {
    private final List<ItemMatch> itemMatches;
    private Robot robot;

    public NearbyItemRobotThread(List<ItemMatch> itemMatches) {
        super("scum-nearby-item-robot");
        this.itemMatches = List.copyOf(Objects.requireNonNull(itemMatches, "itemMatches"));
    }

    @Override
    public void run() {
        try {
            if (itemMatches.isEmpty()) {
                return;
            }

            robot = new Robot();
            for (ItemMatch itemMatch : itemMatches) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                processItem(itemMatch);
            }
        } catch (AWTException e) {
            System.err.println("创建附近物品操作 Robot 失败: " + e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("附近物品 Robot 线程执行失败: " + e.getMessage());
        }
    }

    /**
     * 单个物品的 Robot 操作入口。
     * 可根据 itemMatch.type() 区分 PAN 和 STONE_FIRE，再使用屏幕坐标操作。
     */
    private void processItem(ItemMatch itemMatch) {
        // 识别结果已由 Main 统一打印，此处只保留后续 Robot 操作入口。
        // 暂不调用 robot.mouseMove、mousePress 或 mouseRelease。
    }

    /** 请求线程在处理下一个物品前结束。 */
    public void requestStop() {
        interrupt();
    }
}
