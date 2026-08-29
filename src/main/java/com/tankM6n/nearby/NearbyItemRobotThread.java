//// SPDX-FileCopyrightText: 2026 Ksleephead
//// SPDX-License-Identifier: GPL-3.0-only
//
//package com.tankM6n.nearby;
//
//import java.awt.*;
//import java.awt.event.KeyEvent;
//import java.awt.event.MouseEvent;
//import java.time.LocalDateTime;
//import java.util.*;
//import java.util.List;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicBoolean;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.stream.Collectors;
//
///**
// * 接收一轮附近物品识别结果，并在独立线程中执行后续 Robot 操作。
// * 当前只搭建通用遍历入口，尚未加入鼠标移动或点击行为。
// */
//public final class NearbyItemRobotThread extends Thread {
//    private final List<ItemMatch> itemMatches;
//    private Robot robot;
//    private RegionTemplateDetector panPositionDetector;
//    private volatile List<ScreenTemplateMatch> panPositions;
//
//    public NearbyItemRobotThread(List<ItemMatch> itemMatches) {
//        super("scum-nearby-item-robot");
//        this.itemMatches = List.copyOf(Objects.requireNonNull(itemMatches, "itemMatches"));
//    }
//
//    @Override
//    public void run() {
//        try {
//            if (itemMatches.isEmpty()) {
//                return;
//            }
//            Map<ItemType, List<ItemMatch>> collect = itemMatches.stream()
//                    .collect(Collectors.groupingBy(ItemMatch::type));
//            List<ItemMatch> rices = collect.get(ItemType.RICE);
//            List<ItemMatch> waters = collect.get(ItemType.WATER);
//            robot = new Robot();
//
//            //关闭tab
//            robot.keyPress(KeyEvent.VK_TAB);
//            safeDelay(50);
//            robot.keyRelease(KeyEvent.VK_TAB);
//            // 开始做饭
//            if (waters.size() == 0 || rices.size() == 0){
//                System.out.println("未检测到米饭/水，结束" + LocalDateTime.now());
//                return;
//            }
//
//            safeDelay(1 * 1000);
//
//            Iterator<ItemMatch> iterator = rices.iterator();
//            while (iterator.hasNext()){
//                int temp = -1;
//                ItemMatch water = waters.get(0);
//                //打开tab
//                robot.keyPress(KeyEvent.VK_TAB);
//                safeDelay(50);
//                robot.keyRelease(KeyEvent.VK_TAB);
//
//                safeDelay(500);
//                panPositions = findPanPositions();
//                safeDelay(500);
//
//                //每个米饭用两次，如果遇到了2/10的 直接return
//                for (int j = 0; j < 2; j++) {
//                    ItemMatch rice = iterator.next();
//                    for (ScreenTemplateMatch panPosition : panPositions) {
//                        int pingdiguoX = panPosition.screenX();
//                        int pingdiguoY = panPosition.screenY();
//
//                        //点进烹饪界面
//                        robot.mouseMove(pingdiguoX , pingdiguoY + 40);
//                        safeDelay(500);
//                        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
//                        safeDelay(50);
//                        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
//
//                        //选择米饭
//                        safeDelay(500);
//                        robot.mouseMove(847 , 586);
//                        safeDelay(500);
//                        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
//                        safeDelay(50);
//                        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
//
//                        //点击烹饪
//                        safeDelay(500);
//                        robot.mouseMove(964 , 732);
//                        safeDelay(500);
//                        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
//                        safeDelay(50);
//                        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
//
//                        safeDelay(500);
//
//                        if (!moveRice(rice, pingdiguoX, pingdiguoY, 0)){
//                            //捡起米饭 那这份饭就不做了
//                            robot.mouseMove(rice.screenX() , rice.screenY());
//                            safeDelay(300);
//                            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
//                            safeDelay(50);
//                            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
//                            safeDelay(50);
//                            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
//                            safeDelay(50);
//                            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
//                            return;
//                        }
//                        safeDelay(500);
//                        moveWater(water, pingdiguoX, pingdiguoY , 0);
//                        safeDelay(500);
//                        //点击烹饪
//                        robot.mouseMove(pingdiguoX + 255, pingdiguoY + 36);
//                        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
//                        safeDelay(50);
//                        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
//                    }
//                    for (int i = 0; i < panPositions.size(); i++) {
//                        detectCookStatus(i);
//                        //结束黑暗料理
//                        robot.mouseMove(panPositions.get(i).screenX() + 255
//                                , panPositions.get(i).screenY() + 81);
//                        safeDelay(500);
//                        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
//                        safeDelay(50);
//                        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
//                        safeDelay(1000);
//                        safeDelay(1000);
//
//                        //拿取黑暗料理
//                        robot.mouseMove(panPositions.get(i).screenX() + 92
//                                , panPositions.get(i).screenY() + 82);
//                        safeDelay(500);
//                        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
//                        safeDelay(50);
//                        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
//                        safeDelay(1000);
//                    }
//
//                    //关闭tab
//                    robot.keyPress(KeyEvent.VK_TAB);
//                    safeDelay(50);
//                    robot.keyRelease(KeyEvent.VK_TAB);
//                }
//            }
//        } catch (CancellationException e) {
//            // 收到停止信号后直接结束，避免跳过延时继续执行剩余 Robot 操作。
//            Thread.currentThread().interrupt();
//        } catch (AWTException e) {
//            System.err.println("创建附近物品操作 Robot 失败: " + e.getMessage());
//        } catch (RuntimeException | InterruptedException e) {
//            e.printStackTrace();
//            System.err.println("附近物品 Robot 线程执行失败: " + e.getMessage());
//        }
//    }
//
//    private void detectCookStatus(int i) throws InterruptedException {
//        // ===== 配置参数 =====
//        int maxDepth = 100;           // 最大检测次数（比如 30 次 = 30 秒）
//        int checkInterval = 2;       // 检测间隔（秒）
//
//        // ===== 共享状态 =====
//        CountDownLatch latch = new CountDownLatch(1);
//        AtomicInteger attemptCount = new AtomicInteger(0);   // 当前递归/检测次数
//        AtomicBoolean conditionMet = new AtomicBoolean(false); // true=条件满足, false=超时
//
//        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
//
//        // 使用固定延迟任务，不在任务内部递归 schedule，避免停止线程池时再次提交任务。
//        Runnable checkTask = () -> {
//            if (Thread.currentThread().isInterrupted()) {
//                return;
//            }
//
//            int currentAttempt = attemptCount.incrementAndGet();
//            if (currentAttempt > maxDepth) {
//                System.out.println("已达到最大检测次数（" + maxDepth + "），停止检测");
//                conditionMet.set(false);
//                latch.countDown();
//                return;
//            }
//
//            try {
//                Color color = getPixelColor(
//                        panPositions.get(i).screenX() - 21,
//                        panPositions.get(i).screenY() + 103);
//
//                if (Thread.currentThread().isInterrupted()) {
//                    return;
//                }
//                if (color.getBlue() < 50) {
//                    System.out.println("✅ 条件满足！blue=" + color.getBlue());
//                    conditionMet.set(true);
//                    latch.countDown();
//                }
//            } catch (RuntimeException e) {
//                // ↓ 引起的 shutdownNow() 是正常停止，不应作为任务错误打印。
//                if (!Thread.currentThread().isInterrupted() && !scheduler.isShutdown()) {
//                    System.err.println("❌ 检测异常：" + e.getMessage());
//                    e.printStackTrace();
//                }
//            }
//        };
//
//        // ===== 启动第一次检测 =====
//        System.out.println("🚀 启动检测任务...");
//        scheduler.scheduleWithFixedDelay(
//                checkTask, 1, checkInterval, TimeUnit.SECONDS);
//
//        // ===== 外面线程阻塞等待（相当于 wait）=====
//        try {
//            latch.await();  // 阻塞，直到条件满足、超过最大次数或线程被停止
//        } finally {
//            // ↓ 中断 latch.await() 时也必须关闭池，防止定时任务泄漏并继续操作 Robot。
//            scheduler.shutdownNow();
//        }
//
//        // ===== 根据结果做后续处理 =====
//        if (conditionMet.get()) {
//            System.out.println("主线程被唤醒：条件满足，继续执行后续逻辑...");
//        } else {
//            System.out.println("主线程被唤醒：检测超时，执行超时处理逻辑...");
//        }
//    }
//
//    private boolean moveWater(ItemMatch water, int pingdiguoX, int pingdiguoY , int tryTimes) {
//        int maxTryTimes = 3;
//        int waterX = water.screenX();
//        int waterY = water.screenY();
//
//        robot.mouseMove(waterX , waterY);
//        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
//        safeDelay(200);
//        robot.mouseMove(waterX+1 , waterY+1);
//        safeDelay(500);
//        robot.mouseMove(pingdiguoX + 45, pingdiguoY + 45);
//        safeDelay(500);
//        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
//        //执行校验，是否正确托过去了
//        Color riceColor = getPixelColor(pingdiguoX + 45, pingdiguoY + 45);
//        if (riceColor.getBlue()  <= 100){
//            if (tryTimes < maxTryTimes) {
//                tryTimes++;
//                //递归调用本身
//                return moveWater(water, pingdiguoX, pingdiguoY , tryTimes);
//            }else {
//                //填充失败
//                return false;
//            }
//        }else {
//            //填充成功
//            return true;
//        }
//    }
//
//    private boolean moveRice(ItemMatch rice , int pingdiguoX, int pingdiguoY , int tryTimes) {
//        int riceX = rice.screenX();
//        int riceY = rice.screenY();
//        int maxTryTimes = 5;
//        robot.mouseMove(riceX, riceY);
//        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
//        safeDelay(200);
//        robot.mouseMove(riceX +1 , riceY +1);
//        safeDelay(500);
//        robot.mouseMove(pingdiguoX, pingdiguoY + 45);
//        safeDelay(500);
//        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
//        //执行校验，是否正确托过去了
//        Color riceColor = getPixelColor(pingdiguoX, pingdiguoY + 45);
//        if (riceColor.getRed() <= 150 && riceColor.getBlue()  <= 150 && riceColor.getGreen() <= 150 ){
//            if (tryTimes < maxTryTimes) {
//                tryTimes++;
//                //递归调用本身
//                return moveRice(rice, pingdiguoX, pingdiguoY , tryTimes);
//            }else {
//                //填充失败
//                return false;
//            }
//        }else {
//            //填充成功
//            return true;
//        }
//    }
//
//    private List<ScreenTemplateMatch> findPanPositions() {
//        List<ScreenTemplateMatch> panPositions = null;
//        System.out.println("识别平底锅");
//        try {
//            if (panPositionDetector == null) {
//                // 左上角 (699,40)，右下边界扩大到 (740,340)，尺寸为 41×300。  490
//                panPositionDetector = new RegionTemplateDetector(
//                        // 确保位于 y=312 的 35×19 模板能够完整参与比较。
//                        new Rectangle(699, 40, 41, 450),
//                        "classpath:/image/pingdiguo.png",
//                        0.900);
//            }
//
//            // 每次调用本方法都会重新截图识别；返回值先保留在方法局部变量中。
//            panPositions = panPositionDetector.detectOnce();
//            for (ScreenTemplateMatch panPosition : panPositions) {
//                System.out.printf(
//                        "PAN_POSITION -> similarity=%.3f x=%d y=%d%n",
//                        panPosition.similarity(),
//                        panPosition.screenX(),
//                        panPosition.screenY());
//            }
//            if (panPositions.size() == 0){
//                System.out.println("未检测到");
//            }
//        } catch (Exception e) {
//            System.err.println("识别平底锅位置失败: " + e.getMessage());
//        }
//        return panPositions;
//    }
//
//    /** 可被 interrupt() 立即打断的延时。 */
//    private void safeDelay(long millis) {
//        long end = System.currentTimeMillis() + Math.max(0L, millis);
//        while (System.currentTimeMillis() < end) {
//            if (Thread.currentThread().isInterrupted()) {
//                throw new CancellationException("Robot thread stopped");
//            }
//            long remaining = end - System.currentTimeMillis();
//            try {
//                Thread.sleep(Math.min(100L, remaining));
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                throw new CancellationException("Robot thread stopped");
//            }
//        }
//        if (Thread.currentThread().isInterrupted()) {
//            throw new CancellationException("Robot thread stopped");
//        }
//    }
//
//    /** 请求线程在处理下一个物品前结束。 */
//    public void requestStop() {
//        interrupt();
//    }
//    public Color getPixelColor(int x, int y) {
//        long end = System.currentTimeMillis() + 300;
//        while (System.currentTimeMillis() < end) {
//            if ( Thread.currentThread().isInterrupted()) {
//                return Color.BLACK;
//            }
//            try {
//                Thread.sleep(Math.min(50, end - System.currentTimeMillis()));
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                return Color.BLACK;
//            }
//        }
//        Color pixelColor = robot.getPixelColor(x, y);
//        return pixelColor;
//    }
//}
