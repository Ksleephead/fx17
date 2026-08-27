// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 接收一轮附近物品识别结果，并在独立线程中执行后续 Robot 操作。
 * 当前只搭建通用遍历入口，尚未加入鼠标移动或点击行为。
 */
public final class cookCornThread extends Thread {
    private final List<ItemMatch> itemMatches;
    private Robot robot;
    private RegionTemplateDetector panPositionDetector;
    private ThreadPoolExecutor executor;
    private volatile List<ScreenTemplateMatch> panPositions;

    public cookCornThread(List<ItemMatch> itemMatches , ThreadPoolExecutor executor) {
        super("scum-nearby-item-robot");
        this.itemMatches = List.copyOf(Objects.requireNonNull(itemMatches, "itemMatches"));
        this.executor = executor;
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
            List<ItemMatch> corns = collect.get(ItemType.CORN);
            robot = new Robot();

            //关闭tab
            robot.keyPress(KeyEvent.VK_TAB);
            safeDelay(50);
            robot.keyRelease(KeyEvent.VK_TAB);
            // 开始做饭

            if (pans.size() < 0){
                System.out.println("未识别到锅");
                return;
            }
            if (corns.size() < 0){
                System.out.println("未识别到玉米");
                return;
            }

            safeDelay(1 * 1000);
            Iterator<ItemMatch> cornIterator = corns.iterator();
            int count = 0;
            while (cornIterator.hasNext()) {
                if (count >= 10){
                    break;
                }
                ItemMatch pan = pans.get(0);
                int x = pan.screenX();
                int y = pan.screenY();
                //打开tab
                robot.keyPress(KeyEvent.VK_TAB);
                safeDelay(50);
                robot.keyRelease(KeyEvent.VK_TAB);

                safeDelay(500);
                robot.mouseMove(x , y);
                safeDelay(200);
                robot.mousePress(MouseEvent.BUTTON3_DOWN_MASK);
                safeDelay(50);
                robot.mouseRelease(MouseEvent.BUTTON3_DOWN_MASK);

                //移动到烹饪食品上面
                safeDelay(500);
                robot.mouseMove(x + 20 , y + 80);
                safeDelay(500);
                robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                safeDelay(50);
                robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

                safeDelay(500);
                panPositions = findPanPositions();
                safeDelay(500);

                for (ScreenTemplateMatch panPosition : panPositions) {
                    int pingdiguoX = panPosition.screenX();
                    int pingdiguoY = panPosition.screenY();
                    //点进烹饪界面
                    robot.mouseMove(pingdiguoX , pingdiguoY + 40);
                    safeDelay(500);
                    robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                    safeDelay(50);
                    robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

                    //选择烤蔬菜
                    safeDelay(500);
                    robot.mouseMove(848 , 307);
                    safeDelay(500);
                    robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                    safeDelay(50);
                    robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

                    //点击烹饪
                    safeDelay(500);
                    robot.mouseMove(964 , 732);
                    safeDelay(500);
                    robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                    safeDelay(50);
                    robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

                    safeDelay(500);
                }
                safeDelay(500);
                panPositions = findPanPositions();
                safeDelay(500);
                for (ScreenTemplateMatch panPosition : panPositions) {
                    int pingdiguoX = panPosition.screenX();
                    int pingdiguoY = panPosition.screenY();

                    //偏移量
                    int[][] points = {
                            {0, 32},
                            {133, 32},
                            {176, 32},
                            {0, 75},
                            {44, 76}
                    };
                    for (int i = 0; i < points.length; i++) {
                        count++;
                        if (count > 10){
                            break;
                        }
                        ItemMatch corn = cornIterator.next();
                        int xBias = points[i][0];
                        int yBias = points[i][1];
                        moveCorn(corn, pingdiguoX, pingdiguoY, 0, xBias, yBias);
                    }
                }
                for (ScreenTemplateMatch panPosition : panPositions) {
                    int pingdiguoX = panPosition.screenX();
                    int pingdiguoY = panPosition.screenY();
                    safeDelay(500);
                    //点击烹饪
                    robot.mouseMove(pingdiguoX + 255, pingdiguoY + 36);
                    robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                    safeDelay(50);
                    robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
                }
            }
            detectCookStatus();
            for (ScreenTemplateMatch panPosition : panPositions) {
                //结束黑暗料理
                safeDelay(500);
                robot.mouseMove(panPosition.screenX() + 255  , panPosition.screenY() + 81);
                robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                safeDelay(50);
                robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
            }

            for (ScreenTemplateMatch panPosition : panPositions) {
                //拿取黑暗料理
                safeDelay(500);
                robot.mouseMove(panPosition.screenX() + 92  , panPosition.screenY() + 82);
                robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                safeDelay(50);
                robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
            }
        } catch (CancellationException | InterruptedException e) {
            // 收到停止信号后直接结束，避免跳过延时继续执行剩余 Robot 操作。
            Thread.currentThread().interrupt();
        } catch (AWTException e) {
            System.err.println("创建附近物品操作 Robot 失败: " + e.getMessage());
        } catch (RuntimeException e) {
            e.printStackTrace();
            System.err.println("附近物品 Robot 线程执行失败: " + e.getMessage());
        }
    }

    private void detectCookStatus() throws InterruptedException {
        // ===== 配置参数 =====
        int maxDepth = 15;           // 最大检测次数（比如 30 次 = 30 秒）
        int checkInterval = 1;       // 检测间隔（秒）

        // ===== 共享状态 =====
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger attemptCount = new AtomicInteger(0);   // 当前递归/检测次数
        AtomicBoolean conditionMet = new AtomicBoolean(false); // true=条件满足, false=超时

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        // ===== 递归任务 =====
        Runnable checkTask = new Runnable() {
            @Override
            public void run() {
                int currentAttempt = attemptCount.incrementAndGet();

                // ① 检查是否超过最大深度
                if (currentAttempt > maxDepth) {
                    System.out.println("已达到最大检测次数（" + maxDepth + "），停止检测");
                    conditionMet.set(false);  // 标记为"未满足条件，超时退出"
                    latch.countDown();        // 唤醒外面线程
                    return;                   // 不再调度，任务结束
                }
                try {
                    // ② 检测逻辑
                    Color color = getPixelColor(panPositions.get(0).screenX() + 17 , panPositions.get(0).screenY() + 110);
                    robot.mouseMove(panPositions.get(0).screenX() - 20 , panPositions.get(0).screenY() + 110);
                    if (color.getBlue() < 50) {
                        System.out.println("✅ 条件满足！blue=" + color.getBlue());
                        conditionMet.set(true);   // 标记条件满足
                        latch.countDown();        // 唤醒外面线程
                        return;                   // 不再调度，任务结束
                    }else {
                        System.out.println("第" + currentAttempt + "次尝试，blue值" + color.getBlue());
                    }
                    // ③ 条件未满足，继续递归调度（1 秒后执行下一次）
                    scheduler.schedule(this, checkInterval, TimeUnit.SECONDS);

                } catch (Exception e) {
                    System.err.println("❌ 检测异常：" + e.getMessage());
                    e.printStackTrace();
                    // 异常后也继续调度（你也可以选择直接停止）
                    scheduler.schedule(this, checkInterval, TimeUnit.SECONDS);
                }
            }
        };

        // ===== 启动第一次检测 =====
        System.out.println("🚀 启动检测任务...");
        scheduler.schedule(checkTask, 1, TimeUnit.SECONDS);

        // ===== 外面线程阻塞等待（相当于 wait）=====
        latch.await();  // 阻塞，直到条件满足 或 超过最大深度

        // ===== 被唤醒后，关闭线程池 =====
        scheduler.shutdown();

        // ===== 根据结果做后续处理 =====
        if (conditionMet.get()) {
            System.out.println("主线程被唤醒：条件满足，继续执行后续逻辑...");
        } else {
            System.out.println("主线程被唤醒：检测超时，执行超时处理逻辑...");
        }
    }


    private boolean moveWater(ItemMatch water, int pingdiguoX, int pingdiguoY , int tryTimes) {
        int maxTryTimes = 3;
        int waterX = water.screenX();
        int waterY = water.screenY();

        robot.mouseMove(waterX , waterY);
        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
        safeDelay(200);
        robot.mouseMove(waterX+1 , waterY+1);
        safeDelay(500);
        robot.mouseMove(pingdiguoX + 45, pingdiguoY + 45);
        safeDelay(500);
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

    private boolean moveCorn(ItemMatch corn , int pingdiguoX, int pingdiguoY , int tryTimes , int xBias , int yBias) {
        int cornX = corn.screenX();
        int cornY = corn.screenY();
        int maxTryTimes = 10;
        robot.mouseMove(cornX, cornY);
        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
        safeDelay(200);
        robot.mouseMove(cornX +1 , cornY +1);
        safeDelay(500);
        robot.mouseMove(pingdiguoX + xBias, pingdiguoY + yBias);
        safeDelay(500);
        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
        //执行校验，是否正确托过去了
        Color cornColor = getPixelColor(pingdiguoX + xBias, pingdiguoY + yBias);
        if (cornColor.getRed() <= 100 && cornColor.getGreen() <= 100 ){
            if (tryTimes < maxTryTimes) {
                tryTimes++;
                //递归调用本身
                return moveCorn(corn, pingdiguoX, pingdiguoY , tryTimes , xBias , yBias);
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
                        new Rectangle(698, 37, 41, 450),
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

    /** 可被 interrupt() 立即打断的延时。 */
    private void safeDelay(long millis) {
        long end = System.currentTimeMillis() + Math.max(0L, millis);
        while (System.currentTimeMillis() < end) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Robot thread stopped");
            }
            long remaining = end - System.currentTimeMillis();
            try {
                Thread.sleep(Math.min(100L, remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Robot thread stopped");
            }
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Robot thread stopped");
        }
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
