// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n;

import com.tankM6n.nearby.*;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;

public class Main extends Application {
    private String drinkWaterAfter;    // 体力耗尽后多少下后喝水
    private String recoveryTime;       // 体力恢复时间（单位：秒）
    private boolean dropInsteadDestroy; // 是否丢下而不是摧毁（新增复选框状态）
    private String restType;           // 休息类型（新增下拉框值）
    private boolean enableAutoEat;     // 是否启用自动吃饭
    private CheckBox autoEatCheckBox;  // 自动吃饭复选框引用

    // ===== NativeHook 生命周期控制 =====
    private static volatile boolean nativeHookRegistered = false;
    private static final Object NATIVE_HOOK_LOCK = new Object();
    // 新增咖啡因相关成员变量
    private String caffeineMg;          // 当前已吸收咖啡因（毫克）
    private boolean enableAutoCaffeine; // 是否启用自动吃咖啡粉

    private final ConfigService configService = new ConfigService();

    // 存储输入框引用
    private Map<String, TextField> inputFields = new HashMap<>();

    // 复选框引用
    private CheckBox dropCheckBox;

    // 新增咖啡因控件引用
    private CheckBox caffeineCheckBox;
    private TextField caffeineTextField;

    // 下拉框引用
    private ComboBox<String> restTypeComboBox;

    // 炼体策略下拉框，默认效率优先。
    private ComboBox<String> trainingEfficiencyComboBox;
    private volatile String trainingEfficiency = "效率优先";

    // ===================== 新增服务器重启 =====================
    private ComboBox<String> serverRestartComboBox; // 服务器重启时间下拉框
    // Codex生成：重启时间和重启间隔统一使用合法范围0～23。
    private ComboBox<String> serverRestartIntervalComboBox; // 服务器重启间隔下拉框
    private volatile String serverRestartTime = "4";        // 服务器重启时间（默认4点）
    private volatile String serverRestartInterval = "6";    // 服务器重启间隔（默认6小时）

    // 制作烤玉米数量，界面可选择 1～50，默认制作 1 个。
    private ComboBox<Integer> cornCookCountComboBox;
    private volatile int cornCookCount = 1;

    private ScheduledExecutorService restartScheduler;
    // Codex生成：保存唯一的重启检查任务，避免重连后重复创建定时任务。
    private ScheduledFuture<?> restartCheckTask;
    private int restartDelayMinutes = 2; // 重连后给电脑进入服务器的时间
    // 主舞台引用
    private Stage mainStage;

    // 编辑/保存按钮引用
    private Button editSaveButton;

    // 训练线程
    private volatile xiangzi trainingThread;

    // 附近物品识别器；实例复用，避免重复读取和处理模板。
    private NearbyItemDetector nearbyItemDetector;

    // 接收检测结果并执行后续 Robot 操作的独立线程。
    private volatile NearbyItemRobotThread nearbyItemRobotThread;
    private volatile cookCornThread cookCornThread;
    // 控制多轮烤玉米的外层线程；与单轮 cookCornThread 分开，便于停止整个 cook()。
    private volatile Thread cookTaskThread;

    // 累计炼体时长。运行期间使用单调时钟，避免系统时间调整影响计时。
    private long accumulatedTrainingMillis;
    private volatile long trainingStartedAtNanos = -1L;
    private TextField trainingDurationField;
    private Timeline trainingDurationTimeline;

    // Retain the JavaFX player while a short notification is playing.
    private MediaPlayer notificationPlayer;

    private ExecutorService service;
    @Override
    public void start(Stage primaryStage) {
        this.mainStage = primaryStage;

        // 主面板
        Pane root = new Pane();
        root.setPadding(new Insets(20));

        // 添加顶部提示信息
        Label tip1 = new Label("1、建筑手套放在7号快捷键");
        tip1.setLayoutX(20);
        tip1.setLayoutY(10);
        tip1.setFont(Font.font("System", FontWeight.BOLD, 14));
        tip1.setStyle("-fx-text-fill: red;");

        Label tip2 = new Label("2、吃的放到4、9、0号快捷键");
        tip2.setLayoutX(20);
        tip2.setLayoutY(35);
        tip2.setFont(Font.font("System", FontWeight.BOLD, 14));
        tip2.setStyle("-fx-text-fill: red;");


        Label tip3 = new Label("3、分辨率改成【全屏】1024*768");
        tip3.setLayoutX(220);
        tip3.setLayoutY(10);
        tip3.setFont(Font.font("System", FontWeight.BOLD, 14));
        tip3.setStyle("-fx-text-fill: red;");

        Label tip4 = new Label("4、装咖啡的瓶子放8号快捷键");
        tip4.setLayoutX(220);
        tip4.setLayoutY(35);
        tip4.setFont(Font.font("System", FontWeight.BOLD, 14));
        tip4.setStyle("-fx-text-fill: red;");

        Label tip5 = new Label("5、鞋放在6号快捷键");
        tip5.setLayoutX(20);
        tip5.setLayoutY(60);
        tip5.setFont(Font.font("System", FontWeight.BOLD, 14));
        tip5.setStyle("-fx-text-fill: red;");


        Label tip6 = new Label("6、按【↓】或【PGDN】停止训练/停止做饭");
        tip6.setLayoutX(220);
        tip6.setLayoutY(60);
        tip6.setFont(Font.font("System", FontWeight.BOLD, 14));
        tip6.setStyle("-fx-text-fill: red;");

        Label tip7 = new Label("7、切屏进入游戏后按【↑】或【PGUP】开始训练");
        tip7.setLayoutX(20);
        tip7.setLayoutY(85);
        tip7.setFont(Font.font("System", FontWeight.BOLD, 14));
        tip7.setStyle("-fx-text-fill: red;");

        Label tip8 = new Label("8、切屏进入游戏后按【←】开始做饭");
        tip8.setLayoutX(20);
        tip8.setLayoutY(110);
        tip8.setFont(Font.font("System", FontWeight.BOLD, 14));
        tip8.setStyle("-fx-text-fill: red;");

        root.getChildren().addAll(tip1, tip2 , tip3 , tip4 , tip5 , tip6 , tip7, tip8);

        // 当前只保留仍参与训练逻辑的恢复时间输入框。
//        createInputGroup(root, "体力耗尽后多少下后喝水：", "", 20, 170, "drinkWaterAfter");
        createInputGroup(root, "体力恢复时间（单位：秒）：", "", 20, 145, "recoveryTime");

        // 添加休息类型下拉框
        Label restTypeLabel = new Label("休息类型：");
        restTypeLabel.setLayoutX(20);
        restTypeLabel.setLayoutY(180);

        // 创建下拉框并添加选项
        restTypeComboBox = new ComboBox<>();
        ObservableList<String> restOptions = FXCollections.observableArrayList("坐下", "趴下");
        restTypeComboBox.setItems(restOptions);
        restTypeComboBox.setLayoutX(120);
        restTypeComboBox.setLayoutY(180);
        restTypeComboBox.setPrefWidth(120);
        restTypeComboBox.setValue("趴下"); // 设置默认值

        // 为下拉框添加事件处理
        restTypeComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            restType = newValue;
        });

        // 添加复选框（如果点击的是丢下而不是摧毁，把这个勾打上）
        dropCheckBox = new CheckBox("如果点击的是丢下而不是摧毁，把这个勾打上【失效】");
        dropCheckBox.setLayoutX(20);
        dropCheckBox.setLayoutY(215);
        dropCheckBox.setSelected(dropInsteadDestroy);
        dropCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            dropInsteadDestroy = newValue;
        });

        // 添加咖啡因复选框
        caffeineCheckBox = new CheckBox("是否启用自动吃咖啡粉");
        caffeineCheckBox.setLayoutX(20);
        caffeineCheckBox.setLayoutY(250);
        caffeineCheckBox.setSelected(enableAutoCaffeine);
        caffeineCheckBox.setDisable(true); // 初始状态下禁用
        caffeineCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            enableAutoCaffeine = newValue;
            // 根据复选框状态设置输入框的可用性
            if (caffeineTextField != null) {
                caffeineTextField.setDisable(!newValue);
            }
        });

        // 添加咖啡因输入框和标签
//        Label caffeineLabel = new Label("当前已吸收咖啡因（单位:毫克,整数）：");
//        caffeineLabel.setLayoutX(20);
//        caffeineLabel.setLayoutY(390);

//        caffeineTextField = new TextField();
//        caffeineTextField.setLayoutX(220);
//        caffeineTextField.setLayoutY(390);
//        caffeineTextField.setPrefWidth(120);
//        caffeineTextField.setPromptText("请输入毫克数");
//        caffeineTextField.setDisable(true); // 初始状态下禁用
//        caffeineTextField.textProperty().addListener((observable, oldValue, newValue) -> {
//            caffeineMg = newValue;
//        });

        // 添加自动吃饭复选框 - 在咖啡因控件后添加
        autoEatCheckBox = new CheckBox("是否启用自动吃饭");
        autoEatCheckBox.setLayoutX(220);
        autoEatCheckBox.setLayoutY(250);
        autoEatCheckBox.setSelected(enableAutoEat);
        autoEatCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            enableAutoEat = newValue;
        });

        // ===================== 服务器重启时间 =====================
        Label serverRestartLabel = new Label("下一次服务器重启时间【24小时制】：");
        serverRestartLabel.setLayoutX(20);
        serverRestartLabel.setLayoutY(285);

        serverRestartComboBox = new ComboBox<>();
        ObservableList<String> hours = FXCollections.observableArrayList();
        // Codex生成：24小时制的合法小时为0～23，不提供24选项。
        for (int i = 0; i < 24; i++) {
            hours.add(String.valueOf(i));
        }
        serverRestartComboBox.setItems(hours);
        serverRestartComboBox.setLayoutX(220);
        serverRestartComboBox.setLayoutY(285);
        serverRestartComboBox.setPrefWidth(120);
        serverRestartComboBox.setValue(serverRestartTime);
        serverRestartComboBox.valueProperty().addListener((obs, o, n) -> serverRestartTime = n);

        Label serverRestartIntervalLabel = new Label("服务器重启间隔【小时】：");
        serverRestartIntervalLabel.setLayoutX(20);
        serverRestartIntervalLabel.setLayoutY(315);

        serverRestartIntervalComboBox = new ComboBox<>();
        ObservableList<String> intervals = FXCollections.observableArrayList();
        // Codex生成：重启间隔只提供0～23，不提供24选项。
        for (int i = 0; i < 24; i++) {
            intervals.add(String.valueOf(i));
        }
        serverRestartIntervalComboBox.setItems(intervals);
        serverRestartIntervalComboBox.setLayoutX(220);
        serverRestartIntervalComboBox.setLayoutY(315);
        serverRestartIntervalComboBox.setPrefWidth(120);
        serverRestartIntervalComboBox.setValue(serverRestartInterval);
        serverRestartIntervalComboBox.valueProperty().addListener((obs, o, n) -> serverRestartInterval = n);

        // ===================== 炼体效率选择 =====================
        Label trainingEfficiencyLabel = new Label("选择炼体效率：");
        trainingEfficiencyLabel.setLayoutX(20);
        trainingEfficiencyLabel.setLayoutY(345);

        trainingEfficiencyComboBox = new ComboBox<>();
        trainingEfficiencyComboBox.setItems(FXCollections.observableArrayList(
                "效率优先", "敏捷优先"));
        trainingEfficiencyComboBox.setLayoutX(220);
        trainingEfficiencyComboBox.setLayoutY(345);
        trainingEfficiencyComboBox.setPrefWidth(120);
        trainingEfficiencyComboBox.setValue("效率优先");
        trainingEfficiencyComboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                trainingEfficiency = newValue;
            }
        });

        // ===================== 制作烤玉米数量 =====================
        Label cornCookCountLabel = new Label("制作烤玉米个数：");
        cornCookCountLabel.setLayoutX(20);
        cornCookCountLabel.setLayoutY(380);

        cornCookCountComboBox = new ComboBox<>();
        ObservableList<Integer> cornCookCountOptions = FXCollections.observableArrayList();
        for (int i = 1; i <= 50; i++) {
            cornCookCountOptions.add(i);
        }
        cornCookCountComboBox.setItems(cornCookCountOptions);
        cornCookCountComboBox.setLayoutX(220);
        cornCookCountComboBox.setLayoutY(380);
        cornCookCountComboBox.setPrefWidth(120);
        cornCookCountComboBox.setValue(1);
        cornCookCountComboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                cornCookCount = newValue;
            }
        });

        // 添加编辑/保存按钮
        editSaveButton = new Button("编辑");
        editSaveButton.setLayoutX(20);
        editSaveButton.setLayoutY(425);
        editSaveButton.setPrefWidth(150);
        editSaveButton.setOnAction(event -> toggleEditMode());

        // 添加开始训练按钮
        Button startButton = new Button("开始训练");
        startButton.setLayoutX(180);
        startButton.setLayoutY(425);
        startButton.setPrefWidth(150);
        startButton.setOnAction(event -> startTraining("default"));

        // 添加停止训练按钮
        Button stopButton = new Button("停止训练");
        stopButton.setLayoutX(340);
        stopButton.setLayoutY(425);
        stopButton.setPrefWidth(150);
        stopButton.setOnAction(event -> stopTraining());

        Label trainingDurationLabel = new Label("已炼体时长：");
        trainingDurationLabel.setLayoutX(20);
        trainingDurationLabel.setLayoutY(215);
//        trainingDurationLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        trainingDurationField = new TextField();
        trainingDurationField.setLayoutX(105);
        trainingDurationField.setLayoutY(215);
        trainingDurationField.setPrefWidth(225);
        trainingDurationField.setEditable(false);
        trainingDurationField.setFocusTraversable(false);
        trainingDurationField.setStyle("-fx-font-weight: bold; -fx-alignment: center;");
        updateTrainingDurationDisplay();

        root.getChildren().addAll(restTypeLabel, restTypeComboBox,
//                dropCheckBox,
                caffeineCheckBox, autoEatCheckBox,
                serverRestartLabel, serverRestartComboBox,
                serverRestartIntervalLabel, serverRestartIntervalComboBox,
                trainingEfficiencyLabel, trainingEfficiencyComboBox,
                cornCookCountLabel, cornCookCountComboBox,
                editSaveButton, startButton, stopButton,
                trainingDurationLabel, trainingDurationField);

        // 设置场景和舞台
        Scene scene = new Scene(root, 500, 480);
        primaryStage.setTitle("SCUM创可贴免费炼体器(作者：GorphynMars)");
        primaryStage.setScene(scene);

        // 加载保存的配置
        loadConfig();
        startTrainingDurationDisplayTimer();

        // 初始状态：禁用所有输入组件
        setInputFieldsEditable(false);

        // 设置窗口关闭事件处理程序
        primaryStage.setOnCloseRequest(this::handleWindowClose);

        primaryStage.show();
        ensureNativeHookRegistered();
    }

    /**
     * 切换编辑模式
     */
    private void toggleEditMode() {
        if (editSaveButton.getText().equals("编辑")) {
            // 切换到编辑模式
            setInputFieldsEditable(true);
            editSaveButton.setText("保存");
        } else {
            // 切换到保存模式
            setInputFieldsEditable(false);
            editSaveButton.setText("编辑");
            saveConfig(); // 保存配置
        }
    }

    /**
     * 创建输入框组
     */
    private void createInputGroup(Pane root, String beforeText, String afterText,
                                  int x, int y, String fieldName) {
        // 创建前置提示标签
        Label promptLabelBefore = new Label(beforeText);
        promptLabelBefore.setLayoutX(x);
        promptLabelBefore.setLayoutY(y - 5);

        // 创建输入框
        TextField inputField = new TextField();
        inputField.setLayoutX(x + 180);
        inputField.setLayoutY(y);
        inputField.setPrefWidth(120);
        inputField.setPromptText("请输入内容");

        // 存储输入框引用
        inputFields.put(fieldName, inputField);

        // 创建后置提示标签
        Label promptLabelAfter = new Label(afterText);
        promptLabelAfter.setLayoutX(x + 310);
        promptLabelAfter.setLayoutY(y);

        // 为输入框添加事件处理
        inputField.textProperty().addListener((observable, oldValue, newValue) -> {
            switch (fieldName) {
                case "drinkWaterAfter":
                    drinkWaterAfter = newValue;
                    break;
                case "recoveryTime":
                    recoveryTime = newValue;
                    break;
            }
        });

        // 将组件添加到面板
        root.getChildren().addAll(promptLabelBefore, inputField, promptLabelAfter);
    }

    /**
     * 设置所有输入框的可编辑状态
     */
    private void setInputFieldsEditable(boolean editable) {
        for (TextField field : inputFields.values()) {
            // 修复：同时设置禁用状态和可编辑状态
            field.setDisable(!editable);
            field.setEditable(editable);
        }
        // 同时设置复选框和下拉框的可编辑状态
        dropCheckBox.setDisable(!editable);
        restTypeComboBox.setDisable(!editable);
        caffeineCheckBox.setDisable(!editable);
        // 设置自动吃饭复选框的可编辑状态
        autoEatCheckBox.setDisable(!editable);
        serverRestartComboBox.setDisable(!editable);
        // Codex生成：重启间隔只允许在编辑模式下修改。
        serverRestartIntervalComboBox.setDisable(!editable);
        trainingEfficiencyComboBox.setDisable(!editable);
        cornCookCountComboBox.setDisable(!editable);
    }

    /**
     * 加载保存的配置
     */
    private void loadConfig() {
        AppConfig config = configService.load();
        drinkWaterAfter = config.getDrinkWaterAfter();
        recoveryTime = config.getRecoveryTime();
        dropInsteadDestroy = config.isDropInsteadDestroy();
        restType = config.getRestType();
        caffeineMg = config.getCaffeineMg();
        enableAutoCaffeine = config.isEnableAutoCaffeine();
        enableAutoEat = config.isEnableAutoEat();
        trainingEfficiency = config.getTrainingEfficiency();
        serverRestartTime = config.getServerRestartTime();
        // Codex生成：加载已保存的服务器重启间隔。
        serverRestartInterval = config.getServerRestartInterval();
        accumulatedTrainingMillis = config.getAccumulatedTrainingMillis();

        updateUIFromConfig();
        updateTrainingDurationDisplay();
    }

    /**
     * 保存配置到文件
     */
    private void saveConfig() {
        AppConfig config = new AppConfig();
        config.setDrinkWaterAfter(drinkWaterAfter);
        config.setRecoveryTime(recoveryTime);
        config.setDropInsteadDestroy(dropInsteadDestroy);
        config.setRestType(restType);
        config.setEnableAutoEat(enableAutoEat);
        config.setTrainingEfficiency(trainingEfficiency);
        config.setCaffeineMg(caffeineMg);
        config.setEnableAutoCaffeine(enableAutoCaffeine);
        config.setServerRestartTime(serverRestartTime);
        // Codex生成：把服务器重启间隔写入配置对象。
        config.setServerRestartInterval(serverRestartInterval);
        config.setAccumulatedTrainingMillis(getCurrentTrainingDurationMillis());

        configService.save(config);
    }

    /**
     * 更新UI中的输入框值
     */
    private void updateUIFromConfig() {
        // 使用存储的输入框引用来设置值
//        inputFields.get("drinkWaterAfter").setText(drinkWaterAfter != null ? drinkWaterAfter : "");
        inputFields.get("recoveryTime").setText(recoveryTime != null ? recoveryTime : "");

        // 更新复选框状态
        if (dropCheckBox != null) {
            dropCheckBox.setSelected(dropInsteadDestroy);
        }

        // 更新下拉框状态
        if (restTypeComboBox != null && restType != null) {
            restTypeComboBox.setValue(restType);
        }

        // 新增：更新咖啡因相关UI状态
        if (caffeineTextField != null) {
            caffeineTextField.setText(caffeineMg != null ? caffeineMg : "");
        }
        if (caffeineCheckBox != null) {
            caffeineCheckBox.setSelected(enableAutoCaffeine);
            // 根据复选框状态设置输入框的可用性
//            caffeineTextField.setDisable(!enableAutoCaffeine);
        }
        // 新增：更新自动吃饭复选框状态
        if (autoEatCheckBox != null) {
            autoEatCheckBox.setSelected(enableAutoEat);
        }
        if (trainingEfficiencyComboBox != null) {
            trainingEfficiencyComboBox.setValue(trainingEfficiency);
        }
        // ===================== 更新服务器重启下拉框 =====================
        if (serverRestartComboBox != null) {
            serverRestartComboBox.setValue(serverRestartTime);
        }
        // Codex生成：将配置中的重启间隔同步到新增下拉框。
        if (serverRestartIntervalComboBox != null) {
            serverRestartIntervalComboBox.setValue(serverRestartInterval);
        }
    }

    /**
     * 窗口关闭事件处理
     */
    private void handleWindowClose(WindowEvent event) {
        stopCook();
        stopTraining(); // 确保线程停止
        stopNearbyItemRobotThread();
        unregisterNativeHook();
        saveConfig();
        if (restartScheduler != null) {
            restartScheduler.shutdownNow();
        }
    }

    /**
     * 开始训练线程
     */
    private void startTraining(String value) {
        // 如果处于编辑模式，先保存配置
        if (editSaveButton.getText().equals("保存")) {
            setInputFieldsEditable(false);
            editSaveButton.setText("编辑");
            saveConfig();
        }

        // 如果线程已经在运行，先停止
        stopTraining();

        try {
            // 将字符串参数转换为double类型
            double drinkWaterAfterValue = Double.parseDouble(drinkWaterAfter);
            double recoveryTimeValue = Double.parseDouble(recoveryTime);

            // 新增：处理咖啡因参数
            Double caffeineMgValue = 0.0;
            if (enableAutoCaffeine && caffeineMg != null && !caffeineMg.isEmpty()) {
                try {
                    caffeineMgValue = Double.valueOf(caffeineMg);
                } catch (NumberFormatException e) {
                    System.err.println("咖啡因参数转换错误: " + e.getMessage());
                }
            }else {
                enableAutoCaffeine = false;
            }
            if (service == null || service.isShutdown()) {
                service = Executors.newSingleThreadExecutor();
            }
            // 创建新的训练线程并传入参数（包括复选框状态和休息类型）
            trainingThread = new xiangzi(
                    recoveryTimeValue,
                    dropInsteadDestroy,  // 传递复选框状态作为mousePositionChange参数
                    restType,            // 传递休息类型参数
                    enableAutoCaffeine,  // 新增：是否启用自动吃咖啡粉
                    caffeineMgValue,      // 新增：当前已吸收咖啡因（毫克）
                    enableAutoEat,      // 新增：传递自动吃饭参数
                    value,              //是否需要切屏
                    trainingEfficiency, // 炼体策略：效率优先或敏捷优先
                    service
            );

            // 启动线程
            trainingThread.start();
            beginTrainingDuration();
            playNotificationSound("/audio/start.mp3");

            scheduleRestartTask();
        } catch (NumberFormatException e) {
            // 处理转换错误
            System.err.println("参数转换错误: " + e.getMessage());
        }
    }

    /**
     * 停止训练线程
     */
    private synchronized void stopTraining() {
        boolean wasTiming = trainingStartedAtNanos >= 0L;
        if (trainingThread != null) {
            trainingThread.setRunning();
            trainingThread.interrupt();
            trainingThread = null;
//            restartScheduler.shutdownNow();
        }

        if (service != null) {
            service.shutdownNow();
            service = null;
        }
        if (cookCornThread != null){
            cookCornThread.interrupt();
        }

        if (wasTiming) {
            finishTrainingDuration();
            saveConfig();
            playNotificationSound("/audio/end.mp3");
        }
    }

    /**
     * Plays a bundled training-state notification on the JavaFX application thread.
     */
    private void playNotificationSound(String resourcePath) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> playNotificationSound(resourcePath));
            return;
        }

        URL audioUrl = Main.class.getResource(resourcePath);
        if (audioUrl == null) {
            System.err.println("Audio resource not found: " + resourcePath);
            return;
        }

        try {
            if (notificationPlayer != null) {
                notificationPlayer.stop();
                notificationPlayer.dispose();
            }

            MediaPlayer player = new MediaPlayer(new Media(audioUrl.toExternalForm()));
            notificationPlayer = player;
            player.setOnEndOfMedia(() -> disposeNotificationPlayer(player));
            player.setOnError(() -> {
                System.err.println("Unable to play audio " + resourcePath + ": "
                        + player.getError());
                disposeNotificationPlayer(player);
            });
            player.play();
        } catch (RuntimeException e) {
            System.err.println("Unable to load audio " + resourcePath + ": " + e.getMessage());
        }
    }

    private void disposeNotificationPlayer(MediaPlayer player) {
        player.dispose();
        if (notificationPlayer == player) {
            notificationPlayer = null;
        }
    }

    private synchronized void beginTrainingDuration() {
        if (trainingStartedAtNanos < 0L) {
            trainingStartedAtNanos = System.nanoTime();
        }
        updateTrainingDurationDisplay();
    }

    private synchronized void finishTrainingDuration() {
        if (trainingStartedAtNanos >= 0L) {
            accumulatedTrainingMillis += TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - trainingStartedAtNanos);
            trainingStartedAtNanos = -1L;
        }
        updateTrainingDurationDisplay();
    }

    private synchronized long getCurrentTrainingDurationMillis() {
        if (trainingStartedAtNanos < 0L) {
            return accumulatedTrainingMillis;
        }
        return accumulatedTrainingMillis + TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - trainingStartedAtNanos);
    }

    private void startTrainingDurationDisplayTimer() {
        trainingDurationTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            xiangzi currentThread = trainingThread;
            if (trainingStartedAtNanos >= 0L && (currentThread == null || !currentThread.isAlive())) {
                stopTraining();
            } else {
                updateTrainingDurationDisplay();
            }
        }));
        trainingDurationTimeline.setCycleCount(Timeline.INDEFINITE);
        trainingDurationTimeline.play();
    }

    private void updateTrainingDurationDisplay() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateTrainingDurationDisplay);
            return;
        }
        if (trainingDurationField == null) {
            return;
        }

        long totalSeconds = getCurrentTrainingDurationMillis() / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        trainingDurationField.setText(String.format("%d小时%02d分钟%02d秒", hours, minutes, seconds));
    }

    private void unregisterNativeHook() {
        synchronized (NATIVE_HOOK_LOCK) {
            if (nativeHookRegistered) {
                try {
                    GlobalScreen.unregisterNativeHook();
                } catch (Exception e) {
                    System.err.println("卸载 NativeHook 失败: " + e.getMessage());
                } finally {
                    nativeHookRegistered = false;
                }
            }
        }
    }

    private void ensureNativeHookRegistered() {
        synchronized (NATIVE_HOOK_LOCK) {
            if (nativeHookRegistered) {
                return;
            }

            try {
                GlobalScreen.registerNativeHook();
                nativeHookRegistered = true;
            } catch (NativeHookException e) {
                System.err.println("NativeHook 注册失败: " + e.getMessage());
                return;
            }

            GlobalScreen.addNativeKeyListener(new NativeKeyListener() {
                @Override
                public void nativeKeyPressed(NativeKeyEvent e) {
                    if (e.getKeyCode() == NativeKeyEvent.VC_DOWN || e.getKeyCode() == NativeKeyEvent.VC_PAGE_DOWN) {
                        System.out.println("停止按钮生效");
                        // 直接从热键线程发出停止信号，不能等待 JavaFX UI 线程处理。
                        stopCook();
                        Platform.runLater(() -> {
                            stopTraining();
                            stopNearbyItemRobotThread();
                        });
                    }
                    if (e.getKeyCode() == NativeKeyEvent.VC_UP || e.getKeyCode() == NativeKeyEvent.VC_PAGE_UP) {
                        System.out.println("pageUp游戏内开始");
                        Platform.runLater(() -> startTraining("inGame"));
                    }
                    if (e.getKeyCode() == NativeKeyEvent.VC_LEFT) {
                        System.out.println("左方向键执行一次附近物品识别+制作简易米饭");
                        startCook();
                    }
                }


                @Override public void nativeKeyReleased(NativeKeyEvent e) {}
                @Override public void nativeKeyTyped(NativeKeyEvent e) {}
            });
        }
    }

    /** 在独立线程中执行多轮烤玉米，避免 join() 阻塞 JavaFX UI 线程。 */
    private synchronized void startCook() {
        if (cookTaskThread != null && cookTaskThread.isAlive()) {
            System.out.println("烤玉米任务正在运行");
            return;
        }

        Thread task = new Thread(() -> {
            try {
                cook();
            } finally {
                synchronized (Main.this) {
                    if (cookTaskThread == Thread.currentThread()) {
                        cookTaskThread = null;
                    }
                }
            }
        }, "scum-cook-controller");
        cookTaskThread = task;
        task.start();
    }

    /** 同时停止 cook() 外层循环和当前正在执行的单轮 Robot 线程。 */
    private void stopCook() {
        Thread task = cookTaskThread;
        if (task != null) {
            task.interrupt();
        }

        cookCornThread currentCookThread = cookCornThread;
        if (currentCookThread != null) {
            currentCookThread.requestStop();
        }
    }
    private void ensureRunning() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
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

    private void cook(){
        //TODO 做米饭的线程
//            nearbyItemRobotThread = new NearbyItemRobotThread(matches);
//            executor.submit(nearbyItemRobotThread);


        try {
            Robot robot = new Robot();
            //打开tab
            robot.keyPress(KeyEvent.VK_TAB);
            safeDelay(50);
            robot.keyRelease(KeyEvent.VK_TAB);
            safeDelay(500);
            //打开1面板
            robot.keyPress(KeyEvent.VK_1);
            safeDelay(50);
            robot.keyRelease(KeyEvent.VK_1);
            safeDelay(500);
            Color infoColor = robot.getPixelColor(330, 58);
            if (infoColor.getRed() > 180){
                robot.mouseMove(330, 58);
                safeDelay(200);
                robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                safeDelay(50);
                robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
                safeDelay(500);
            }
            //打开2面板
            robot.keyPress(KeyEvent.VK_2);
            safeDelay(50);
            robot.keyRelease(KeyEvent.VK_2);
            safeDelay(300);
            robot.mouseMove(965,24);
            safeDelay(300);
            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            safeDelay(50);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
        } catch (Exception e) {
            e.printStackTrace();
        }


        //做烤玉米线程
        for (int i = 0 ; i < cornCookCount && !Thread.currentThread().isInterrupted(); i++) {
            System.out.println(i + "    cornCookCount    " + cornCookCount);
            List<ItemMatch> matches = detectNearbyItemsOnce();
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (matches != null && matches.size() > 0) {
                cookCornThread currentCookThread = new cookCornThread(matches);
                cookCornThread = currentCookThread;
                currentCookThread.start();
                try {
                    currentCookThread.join();
                } catch (InterruptedException e) {
                    currentCookThread.requestStop();
                    Thread.currentThread().interrupt();
                    return;
                } finally {
                    if (cookCornThread == currentCookThread) {
                        cookCornThread = null;
                    }
                }
            }
        }
    }
    private synchronized List<ItemMatch> detectNearbyItemsOnce() {
        try {
            // 第一次按热键时加载配置和模板，后续按键复用同一个检测器实例。
            if (nearbyItemDetector == null) {
                NearbyItemDetectorConfig config = NearbyItemDetectorConfig.load(
                        Path.of("nearby-item-detector.properties"));
                nearbyItemDetector = new NearbyItemDetector(config);
            }
            // 详细结果同时包含最终匹配集合和每个槽位的所有模板分数。
            DetectionResult result =
                    nearbyItemDetector.detectDetailedOnce();
            List<ItemMatch> matches = result.matches();
            EnumSet<ItemType> detectedTypes = EnumSet.noneOf(ItemType.class);

            // 每个槽位只打印一行，展示四种已知物品的相似度。
            for (SlotSimilarity slot : result.slotSimilarities()) {
                if (slot.detectedType() != null) {
                    detectedTypes.add(slot.detectedType());
                }
                System.out.printf(
                        Locale.ROOT,
                        "SLOT -> row=%d col=%d x=%d y=%d "
                                + "panSimilarity=%.3f stoneFireSimilarity=%.3f "
                                + "riceSimilarity=%.3f waterSimilarity=%.3f cornSimilarity=%.3f detected=%s%n",
                        slot.row(), slot.col(), slot.screenX(), slot.screenY(),
                        slot.similarity(ItemType.PAN),
                        slot.similarity(ItemType.STONE_FIRE),
                        slot.similarity(ItemType.RICE),
                        slot.similarity(ItemType.WATER),
                        slot.similarity(ItemType.CORN),
                        slot.detectedType() == null ? "NONE" : slot.detectedType());
            }

            // 每种未识别到的物品都单独打印，避免无法判断是漏打印还是未匹配。
            for (ItemType type : ItemType.values()) {
                if (!detectedTypes.contains(type)) {
                    System.out.printf("%s -> NOT_DETECTED%n", type);
                }
            }

            // 一轮识别结束后，把所有物品结果传给通用 Robot 操作线程。
            stopNearbyItemRobotThread();
            return matches;
        } catch (Exception e) {
            System.err.println("附近物品识别失败: " + e.getMessage());
        }
        return null;
    }

    private synchronized void stopNearbyItemRobotThread() {
        if (nearbyItemRobotThread != null) {
            nearbyItemRobotThread.requestStop();
            nearbyItemRobotThread = null;
        }
        if (cookCornThread != null) {
            cookCornThread.requestStop();
            cookCornThread = null;
        }
    }

    /**
     * 新增：根据服务器重启时间，自动 stop → wait → start
     */
    private synchronized void scheduleRestartTask() {
        if (restartScheduler == null || restartScheduler.isShutdown()) {
            restartScheduler = Executors.newSingleThreadScheduledExecutor();
        }

        // Codex生成：已有检查任务正在运行时直接复用，防止重连再次启动相同任务。
        if (restartCheckTask != null && !restartCheckTask.isCancelled() && !restartCheckTask.isDone()) {
            return;
        }

        restartCheckTask = restartScheduler.scheduleAtFixedRate(() -> {
            LocalTime now = LocalTime.now();
            // Codex生成：每次检查都读取最新下拉框值，修改后无需重建定时任务。
            Integer targetHour = parseRestartHour(serverRestartTime);

            if (targetHour != null && now.getHour() == targetHour && now.getMinute() == 2) {//假设是4点02分已经断开连接了.并且服务器已经重启完了
                Platform.runLater(() -> {
                    stopTraining();
                    Robot robot = null;
                    try {
                        robot = new Robot();
                        robot.mouseMove(513 , 403);
                        robot.delay(300);
                        //这里是点击确定框
                        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                        robot.delay(50);
                        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                        robot.delay(3000);
                        //这里等一会服务器重启，点击继续游戏
                        robot.mouseMove(119 , 396);
                        robot.delay(300);
                        //这里是点击确定框
                        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                        robot.delay(50);
                        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                        //然后就等加载，等后续开始执行startTraining
                    } catch (AWTException e) {
                        throw new RuntimeException(e);
                    }
                    restartScheduler.schedule(
                            () -> Platform.runLater(() -> startTraining("restart")),
                            restartDelayMinutes,
                            TimeUnit.MINUTES
                    );
                    // Codex生成：本次重启处理完成后，推进并保存下一次服务器重启时间。
                    advanceServerRestartTime();
                });
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    /**
     * Codex生成：把下拉框中的重启小时转换为可比较的0～23整数。
     */
    private Integer parseRestartHour(String value) {
        try {
            int hour = Integer.parseInt(value);
            if (hour < 0 || hour > 23) {
                return null;
            }
            return hour;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Codex生成：本次重启逻辑执行完成后，将当前重启小时加上间隔并对24取余。
     * 例如当前为22点、间隔为4小时，新的下一次重启时间就是2点。
     * 计算完成后同步更新下拉框，并立即保存到配置文件。
     */
    private void advanceServerRestartTime() {
        try {
            int currentHour = Integer.parseInt(serverRestartTime);
            int interval = Integer.parseInt(serverRestartInterval);
            if (currentHour < 0 || currentHour > 23 || interval < 0 || interval > 23) {
                return;
            }

            int nextHour = (currentHour + interval) % 24;
            serverRestartTime = String.valueOf(nextHour);
            serverRestartComboBox.setValue(serverRestartTime);
            saveConfig();
        } catch (NumberFormatException e) {
            System.err.println("服务器重启时间或间隔参数错误: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        ConsoleLog.initialize();
        launch(args);
    }
}
