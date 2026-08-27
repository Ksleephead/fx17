// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
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


            robot.delay(1 * 1000);
            for (ItemMatch rice : rices) {
                ItemMatch pan = null;
                ItemMatch water = null;
                if (pans.size() > 0 || waters.size() > 0){
                    pan = pans.get(0);
                    water = waters.get(0);
                }else {
                    System.out.println("未检测到平底锅/水，结束");
                    return;
                }
                //一个米饭坐标用两次
                cook(rice, pan, water);
//                cook(rice, pan, water);

            }
        } catch (AWTException e) {
            System.err.println("创建附近物品操作 Robot 失败: " + e.getMessage());
        } catch (RuntimeException e) {
            e.printStackTrace();
            System.err.println("附近物品 Robot 线程执行失败: " + e.getMessage());
        }
    }

    private void cook(ItemMatch rice, ItemMatch pan, ItemMatch water) {
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
        List<ScreenTemplateMatch> panPositions = findPanPositions();
        robot.delay(500);

        for (ScreenTemplateMatch panPosition : panPositions) {
            int pingdiguoX = panPosition.screenX();
            int pingdiguoY = panPosition.screenY();

            //点进烹饪界面
            robot.mouseMove(pingdiguoX , pingdiguoY + 40);
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

            //点击烹饪
            robot.delay(500);
            robot.mouseMove(964 , 732);
            robot.delay(500);
            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            robot.delay(50);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

            robot.delay(500);


            boolean riceRst = moveRice(rice, pingdiguoX, pingdiguoY, 0);
            if (!riceRst){
                //关闭tab
                robot.keyPress(KeyEvent.VK_TAB);
                robot.delay(50);
                robot.keyRelease(KeyEvent.VK_TAB);
                //下一个米饭
                return;
            }
            robot.delay(500);
            moveWater(water, pingdiguoX, pingdiguoY , 0);

            robot.delay(500);

            robot.mouseMove(pingdiguoX + 255, pingdiguoY + 36);
            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            robot.delay(50);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
        }

//        //结束黑暗料理
//        robot.delay(1000);
//        robot.mouseMove(972  , 403);
//        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
//        robot.delay(50);
//        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
//
//        //拿取黑暗料理
//        robot.delay(1000);
//        robot.mouseMove(800  , 393);
//        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
//        robot.delay(50);
//        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

        //关闭tab
        robot.keyPress(KeyEvent.VK_TAB);
        robot.delay(50);
        robot.keyRelease(KeyEvent.VK_TAB);
    }

    private boolean moveWater(ItemMatch water, int pingdiguoX, int pingdiguoY , int tryTimes) {
        int maxTryTimes = 3;
        int waterX = water.screenX();
        int waterY = water.screenY();

        robot.mouseMove(waterX , waterY);
        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
        robot.delay(200);
        robot.mouseMove(waterX+1 , waterY+1);
        robot.delay(500);
        robot.mouseMove(pingdiguoX + 45, pingdiguoY + 45);
        robot.delay(500);
        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
        //执行校验，是否正确托过去了
        Color riceColor = getPixelColor(pingdiguoX + 45, pingdiguoY + 45);
        if (riceColor.getBlue()  <= 100){
            if (tryTimes < maxTryTimes) {
                tryTimes++;
                //递归调用本身
                return moveWater(water, pingdiguoX, pingdiguoY , tryTimes);
            }else {
                //填充失败
                return false;
            }
        }else {
            //填充成功
            return true;
        }
    }

    private boolean moveRice(ItemMatch rice , int pingdiguoX, int pingdiguoY , int tryTimes) {
        int riceX = rice.screenX();
        int riceY = rice.screenY();
        int maxTryTimes = 3;
        robot.mouseMove(riceX, riceY);
        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
        robot.delay(200);
        robot.mouseMove(riceX +1 , riceY +1);
        robot.delay(500);
        robot.mouseMove(pingdiguoX, pingdiguoY + 45);
        robot.delay(500);
        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
        //执行校验，是否正确托过去了
        Color riceColor = getPixelColor(pingdiguoX, pingdiguoY + 45);
        if (riceColor.getRed() <= 150 && riceColor.getBlue()  <= 150 && riceColor.getGreen() <= 150 ){
            if (tryTimes < maxTryTimes) {
                tryTimes++;
                //递归调用本身
                return moveRice(rice, pingdiguoX, pingdiguoY , tryTimes);
            }else {
                //填充失败
                return false;
            }
        }else {
            //填充成功
            return true;
        }
    }

    private List<ScreenTemplateMatch> findPanPositions() {
        List<ScreenTemplateMatch> panPositions = null;
        System.out.println("识别平底锅");
        try {
            if (panPositionDetector == null) {
                // 左上角 (699,40)，右下边界扩大到 (740,340)，尺寸为 41×300。  490
                panPositionDetector = new RegionTemplateDetector(
                        // 确保位于 y=312 的 35×19 模板能够完整参与比较。
                        new Rectangle(699, 40, 41, 450),
                        "classpath:/image/pingdiguo.png",
                        0.900);
            }

            // 每次调用本方法都会重新截图识别；返回值先保留在方法局部变量中。
            panPositions = panPositionDetector.detectOnce();
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
        return panPositions;
    }

    /** 请求线程在处理下一个物品前结束。 */
    public void requestStop() {
        interrupt();
    }
    public Color getPixelColor(int x, int y) {
        long end = System.currentTimeMillis() + 300;
        while (System.currentTimeMillis() < end) {
            if ( Thread.currentThread().isInterrupted()) {
                return Color.BLACK;
            }
            try {
                Thread.sleep(Math.min(50, end - System.currentTimeMillis()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Color.BLACK;
            }
        }
        Color pixelColor = robot.getPixelColor(x, y);
        return pixelColor;
    }
}
