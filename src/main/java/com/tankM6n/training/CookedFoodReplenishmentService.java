// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.training;

import com.tankM6n.nearby.ScreenTemplateMatch;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Checks the quick slots and replenishes cooked food from the fridge. */
final class CookedFoodReplenishmentService {
    private final TrainingRobot robot;
    private final TrainingDetectionService detectionService;
    private final InventoryService inventoryService;

    CookedFoodReplenishmentService(TrainingRobot robot,
                                   TrainingDetectionService detectionService,
                                   InventoryService inventoryService) {
        this.robot = robot;
        this.detectionService = detectionService;
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
    }

    void replenishIfNeeded() throws Exception {
        robot.tabSwitch();
        robot.safeDelay(200);
        robot.keyPress(KeyEvent.VK_1);
        robot.safeDelay(50);
        robot.keyRelease(KeyEvent.VK_1);
        robot.safeDelay(300);

        // 4号快捷键状态豆消失表示烤玉米耗尽；9号快捷键暂存烤鱼。
        boolean needCorn = robot.getPixelColor(838, 671).getGreen() < 150;
        boolean needFish = robot.getPixelColor(927, 758).getGreen() < 150;
        if (needCorn || needFish) {
            replenishFromFridge(needFish, needCorn);
        }
        robot.tabSwitch();
    }

    private void replenishFromFridge(boolean needFish, boolean needCorn) throws Exception {
        Optional<StorageItemMatch> position = inventoryService.findCaseOrFridge("fridge");
        if (position.isEmpty()) {
            return;
        }

        StorageItemMatch fridge = position.get();
        System.out.println("识别到冰箱位置:" + fridge.screenX() + "," + fridge.screenY());
        Color color = robot.getPixelColor(fridge.screenX() - 24, fridge.screenY() - 37);
        if (color.getRed() < 200) {
            robot.mouseMove(fridge.screenX(), fridge.screenY());
            robot.safeDelay(300);
            doubleClick();
        }

        robot.mouseMove(625, 252);
        robot.click(InputEvent.BUTTON1_DOWN_MASK);
        robot.safeDelay(1000);
        robot.mouseMove(0, 0);
        robot.safeDelay(300);

        if (needFish) {
            clickFirstMatch(detectionService.detectCookedFishOnce(), "烤鱼");
        }
        if (needCorn) {
            clickFirstMatch(detectionService.detectCookedCornOnce(), "烤玉米");
        }
    }

    private void clickFirstMatch(List<ScreenTemplateMatch> matches, String itemName)
            throws InterruptedException {
        if (matches == null || matches.isEmpty()) {
            return;
        }
        System.out.println("识别到" + itemName + "个数：" + matches.size());
        ScreenTemplateMatch match = matches.get(0);
        robot.mouseMove(match.screenX(), match.screenY());
        robot.safeDelay(500);
        doubleClick();
        robot.safeDelay(500);
    }

    private void doubleClick() throws InterruptedException {
        robot.click(InputEvent.BUTTON1_DOWN_MASK);
        robot.safeDelay(50);
        robot.click(InputEvent.BUTTON1_DOWN_MASK);
    }
}
