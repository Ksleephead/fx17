// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.training;

import com.tankM6n.nearby.ScreenTemplateMatch;
import org.springframework.util.CollectionUtils;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;


public class TrainingWorker extends Thread {

    private double recoveryTime;       // 体力恢复时间（单位：秒） 65
    private double timePerHit;          // 砸一次箱子的等待时间（单位：秒）
    private String insideGameOrNot = "default";
    private String restType;              // 传递休息类型参数
    private String trainingEfficiency;    // 炼体策略：效率优先或敏捷优先
    // 新增咖啡因相关成员变量
    private boolean enableAutoCaffeine; // 是否启用自动吃咖啡粉

    private volatile boolean running;
    private boolean foodStroageIntoFridge;     // 食物是否存放于冰箱
    TrainingRobot robot;
    private final TrainingDetectionService detectionService = new TrainingDetectionService();
    private RestService restService;
    private RepairService repairService;
    private InventoryService inventoryService;
    private FoodService foodService;
    private StorageService storageService;

    private ExecutorService executor;
    private final Runnable completionCallback;
    // 添加构造方法接收6个double参数

    public TrainingWorker(double recoveryTime, double timePerHit, boolean dropInsteadDestroy, String restType, boolean enableAutoCaffeine, double caffeineMgValue, boolean enableAutoEat, boolean foodStroageIntoFridge, String insideGameOrNot, String trainingEfficiency, ExecutorService executor) {
        this(recoveryTime, timePerHit, dropInsteadDestroy, restType,
                enableAutoCaffeine, caffeineMgValue, enableAutoEat,
                foodStroageIntoFridge, insideGameOrNot, trainingEfficiency,
                executor, () -> { });
    }

    public TrainingWorker(double recoveryTime, double timePerHit,
                          boolean dropInsteadDestroy, String restType,
                          boolean enableAutoCaffeine, double caffeineMgValue,
                          boolean enableAutoEat, boolean foodStroageIntoFridge,
                          String insideGameOrNot, String trainingEfficiency,
                          ExecutorService executor, Runnable completionCallback) {
        this.recoveryTime = recoveryTime;
        this.timePerHit = timePerHit;
        this.restType = restType;
        this.enableAutoCaffeine = enableAutoCaffeine;
        this.foodStroageIntoFridge = foodStroageIntoFridge;
        this.insideGameOrNot = insideGameOrNot;
        this.trainingEfficiency = trainingEfficiency;
        this.executor = executor;
        this.completionCallback = completionCallback;
    }

    @Override
    public void run() {
        try {
            robot = new TrainingRobot(() -> running);
            restService = new RestService(robot, recoveryTime, restType);
            repairService = new RepairService(robot, detectionService);
            inventoryService = new InventoryService(robot, detectionService);
            foodService = new FoodService(
                    robot, detectionService, inventoryService, restService, trainingEfficiency);
            storageService = new StorageService(
                    robot,
                    detectionService,
                    inventoryService,
                    executor,
                    timePerHit,
                    enableAutoCaffeine,
                    foodStroageIntoFridge);
            running = true;
            runTrainingLoop();
        } catch (InterruptedException e) {
            running = false;
        } catch (Exception e) {
            if (running) {
                throw new RuntimeException(e);
            }
        } finally {
            running = false;
            releaseKeys();
            completionCallback.run();
        }
    }

    private void ensureRunning() throws InterruptedException {
        robot.ensureRunning();
    }

    private void safeDelay(long millis) throws InterruptedException {
        robot.safeDelay(millis);
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

    private void runTrainingLoop() throws Exception {
        restService.prepareForTraining(insideGameOrNot);

        muteAll();//聊天频道静音


        for (int i = 0; i < Integer.MAX_VALUE && running; i++) {
            if (!running) {
                break;
            }
            releaseKeys();
            restService.standUp(i);//站立
            repairService.repairGloves(i);//修手套、鞋子

            robot.tabSwitch();
            safeDelay(500);
            robot.keyPress(KeyEvent.VK_1);
            safeDelay(50);
            robot.keyRelease(KeyEvent.VK_1);
            //识别冰箱位置
            StorageItemMatch fridgePosition = null;
            Optional<StorageItemMatch> position = storageService.findCaseOrFridge("fridge");
            if (position.isPresent()) {
                fridgePosition = position.get();
                System.out.println("识别到冰箱位置:" + fridgePosition.screenX() + "," + fridgePosition.screenY());
                Color color = robot.getPixelColor(fridgePosition.screenX() - 24, fridgePosition.screenY() - 37);
                //如果是橙色就是打开了的,<200就是关闭状态
                if (color.getRed() < 200) {
                    robot.mouseMove(fridgePosition.screenX(), fridgePosition.screenY());
                    safeDelay(300);
                    doubleClick();
                    robot.mouseMove(0, 0);
                }
                safeDelay(500);
                List<ScreenTemplateMatch> cookedFishMatches =
                        detectionService.detectCookedFishOnce();
                List<ScreenTemplateMatch> cookedCornMatches =
                        detectionService.detectCookedCornOnce();

                if (!CollectionUtils.isEmpty(cookedFishMatches)){
                    System.out.println("识别到烤鱼个数：" + cookedCornMatches.size());
                    ScreenTemplateMatch screenTemplateMatch = cookedFishMatches.get(0);
                    robot.mouseMove(screenTemplateMatch.screenX() , screenTemplateMatch.screenY());
                    safeDelay(300);
                    doubleClick();
                }
                if (!CollectionUtils.isEmpty(cookedCornMatches)){
                    System.out.println("识别到烤玉米个数：" + cookedCornMatches.size());
                    for (ScreenTemplateMatch screenTemplateMatch : cookedCornMatches) {
                        robot.mouseMove(screenTemplateMatch.screenX() , screenTemplateMatch.screenY());
                        safeDelay(800);
                        doubleClick();
                    }
                }
            }
            robot.tabSwitch();


            foodService.checkAndEat();//吃饭
            foodService.handleRequiredRest();//强制休息
            ensureRunning();
            robot.tabSwitch();
            safeDelay(500);

            storageService.destroyForCycle();
            if (!running) {
                break;
            }
            //关闭Tab
            safeDelay(500);
            ensureRunning();
            robot.tabSwitch();
            safeDelay(100);
            robot.keyPress(KeyEvent.VK_C);
            safeDelay(50);
            robot.keyRelease(KeyEvent.VK_C);
            safeDelay(400);
            restService.recover();
        }
    }

    private void doubleClick() throws InterruptedException {
        //双击打开
        robot.click(InputEvent.BUTTON1_DOWN_MASK);
        safeDelay(50);
        robot.click(InputEvent.BUTTON1_DOWN_MASK);
    }

    private void muteAll() throws InterruptedException {
        //打开聊天框
        robot.keyPress(KeyEvent.VK_T);
        safeDelay(50);
        robot.keyRelease(KeyEvent.VK_T);
        safeDelay(300);
        //切屏完成开始训练前，先检测聊天是否静音
        Color chatColor = robot.getDelayedPixelColor(52, 347);
        if (chatColor.getRed() < 100 && chatColor.getGreen() < 100 && chatColor.getBlue() < 100) {
            //未静音状态
            robot.mouseMove(32,353);
            safeDelay(10);
            robot.mouseMove(32,356);
            safeDelay(300);
            robot.click(InputEvent.BUTTON1_DOWN_MASK);
            safeDelay(300);
            robot.click(InputEvent.BUTTON1_DOWN_MASK);
            safeDelay(1000);
            robot.mouseMove(1024/2,768/2);
            safeDelay(1000);
        }
        //关闭聊天框
        robot.keyPress(KeyEvent.VK_ESCAPE);
        safeDelay(500);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        safeDelay(1000);
    }

    /** @deprecated Kept for source compatibility with the former xiangzi class. */
    @Deprecated
    public void zaxiangzi() throws Exception {
        runTrainingLoop();
    }

    public void requestStop() {
        this.running = false;
        interrupt();
    }

    /** @deprecated Use {@link #requestStop()}. */
    @Deprecated
    public void setRunning() {
        requestStop();
    }


}
