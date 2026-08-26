// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 接收一轮附近物品识别结果，并在独立线程中执行后续 Robot 操作。
 * 当前只搭建通用遍历入口，尚未加入鼠标移动或点击行为。
 */
public final class NearbyItemRobotThread extends Thread {
    private final List<ItemMatch> itemMatches;
    private Robot robot;
    private RegionTemplateDetector panPositionDetector;

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
            Map<ItemType, List<ItemMatch>> collect = itemMatches.stream()
                    .collect(Collectors.groupingBy(ItemMatch::type));
            List<ItemMatch> stoneFires = collect.get(ItemType.STONE_FIRE);
            List<ItemMatch> pans = collect.get(ItemType.PAN);
            List<ItemMatch> rices = collect.get(ItemType.RICE);
            List<ItemMatch> waters = collect.get(ItemType.WATER);
            robot = new Robot();

            //关闭tab
            robot.keyPress(KeyEvent.VK_TAB);
            robot.delay(50);
            robot.keyRelease(KeyEvent.VK_TAB);
            // 开始做饭

            //整理背包
            robot.mouseMove(232 , 69);
            robot.delay(200);
            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            robot.delay(50);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

            robot.delay(1 * 1000);

            for (ItemMatch pan : pans) {
                int x = pan.screenX();
                int y = pan.screenY();
                //打开tab
                robot.keyPress(KeyEvent.VK_TAB);
                robot.delay(50);
                robot.keyRelease(KeyEvent.VK_TAB);

                robot.delay(500);
                robot.mouseMove(x , y);
                robot.delay(200);
                robot.mousePress(MouseEvent.BUTTON3_DOWN_MASK);
                robot.delay(50);
                robot.mouseRelease(MouseEvent.BUTTON3_DOWN_MASK);

                //移动到烹饪食品上面
                robot.delay(500);
                robot.mouseMove(x + 20 , y + 80);
                robot.delay(500);
                robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                robot.delay(50);
                robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

                robot.delay(500);
                findPanPositioon();


                robot.mouseMove(816 , 247);
                robot.delay(500);
                robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                robot.delay(50);
                robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

                //选择米饭
                robot.delay(500);
                robot.mouseMove(847 , 586);
                robot.delay(500);
                robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                robot.delay(50);
                robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

                robot.delay(500);
                robot.mouseMove(964 , 732);
                robot.delay(500);
                robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                robot.delay(50);
                robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

                robot.delay(500);

                if (rices.size() == 0){
                    break;
                }
                int riceX = rices.get(0).screenX();
                int riceY = rices.get(0).screenY();
                rices.remove(0);
                robot.mouseMove(riceX , riceY);
                robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                robot.delay(50);
                robot.mouseMove(720 , 223);
                robot.delay(300);
                robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

                robot.delay(500);


                int waterX = waters.get(0).screenX();
                int waterY = waters.get(0).screenY();


                robot.mouseMove(waterX , waterY);
                robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                robot.delay(50);
                robot.mouseMove(762 , 223);
                robot.delay(300);
                robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

                robot.delay(500);

                robot.mouseMove(972  , 221);
                robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                robot.delay(50);
                robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

                //关闭tab
                robot.keyPress(KeyEvent.VK_TAB);
                robot.delay(50);
                robot.keyRelease(KeyEvent.VK_TAB);

            }
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

    private void findPanPositioon() {
        System.out.println("识别平底锅");
        try {
            if (panPositionDetector == null) {
                // 左上角 (699,40)，右下角 (740,322)，所以尺寸为 41×282。
                panPositionDetector = new RegionTemplateDetector(
                        new Rectangle(699, 40, 41, 282),
                        "classpath:/image/pingdiguo.png",
                        0.800);
            }

            // 每次调用本方法都会重新截图识别；返回值先保留在方法局部变量中。
            List<ScreenTemplateMatch> panPositions = panPositionDetector.detectOnce();
            for (ScreenTemplateMatch panPosition : panPositions) {
                System.out.printf(
                        "PAN_POSITION -> similarity=%.3f x=%d y=%d%n",
                        panPosition.similarity(),
                        panPosition.screenX(),
                        panPosition.screenY());
            }
            if (panPositions.size() == 0){
                System.out.println("未检测到");
            }
        } catch (Exception e) {
            System.err.println("识别平底锅位置失败: " + e.getMessage());
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
