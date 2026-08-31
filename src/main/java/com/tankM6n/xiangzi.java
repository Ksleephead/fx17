// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n;

import com.tankM6n.nearby.ArrowDetectorConfig;
import com.tankM6n.nearby.RegionTemplateDetector;
import com.tankM6n.nearby.ScreenTemplateMatch;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.*;


public class xiangzi extends Thread {

    private double recoveryTime;       // 体力恢复时间（单位：秒） 65
    private double timePerHit;          // 砸一次箱子的等待时间（单位：秒）
    private boolean dropInsteadDestroy = false; // 是否丢下而不是摧毁（新增复选框状态）
    private String insideGameOrNot = "default";
    private String restType;              // 传递休息类型参数
    private String trainingEfficiency;    // 炼体策略：效率优先或敏捷优先
    // 新增咖啡因相关成员变量
    private double caffeineMgValue;          // 当前已吸收咖啡因（毫克）
    private boolean enableAutoCaffeine; // 是否启用自动吃咖啡粉

    private volatile boolean running;
    private boolean enableAutoEat;     // 是否启用自动吃饭

    private boolean coffeeCheck = true;
    long eatCoffeeTime;

    long start;
    Robot robot;
    private ArrowDetectorConfig arrowDetectorConfig;
    private RegionTemplateDetector arrowDetector;

    private ExecutorService executor;
    //是否需要强制休息
    private volatile String needRest;

    // 添加构造方法接收6个double参数

    public xiangzi(double recoveryTime, double timePerHit, boolean dropInsteadDestroy, String restType, boolean enableAutoCaffeine, double caffeineMgValue, boolean enableAutoEat, String insideGameOrNot, String trainingEfficiency, ExecutorService executor) {
        this.recoveryTime = recoveryTime;
        this.timePerHit = timePerHit;
        this.dropInsteadDestroy = dropInsteadDestroy;
        this.restType = restType;
        this.caffeineMgValue = caffeineMgValue;
        this.enableAutoCaffeine = enableAutoCaffeine;
        this.enableAutoEat = enableAutoEat;
        this.insideGameOrNot = insideGameOrNot;
        this.trainingEfficiency = trainingEfficiency;
        this.executor = executor;
    }

    @Override
    public void run() {
        try {
            needRest = "Unnecessary";
            robot = new Robot();
            running = true;
            zaxiangzi();
        } catch (InterruptedException e) {
            running = false;
        } catch (Exception e) {
            if (running) {
                throw new RuntimeException(e);
            }
        } finally {
            running = false;
            releaseKeys();
        }
    }

    private void ensureRunning() throws InterruptedException {
        if (!running || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("training stopped");
        }
    }

    private void safeDelay(long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + Math.max(0, millis);
        while (System.currentTimeMillis() < end) {
            ensureRunning();
            Thread.sleep(Math.min(100, end - System.currentTimeMillis()));
        }
        ensureRunning();
    }

    /** 比较两张截图的尺寸和所有 RGB 像素是否完全相同。 */
    private boolean imagesAreEqual(BufferedImage first, BufferedImage second) {
        if (first == null || second == null
                || first.getWidth() != second.getWidth()
                || first.getHeight() != second.getHeight()) {
            return false;
        }

        int pixelCount = first.getWidth() * first.getHeight();

        // Robot 截图通常是连续的 int 像素缓冲区，直接比较底层数组，
        // 避免为每个像素重复调用 BufferedImage.getRGB(x, y)。
        if (first.getRaster().getDataBuffer() instanceof DataBufferInt firstBuffer
                && second.getRaster().getDataBuffer() instanceof DataBufferInt secondBuffer
                && firstBuffer.getNumBanks() == 1
                && secondBuffer.getNumBanks() == 1
                && firstBuffer.getSize() == pixelCount
                && secondBuffer.getSize() == pixelCount) {
            return Arrays.equals(firstBuffer.getData(), secondBuffer.getData());
        }

        // 兼容非 int 缓冲区图片；一次批量读取后交给 JDK 比较。
        int[] firstPixels = first.getRGB(
                0, 0, first.getWidth(), first.getHeight(), null, 0, first.getWidth());
        int[] secondPixels = second.getRGB(
                0, 0, second.getWidth(), second.getHeight(), null, 0, second.getWidth());
        return Arrays.equals(firstPixels, secondPixels);
    }

    /** 只在配置的竖向区域中识别 jiantou 模板，并返回相似度最高的命中。 */
    private ScreenTemplateMatch detectArrowOnce() throws Exception {
        if (arrowDetector == null) {
            arrowDetectorConfig = ArrowDetectorConfig.load(
                    Path.of("arrow-detector.properties"));
            arrowDetector = new RegionTemplateDetector(
                    arrowDetectorConfig.searchArea(),
                    arrowDetectorConfig.templatePath(),
                    arrowDetectorConfig.similarityThreshold());
        }

        ScreenTemplateMatch bestMatch = null;
        for (ScreenTemplateMatch match : arrowDetector.detectOnce()) {
            if (bestMatch == null || match.similarity() > bestMatch.similarity()) {
                bestMatch = match;
            }
        }
        return bestMatch;
    }

    private void releaseKeys() {
        if (robot == null) {
            return;
        }
        int[] keys = {
                KeyEvent.VK_ALT, KeyEvent.VK_TAB, KeyEvent.VK_W, KeyEvent.VK_C,
                KeyEvent.VK_X, KeyEvent.VK_0, KeyEvent.VK_1, KeyEvent.VK_3,
                KeyEvent.VK_4, KeyEvent.VK_5, KeyEvent.VK_8
        };
        for (int key : keys) {
            try {
                robot.keyRelease(key);
            } catch (Exception ignored) {
            }
        }
        try {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
        } catch (Exception ignored) {
        }
    }

    private boolean checkcIntestine() {
//        Color intestinelColor = getPixelColor(803, 556); //肠道的50%
        Color intestinelColor = getPixelColor(779, 493); //肠道的80%

        int blue = intestinelColor.getBlue();
        int red = intestinelColor.getRed();
        int green = intestinelColor.getGreen();


        if ((blue > 35 && blue < 60)
                && (red > 35 && red < 60)
                && (green > 35 && green < 60)) {
            //是黑色，那就可以吃
            return true;
        }
        //不让吃
        return false;
    }

    private boolean checkWater() {
        Color intestinelColor = getPixelColor(956, 37); //水的20%

        int blue = intestinelColor.getBlue();


        if (blue < 55) {
            //是黑色，那就可以吃
            return true;
        }
        //不让吃
        return false;
    }

    public boolean checkCoffeeSecend() throws InterruptedException {
        tabSwitch();//打开tab
        robot.keyPress(KeyEvent.VK_4);
        safeDelay(50);
        robot.keyRelease(KeyEvent.VK_4);
        System.out.println("开始执行Coffee二次校验");
        boolean rst = false;
        moveMouseForInfoWithRetry(0);
        safeDelay(500);
        //判断[力]右边的竖线像素点是不是绿色的
        Color coffeeColor = getPixelColor(155, 431);
        int green = coffeeColor.getGreen();



        System.out.println("g值" + green + "/" + LocalDateTime.now());
        if (green > 50) {
            //是绿色，还有咖啡
            rst = false;
        } else {
            //黑色,没咖啡了
            rst = true;
        }
        tabSwitch();//关闭tab
        return rst;
    }

    private void moveMouseForInfoWithRetry(int retryCount) {
        // 移动鼠标
        moveMouseCoffeeInfo();
        // 检测移动后，信息框是否正确打开
        Color dazi = getPixelColor(54, 354);
        boolean needRetry = dazi.getGreen() < 80;
        if (needRetry) {
            if (retryCount >= 3) {
                System.out.println("已达到最大重试次数，停止移动鼠标");
                return;
            }
            System.out.println("信息框未正确打开，第 "
                    + retryCount
                    + " 次重试");
            moveMouseForInfoWithRetry(retryCount + 1);
        }
    }

    public Future<Integer> checkCoffee() {

        Future<Integer> future = executor.submit(() -> {
            //1、先检测横线最右侧的绿色边界像素点边界值  y轴是464开始 x轴范围183-320
            Color pixelColor;
            int x = 0;
            //找到突然变黑的坐标
            for (int i = 183; i <= 342; i++) {//183-342是性能指数从左到右边的x轴坐标 ,这里做一个版本的省性能版本
                //锁定鼠标
                robot.mouseMove(0 , 0);
                pixelColor = getPixelColor(i, 464);
                if (pixelColor.getGreen() < 100){//突然变黑了
                    x = i - 1;
                    System.out.println(x + "是边界值" + LocalDateTime.now());
                    break;
                }
                robot.mouseMove(0 , 0);
            }
            //默认返回不需要吃咖啡粉
            return x;
        });
        return future;
    }
    public Future<Integer> checkCoffeeV2() throws InterruptedException {
        Future<Integer> rst = executor.submit(() -> {
            long start = System.currentTimeMillis();
            // 进度条起始 X 坐标，绿色区域位于这一侧
            final int startX = 182;
            // 进度条结束 X 坐标，黑色区域位于这一侧
            final int endX = 319;
            // 进度条所在的 Y 坐标
            int y = 464;
            // 截图宽度，包含 183 和 342
            final int width = endX - startX + 1;
            // 将鼠标移开，避免鼠标遮挡或触发悬浮信息框
            robot.mouseMove(0, 0);
            safeDelay(50);
            robot.keyPress(KeyEvent.VK_4);
            safeDelay(50);
            robot.keyRelease(KeyEvent.VK_4);
            safeDelay(500);
            // 一次性截取整条进度线，避免反复调用 getPixelColor()
            BufferedImage image = robot.createScreenCapture(
                    new Rectangle(startX, y, width, 1)
            );
            // 二分查找左边界，使用截图内部坐标
            int left = 0;
            // 二分查找右边界
            int right = width - 1;
            // 查找第一个黑色像素
            while (left < right) {
                // 获取中间位置
                int middle = (left + right) >>> 1;
                // 获取中间像素的 RGB 整数值
                int rgb = image.getRGB(middle, 0);
                // 从 RGB 中提取绿色通道，范围为 0~255
                int green = (rgb >>> 8) & 0xFF;
                // 从 RGB 中提取红色通道，范围为 0~255
                int red = (rgb >>> 16) & 0xFF;
                // 绿色值大于等于 100，认为当前像素是绿色
                if ((green >= 100) || (red >= 100)) {
                    // 当前是绿色或者红色，第一个黑色像素一定在右侧
                    left = middle + 1;
                    y = 463;
                } else {
                    // 当前是黑色，它可能就是第一个黑色像素
                    right = middle;
                }
            }
            // left 是第一个黑色像素的截图内坐标
            // 转换成屏幕坐标后减 1，得到最后一个绿色像素坐标
            int boundaryX = startX + left - 1;
            System.out.println(
                    boundaryX + " 是绿色边界值，检测时间：" + LocalDateTime.now()
            );
            System.out.println("检测咖啡因含量耗时：" + (System.currentTimeMillis() - start) + "ms");
            // 返回最后一个绿色像素的屏幕 X 坐标
            return boundaryX;
        });
        safeDelay(1000);
        return rst;
    }

    private void moveMouseCoffeeInfo() {
        robot.delay(300);
        robot.mouseMove(13, 310);
        robot.delay(10);
        robot.mouseMove(13, 311);
        robot.delay(10);
        robot.mouseMove(13, 312);
        robot.delay(10);
        robot.mouseMove(13, 313);
        robot.delay(10);
        robot.mouseMove(13, 314);
        robot.delay(1500);
    }

    public boolean checkStomach() {
//        Color stomachlColor = getPixelColor(717, 556); //大概是胃的50%
        Color stomachlColor = getPixelColor(691, 543); //大概是胃的60%
        int blue = stomachlColor.getBlue();
        int red = stomachlColor.getRed();
        int green = stomachlColor.getGreen();

        if ((blue > 35 && blue < 60)
                && (red > 35 && red < 60)
                && (green > 35 && green < 60)) {
            //是黑色，那就可以吃
            return true;
        }
        //不让吃
        return false;
    }
    public boolean checkNengLiang90() {
        Color stomachlColor = getPixelColor(740, 35); //90%
        int blue = stomachlColor.getBlue();

        if (blue > 60) {
            System.out.println(LocalDateTime.now() + "能量充足");
            //是蓝色，那就不需要吃东西
            return false;
        }
        //默认需要吃
        return true;
    }
    public boolean checkNengLiang2() {
        Color stomachlColor = getPixelColor(783, 38); //20%
        int blue = stomachlColor.getBlue();

        if (blue > 60) {
            System.out.println(LocalDateTime.now() + "能量充足");
            //是蓝色，那就不需要吃东西
            return false;
        }
        //默认需要吃
        return true;
    }
    public boolean checkDanBaiZhi() {
        Color stomachlColor = getPixelColor(700, 179);
        int blue = stomachlColor.getBlue();

        if (blue > 60) {
            System.out.println(LocalDateTime.now() + "蛋白质充足");
            //是蓝色，那就不需要吃东西
            return false;
        }
        //默认需要吃
        return true;
    }

    public Color getPixelColor(int x, int y) {
        long end = System.currentTimeMillis() + 300;
        while (System.currentTimeMillis() < end) {
            if (!running || Thread.currentThread().isInterrupted()) {
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

    public void zaxiangzi() throws Exception {
        start = System.currentTimeMillis();

        if ("default".equals(insideGameOrNot)){
            qieping(robot);
        } else if ("restart".equals(insideGameOrNot)) {
            recoveryTab(robot);
            //站起来
            ensureRunning();
            robot.keyPress(KeyEvent.VK_W);
            safeDelay(300);
            robot.keyRelease(KeyEvent.VK_W);
            safeDelay(4 * 1000);
        } else if ("inGame".equals(insideGameOrNot)) {
            safeDelay(1 * 1000);
        }

        //打开聊天框
        robot.keyPress(KeyEvent.VK_T);
        safeDelay(50);
        robot.keyRelease(KeyEvent.VK_T);
        safeDelay(300);
        //切屏完成开始训练前，先检测聊天是否静音
        Color chatColor = getPixelColor(52, 347);
        if (chatColor.getRed() < 100 && chatColor.getGreen() < 100 && chatColor.getBlue() < 100) {
            //未静音状态
            robot.mouseMove(32,353);
            safeDelay(10);
            robot.mouseMove(32,356);
            safeDelay(300);
            mousePress(InputEvent.BUTTON1_DOWN_MASK);
            safeDelay(300);
            mousePress(InputEvent.BUTTON1_DOWN_MASK);
            safeDelay(1000);
            robot.mouseMove(1024/2,768/2);
            safeDelay(1000);
        }
        //关闭聊天框
        robot.keyPress(KeyEvent.VK_ESCAPE);
        safeDelay(500);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        safeDelay(1000);


        int lastEdge = 0;
        for (int i = 0; i < Integer.MAX_VALUE && running; i++) {
            if (!running) {
                break;
            }
            //开局修手套
            standUp(i, robot);
            needRestLogic();
            fixGloves(robot);//加上物品栏上移逻辑
            //吃东西
            eat(i);
            ensureRunning();
            tabSwitch();
            safeDelay(500);
            //修鞋子
            repairShoes(i);
            for (int j = 0; j < 4 && running; j++) {
                //开始摧毁箱子
                coffeeCheckDomin rst = desitroy(robot, lastEdge , j);
                lastEdge = rst.getLastEdge();
                if (rst.getJ() != 0){
                    //一次大循环只吃一次
                    coffeeCheck = false;
                    j = rst.getJ();
                }
            }
            coffeeCheck = true;
            if (!running) {
                break;
            }
            //关闭Tab
            safeDelay(500);
            ensureRunning();
            tabSwitch();
            safeDelay(100);
            robot.keyPress(KeyEvent.VK_C);
            safeDelay(50);
            robot.keyRelease(KeyEvent.VK_C);
            safeDelay(400);
            if ("趴下".equals(restType)) {
                lieDownRevocery(robot);
            } else {
                recoveryTab(robot);
            }

        }
    }

    private void needRestLogic() throws InterruptedException {
        //如果能量够、蛋白质够，但是水分没跟上就单独喝水
        if ("drinkWater".equals(needRest)){
            //打开tab
            tabSwitch();
            safeDelay(500);
            //切换到4
            robot.keyPress(KeyEvent.VK_4);
            safeDelay(50);
            robot.keyRelease(KeyEvent.VK_4);
            safeDelay(500);
            //如果允许吃
            if (checkStomach() && checkcIntestine()) {
                robot.keyPress(KeyEvent.VK_1);
                safeDelay(50);
                robot.keyRelease(KeyEvent.VK_1);
                safeDelay(500);
                //1、先找到箱子,打开箱子
//                robot.mouseMove(400,80);
//                //右键
//                robot.mousePress(MouseEvent.BUTTON3_DOWN_MASK);
//                safeDelay(50);
//                robot.mouseRelease(MouseEvent.BUTTON3_DOWN_MASK);
//                //打开物品栏
//                safeDelay(500);
//                robot.mouseMove(430 , 125);
//                safeDelay(500);
//                //左键
//                robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
//                safeDelay(50);
//                robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
//                //这里有可能会点到清理库存，检测一下是不是打开了
//                getPixelColor(444,347);
                //检测箱子是不是打开了
                Color xiangziColor = robot.getPixelColor(475, 170);
                if (xiangziColor.getRed() < 200) {
                    //430 200 双击一下箱子
                    robot.mouseMove(430 , 200);
                    safeDelay(500);
                    robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                    safeDelay(50);
                    robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
                    safeDelay(100);
                    robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                    safeDelay(50);
                    robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
                }

                safeDelay(1000);
                //2、找到油桶，拿到手上
                moveWater(0);
                //3、喝3口
                for (int i = 0 ; i < 3; i++) {
                    safeDelay(500);
                    robot.mouseMove(865,135);
                    safeDelay(500);
                    //右键
                    robot.mousePress(MouseEvent.BUTTON3_DOWN_MASK);
                    safeDelay(50);
                    robot.mouseRelease(MouseEvent.BUTTON3_DOWN_MASK);
                    safeDelay(500);
                    robot.mouseMove(890,269);
                    safeDelay(500);
                    robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                    safeDelay(50);
                    robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
                    safeDelay(3000);
                }
                //把油桶移动回去
                moveWater2(0);
                safeDelay(500);
                if (getPixelColor(448, 80).getRed() > 90) {
                    moveWater3(0);
                }
            }
            //关闭tab
            safeDelay(500);
            tabSwitch();
            needRest = "Unnecessary";
        }
        if ("necessary".equals(needRest)){
            standUp(1, robot);
            // 1. 创建一个计数器为 1 的闩锁
            CountDownLatch latch = new CountDownLatch(1);

            // 2. 创建定时线程池
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

            // 3. 提交定时任务
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    // ↓ 停止训练后不允许定时任务继续执行 Robot 操作。
                    ensureRunning();
                    //打开tab
                    tabSwitch();
                    robot.keyPress(KeyEvent.VK_4);
                    safeDelay(50);
                    robot.keyRelease(KeyEvent.VK_4);
                    safeDelay(500);
                    boolean intestine = checkcIntestine();
                    boolean stomach = checkStomach();
                    boolean nengliang = true;
                    boolean danBaizhi = true;

                    Color nengliangColor = getPixelColor(751, 30);//差不多是能量的90%
                    int blue = nengliangColor.getBlue();

                    if (blue > 90) {
                        System.out.println(LocalDateTime.now() + "能量充足");
                        //是蓝色，那就不需要吃东西
                        nengliang = false;
                    }

                    Color danbaizhiColor = getPixelColor(700, 150);//蛋白质
                    int blue2 = danbaizhiColor.getBlue();

                    if (blue2 > 90) {
                        System.out.println(LocalDateTime.now() + "蛋白质充足");
                        //是蓝色，那就不需要吃东西
                        danBaizhi = false;
                    }

                    //关闭tab
                    tabSwitch();
                    safeDelay(1000);

                    System.out.println("nengliang/" + nengliang + "/danBaizhi/" + danBaizhi + "/stomach/" + stomach + "/intestine/" + intestine + LocalDateTime.now());
                    if (nengliang || danBaizhi) {
                        if (stomach && intestine) {
                            chi();
                        }
                    }else {
                        needRest = "Unnecessary";
                        System.out.println("✅ 条件满足！准备唤醒外面线程...");
                        latch.countDown(); // 计数器减为 0，唤醒外面等待的线程
                        return;
                    }
                    System.out.println("⏳ 条件未满足，继续检测...");
                } catch (InterruptedException e) {
                    // shutdownNow() 中断正在执行的检测属于正常停止流程，不打印错误堆栈。
                    Thread.currentThread().interrupt();
                    latch.countDown();
                } catch (Exception e) {
                    if (running) {
                        System.err.println("休息状态定时检测失败: " + e.getMessage());
                        e.printStackTrace();
                    } else {
                        latch.countDown();
                    }
                }
            }, 1, 20, TimeUnit.SECONDS);

            System.out.println("阻塞等待中...");
            try {
                latch.await(); // 阻塞，直到条件满足或训练被停止
            } finally {
                // latch.await() 被 ↓ 中断时也必须关闭定时任务，防止后台继续操作 Robot。
                scheduler.shutdownNow();
            }

            System.out.println("被唤醒，定时检测已关闭");
        }
    }

    private void moveWater(int tryTimes) throws InterruptedException {
        int maxTryTimes = 20;
        if (tryTimes > maxTryTimes){
            return;
        }
        //移动到水上面
        robot.mouseMove(444,347);
        safeDelay(500);
        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
        safeDelay(500);
        //移动到手上
        robot.mouseMove(865,135);
        safeDelay(500);
        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
        safeDelay(500);
        //检测是否正确移动
        Color color = getPixelColor(850, 130);
        if (color.getRed() < 90) {
            System.out.println(LocalDateTime.now() + "从箱子移动到手上失败，重试，重试次数：" + tryTimes);
            moveWater(tryTimes + 1);
        }
    }
    private void moveWater2(int tryTimes) throws InterruptedException {
        int maxTryTimes = 20;
        if (tryTimes > maxTryTimes){
            return;
        }
        //移动到手上
        robot.mouseMove(865,135);
        safeDelay(500);
        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
        safeDelay(500);
        //移动到箱子里面
        robot.mouseMove(400,80);
        safeDelay(500);
        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
        safeDelay(500);
        //检测是否正确移动
        Color color = getPixelColor(435,345);
        if (color.getRed() < 90) {
            System.out.println(LocalDateTime.now() + "从手上移动到箱子失败，重试，重试次数：" + tryTimes);
            moveWater2(tryTimes + 1);
        }
    }
    private void moveWater3(int tryTimes) throws InterruptedException {
        int maxTryTimes = 20;
        if (tryTimes > maxTryTimes){
            return;
        }
        //移动第二格
        robot.mouseMove(448, 80);
        safeDelay(500);
        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
        safeDelay(500);
        //移动到箱子里面
        robot.mouseMove(400,80);
        safeDelay(500);
        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
        safeDelay(500);
        //检测是否正确移动
        Color color = getPixelColor(435,345);
        if (color.getRed() < 90) {
            System.out.println(LocalDateTime.now() + "从物品栏移动到箱子失败，重试，重试次数：" + tryTimes);
            moveWater3(tryTimes + 1);
        }
    }

    private void repairShoes(int i) throws InterruptedException {
        //每五次大循环check一次
        if (i % 5 == 0) {
            System.out.println("修鞋子" + LocalDateTime.now());
            robot.keyPress(KeyEvent.VK_1);
            safeDelay(50);
            robot.keyRelease(KeyEvent.VK_1);
            safeDelay(500);
            Color shoes = getPixelColor(1017, 671);
            if (shoes.getRed() > 200) {
                robot.mouseMove(981 ,634);
                safeDelay(300);
                mousePress(InputEvent.BUTTON3_DOWN_MASK);
                safeDelay(300);
                robot.mouseMove(942 , 715);
                safeDelay(300);
                mousePress(InputEvent.BUTTON1_DOWN_MASK);
                safeDelay(6 * 1000);
            }else{
                System.out.println("鞋子状态良好，不用修" + LocalDateTime.now());
            }
        }
    }

    private void mousePress(int button3DownMask) throws InterruptedException {
        robot.mousePress(button3DownMask);
        safeDelay(50);
        robot.mouseRelease(button3DownMask);
    }

    private void eat(int i) throws InterruptedException {
        safeDelay(1000);
        ensureRunning();
        tabSwitch();
        safeDelay(500);
        ensureRunning();
        robot.keyPress(KeyEvent.VK_4);
        safeDelay(50);
        robot.keyRelease(KeyEvent.VK_4);
        safeDelay(1000);
        iQDetect();

        ensureRunning();
        boolean intestine = checkcIntestine();
        boolean stomach = checkStomach();
        boolean nengliang90 = checkNengLiang90();
        boolean nengliang20 = checkNengLiang2();
        boolean danBaizhi = checkDanBaiZhi();
        boolean water = checkWater();

        safeDelay(500);
        ensureRunning();
        tabSwitch();
        safeDelay(500);

        // 2026.08.25版本更新削弱消化
//      oldEat(stomach, intestine, nengliang, danBaizhi, water);
        //新版本策略，只要胃还能吃得下，就一直吃，直到肠道满了,如果能量和蛋白质还能跟上，就接着炼体，如果能量或蛋白质或水有一项不满足，就休息

        if (stomach && intestine) {
            if (trainingEfficiency.equals("效率优先") ? true : nengliang90) {
                System.out.println(stomach + "" + intestine + nengliang90 + trainingEfficiency + LocalDateTime.now());
                chi();
            }
        }
        //满足任意条件 就强制休息
        if (nengliang20 || danBaizhi || water){
            System.err.println("检测到能量或蛋白质或水分不足！，强制休息" + LocalDateTime.now());
            needRest = "necessary";
        }
        if ((!nengliang20 && !danBaizhi) && water){
            needRest = "drinkWater";
        }
    }

    private void chi() throws InterruptedException {
        robot.keyPress(KeyEvent.VK_0);
        safeDelay(100);
        robot.keyRelease(KeyEvent.VK_0);
        safeDelay(5 * 1000);
        robot.keyPress(KeyEvent.VK_4);
        safeDelay(100);
        robot.keyRelease(KeyEvent.VK_4);
        safeDelay(5 * 1000);
        robot.keyPress(KeyEvent.VK_9);
        safeDelay(100);
        robot.keyRelease(KeyEvent.VK_9);
        safeDelay(5 * 1000);
    }

    private void oldEat(boolean stomach, boolean intestine, boolean nengliang, boolean danBaizhi, boolean water) throws InterruptedException {
        if (enableAutoEat && stomach) {
            if ((intestine && (nengliang || danBaizhi))) {
                //吃一口面粉
                ensureRunning();
                for (int j = 0; j < 4; j++) {
                    robot.keyPress(KeyEvent.VK_0);
                    safeDelay(50);
                    robot.keyRelease(KeyEvent.VK_0);
                    safeDelay(2 * 1000);
                }
                //防止卡背包，切换一下
//                ensureRunning();
//                robot.keyPress(KeyEvent.VK_3);
//                safeDelay(100);
//                robot.keyRelease(KeyEvent.VK_3);
//                safeDelay(1 * 1000);
//                ensureRunning();
//                robot.keyPress(KeyEvent.VK_3);
//                safeDelay(100);
//                robot.keyRelease(KeyEvent.VK_3);
//                safeDelay(2 * 1000);
//                robot.keyPress(KeyEvent.VK_5);
//                safeDelay(300);
//                robot.keyRelease(KeyEvent.VK_5);
//                safeDelay(4000);
            }
            if (nengliang || danBaizhi){
                System.out.println(LocalDateTime.now() + "能量+蛋白质其中之一不满足。需要吃" + nengliang + danBaizhi);
            }else {
                System.out.println(LocalDateTime.now() + "能量+蛋白质充足不2需要吃" + nengliang + danBaizhi);
            }
        }
        safeDelay(1000);

        //喝水
        if (!(water && stomach)) {
            return;
        }

        //按一下5喝水
        ensureRunning();
        robot.keyPress(KeyEvent.VK_5);
        safeDelay(300);
        robot.keyRelease(KeyEvent.VK_5);
        safeDelay(4000);
        ensureRunning();
        robot.keyPress(KeyEvent.VK_5);
        safeDelay(300);
        robot.keyRelease(KeyEvent.VK_5);
        safeDelay(4000);
        ensureRunning();
        robot.keyPress(KeyEvent.VK_5);
        safeDelay(300);
        robot.keyRelease(KeyEvent.VK_5);
        safeDelay(4000);
        //防止卡背包，切换一下
        ensureRunning();
        robot.keyPress(KeyEvent.VK_3);
        safeDelay(100);
        robot.keyRelease(KeyEvent.VK_3);
        safeDelay(2 * 1000);
        ensureRunning();
        robot.keyPress(KeyEvent.VK_3);
        safeDelay(100);
        robot.keyRelease(KeyEvent.VK_3);
        safeDelay(2 * 1000);
    }

    private void iQDetect() throws InterruptedException {
        //这里顺带着检查一下智力面板是不是开启了
        Color iQPadel = getPixelColor(298, 117);
        if (iQPadel.getRed() > 200 && iQPadel.getBlue() > 200 && iQPadel.getGreen() > 200){
            //打开了智力面板
            robot.mouseMove(47 , 118);
            safeDelay(50);
            mousePress(InputEvent.BUTTON1_DOWN_MASK);
            safeDelay(100);
            mousePress(InputEvent.BUTTON1_DOWN_MASK);
        }
    }


    public void setRunning() {
        this.running = false;
        interrupt();
    }


    private void eatCoffeeV2(Robot robot) throws InterruptedException {
//        在校验一次是不是需要吃咖啡
//        if (!checkCoffeeSecend()) {
//            System.out.println(LocalDateTime.now() + "出现咖啡含量误判，二次校验不通过");
//            //误判 退出
//            return;
//        }
        if (enableAutoCaffeine) {
            System.out.println(LocalDateTime.now() + "吃一次咖啡");
            safeDelay(1000);
            ensureRunning();
            robot.keyPress(KeyEvent.VK_8);
            safeDelay(50);
            robot.keyRelease(KeyEvent.VK_8);
            safeDelay(5000);
            ensureRunning();
            robot.keyPress(KeyEvent.VK_8);
            safeDelay(50);
            robot.keyRelease(KeyEvent.VK_8);
            safeDelay(5000);
            ensureRunning();
//            robot.keyPress(KeyEvent.VK_8);
//            safeDelay(50);
//            robot.keyRelease(KeyEvent.VK_8);
//            safeDelay(5 * 1000);
        }
    }

    private void fixGloves(Robot robot) throws InterruptedException {
        //修手套
        ensureRunning();
        tabSwitch();
        safeDelay(300);
        ensureRunning();
        robot.keyPress(KeyEvent.VK_1);
        safeDelay(50);
        robot.keyRelease(KeyEvent.VK_1);
        safeDelay(300);
        Color gloves = getPixelColor(748, 758);
        if (gloves.getRed() > 200) {
            robot.mouseMove(715, 724);
            safeDelay(500);
            ensureRunning();
            mousePress(InputEvent.BUTTON3_DOWN_MASK);
            safeDelay(500);
            robot.mouseMove(730, 721);
            safeDelay(500);
            ensureRunning();
            mousePress(InputEvent.BUTTON1_DOWN_MASK);
            safeDelay(6 * 1000);
            ensureRunning();
        }else{
            System.out.println("手套状态良好，不用修" + LocalDateTime.now());
        }
        //物品栏上移
        try {
            ScreenTemplateMatch arrowMatch = detectArrowOnce();
            if (arrowMatch == null) {
                System.out.println("未识别到物品栏箭头，跳过物品栏上移");
            } else {
                int arrowX = arrowMatch.screenX() + arrowDetectorConfig.resultOffsetX();
                int arrowY = arrowMatch.screenY() + arrowDetectorConfig.resultOffsetY();
                System.out.printf(
                        "ARROW -> similarity=%.3f x=%d y=%d%n",
                        arrowMatch.similarity(), arrowX, arrowY);

                robot.mouseMove(arrowX, arrowY);
                safeDelay(500);
                robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                safeDelay(500);
                robot.mouseMove(arrowX, 66);
                safeDelay(500);
                robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
                safeDelay(500);
            }
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("识别物品栏箭头失败: " + e.getMessage());
        }
        tabSwitch();
    }

    private coffeeCheckDomin desitroy(Robot robot , int lastEdge , int j) throws ExecutionException, InterruptedException, IOException, AWTException {
        coffeeCheckDomin coffeeCheckDomin = new coffeeCheckDomin();
        coffeeCheckDomin.setJ(0);
        ensureRunning();
        robot.keyPress(KeyEvent.VK_1);
        safeDelay(50);
        robot.keyRelease(KeyEvent.VK_1);

        safeDelay(500);
        robot.mouseMove(380, 100);
        safeDelay(500);
        ensureRunning();
        mousePress(InputEvent.BUTTON3_DOWN_MASK);
        safeDelay(500);
        if (dropInsteadDestroy) {
            robot.mouseMove(390, 190);
        } else {
            robot.mouseMove(390, 195);
        }
        safeDelay(500);
        ensureRunning();
        mousePress(InputEvent.BUTTON1_DOWN_MASK);
        safeDelay(500);

        ensureRunning();
        robot.keyPress(KeyEvent.VK_4);
        safeDelay(50);
        robot.keyRelease(KeyEvent.VK_4);

        Future<Integer> coffeeEdge = checkCoffeeV2();//开启检测咖啡含量线程
        Future<Boolean> coffeeContains = coffeeStatus();
        //第一次砸
        if (j != 3){
            safeDelay(Math.max(1L, Math.round(timePerHit * 1000.0)));
        }else{
            // 检测区域：(128,317) 到右下边界 (158,324)，尺寸为 30×7。
            Rectangle monitoredArea = new Rectangle(128, 317, 30, 7);
            BufferedImage previousImage = robot.createScreenCapture(monitoredArea);
            long comparisonStartedAt = System.nanoTime();
            long comparisonTimeLimit = TimeUnit.MILLISECONDS.toNanos(
                    Math.max(1L, Math.round(timePerHit * 1000.0))
            );
            for (int i = 0; i < 20; i++) {
                // 每轮开始先检查累计耗时，确保该等待循环不会无限阻塞。
                long elapsed = System.nanoTime() - comparisonStartedAt;
                if (elapsed >= comparisonTimeLimit) {
                    System.out.println("检测区域等待达到上限，继续运行" + LocalDateTime.now());
                    break;
                }

                // 每 2 秒检测一次；safeDelay 可以在按下停止热键后及时结束线程。
                long remainingMillis = TimeUnit.NANOSECONDS.toMillis(
                        comparisonTimeLimit - elapsed);
                safeDelay(Math.min(2000L, remainingMillis));

                // 延时结束后再次检查，达到上限就不再进行下一次截图和比较。
                if (System.nanoTime() - comparisonStartedAt >= comparisonTimeLimit) {
                    System.out.println("检测区域等待达到32秒上限，继续运行" + LocalDateTime.now());
                    break;
                }

                BufferedImage currentImage = robot.createScreenCapture(monitoredArea);
                if (imagesAreEqual(previousImage, currentImage)) {
                    System.out.println("检测区域已稳定，继续运行" + LocalDateTime.now());
                    break;
                }

                System.out.println("检测区域仍在变化，继续等待" + LocalDateTime.now());
                previousImage = currentImage;
            }
        }

        robot.mouseMove(1024 / 2 , 768 / 2);
        safeDelay(200);
        ensureRunning();
        mousePress(InputEvent.BUTTON1_DOWN_MASK);
        safeDelay(50);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        safeDelay(300);
        ensureRunning();

        //这个时候线程肯定执行完了,取刚开始检测的值,x的值跟上一次比较不应该差5个像素
        Integer x = coffeeEdge.get();
        for (int i = 0; i < 2; i++) {
            if (lastEdge != 0){
                int abs = Math.abs(x - lastEdge);
                if (abs > 8){
                    System.out.println("错误，两次咖啡因差值检测超出5个像素");
                    //再次检测
                    Future<Integer> coffeeEdge2 = checkCoffeeV2();//开启检测咖啡含量线程
                    x = coffeeEdge2.get();
                }else {
                    break;
                }
            }
        }

        robot.keyPress(KeyEvent.VK_1);
        safeDelay(50);
        robot.keyRelease(KeyEvent.VK_1);
        safeDelay(500);
        coffeeCheckDomin.setLastEdge(x); // 哪个大取哪个
        boolean eatCoffee = false;//是否需要吃咖啡粉
        if ((lastEdge!=0 && x < lastEdge) || !coffeeContains.get()){
            eatCoffee = true;
            //少运行一次敲箱子
            coffeeCheckDomin.setJ(++j);
        }
        if (eatCoffee && coffeeCheck){
            //  本次大循环已经吃过一次了
            coffeeCheck = false;
            coffeeCheckDomin.setLastEdge(x);
            //关闭TAB
            ensureRunning();
            tabSwitch();

            safeDelay(500);

            robot.keyPress(KeyEvent.VK_C);
            safeDelay(300);
            robot.keyRelease(KeyEvent.VK_C);
            eatCoffeeV2(robot);
            //打开TAB
            ensureRunning();
            tabSwitch();
        }
        return coffeeCheckDomin;
    }

    private void tabSwitch() throws InterruptedException {
        robot.keyPress(KeyEvent.VK_TAB);
        safeDelay(50);
        robot.keyRelease(KeyEvent.VK_TAB);
        //在开关tab的时候，做一个掉线检测
    }

    private Future<Boolean> coffeeStatus() {
        Future<Boolean> future = executor.submit(() -> {
            //如果是白色,说明体内已经没有咖啡粉了
            Color color = getPixelColor(183, 468);
            if (color.getGreen() > 180 && color.getRed() > 180 && color.getBlue() > 180){
                System.out.println("检测到咖啡粉已耗尽" + LocalDateTime.now());
                return false;//无
            }
            return true;//有
        });
        return future;
    }

    private void standUp(int i, Robot robot) throws InterruptedException {
        if ("趴下".equals(restType)) {
            if (i != 0) {
                ensureRunning();
                robot.keyPress(KeyEvent.VK_X);
                safeDelay(50);
                robot.keyRelease(KeyEvent.VK_X);
                safeDelay(3 * 1000);
            }

        } else {
            if (i != 0) {
                ensureRunning();
                robot.keyPress(KeyEvent.VK_W);
                safeDelay(300);
                robot.keyRelease(KeyEvent.VK_W);
                safeDelay(4 * 1000);
                ensureRunning();
            }

        }
    }

    private void qieping(Robot robot) throws InterruptedException {
        //切屏
        ensureRunning();
        robot.keyPress(KeyEvent.VK_ALT);
        safeDelay(100);
        robot.keyPress(KeyEvent.VK_TAB);
        safeDelay(100);
        robot.keyRelease(KeyEvent.VK_TAB);
        robot.keyRelease(KeyEvent.VK_ALT);
        safeDelay(3 * 1000);
    }

    private void lieDownRevocery(Robot robot) throws InterruptedException {
        safeDelay(1000);
        ensureRunning();
        robot.keyPress(KeyEvent.VK_X);
        safeDelay(50);
        robot.keyRelease(KeyEvent.VK_X);
        safeDelay((long) (recoveryTime * 1000));
    }

    private void recoveryTab(Robot robot) throws InterruptedException {
        safeDelay(1000);
        ensureRunning();
        robot.keyPress(KeyEvent.VK_TAB);//按下tab
        safeDelay(900);
        robot.mouseMove(460, 430);
        safeDelay(900);
        ensureRunning();
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        safeDelay(900);
        robot.mouseMove(592, 488);
        safeDelay(900);
        ensureRunning();
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        safeDelay(900);
        robot.keyRelease(KeyEvent.VK_TAB);//松开tab
        releaseKeys();
        safeDelay((long) (recoveryTime * 1000));
    }
}
