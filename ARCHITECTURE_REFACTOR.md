# fx17 行为保持型架构重构记录

## 重构范围与原则

本次重构以现有运行行为为基准，采用渐进迁移而非重写。保留了 Spring Boot、JavaFX、JNativeHook、JNA 依赖、AWT Robot、模板匹配算法、像素判断、1024×768 坐标、操作延时、配置文件格式以及 jlink/jpackage 发布链。

重构前已阅读 `pom.xml`、全部 `src/main/java` 源码、资源目录、三份外置 properties、构建脚本和当前线程控制逻辑，并先执行了基线 `mvn clean package`。

## 重构前主要问题

- `Main` 同时负责 JavaFX UI、配置绑定、训练线程、做饭线程、附近物品识别、JNativeHook、Robot 操作和服务器重启调度。
- `xiangzi` 同时负责训练编排、Robot 基础操作、饮食/补水、修理、休息、储物箱操作、咖啡检测、模板配置加载以及多个异步任务。
- `cookCornThread` 同时承担做饭规则、Robot 操作、模板检测和内部定时检测；外层多轮线程及停止逻辑又由 `Main` 管理。
- 同一个业务的 `Thread`、`Future`、`ExecutorService` 和运行状态分散在不同类中。
- 做饭材料为空、迭代器耗尽和检测失败时存在明确的空指针、越界或无限重试风险。
- `NearbyItemRobotThread` 是整文件注释掉的历史实现，`coffeeCheckDomin` 在职责迁移后不再有引用。

## 最终包结构

```text
com.tankM6n
├── Main                         JavaFX Application、UI 初始化和应用组装
├── DemoMain                     Spring Boot + JavaFX 入口（保持不变）
├── AppConfig / ConfigService    兼容原 properties 的配置模型与读写
├── xiangzi                      旧类名兼容适配器
├── cooking
│   ├── CookingService           多轮做饭、识别器及停止生命周期
│   └── CookingWorker            单轮烤玉米/烤鱼流程
├── game
│   └── GameRobot                通用键鼠、截图与可中断延时边界
├── hotkey
│   └── GlobalHotkeyService      JNativeHook 注册、卸载和按键映射
├── restart
│   └── RestartScheduler         重启检查、重连任务及 generation 取消语义
├── training
│   ├── TrainingService          训练线程及咖啡检测线程池生命周期
│   ├── TrainingSettings         单次训练不可变配置快照
│   ├── TrainingWorker           一轮训练的业务顺序编排
│   ├── TrainingRobot            训练专用 Robot 与停止检查
│   ├── TrainingDetectionService 训练所需模板配置及检测器缓存
│   ├── FoodService              饮食、营养判断、补水和营养恢复
│   ├── RepairService            修手套和修鞋
│   ├── RestService              切屏、站起、坐下/趴下恢复
│   ├── InventoryService         物品栏定位和水桶搬运
│   ├── StorageService           箱子摧毁、咖啡检测和跨轮状态
│   └── StorageItemMatch         储物对象检测结果
└── nearby
    ├── NearbyItemDetector       附近物品识别入口
    ├── NearbyGrid               网格坐标计算
    ├── ImageProcessor           裁剪、灰度、缩放
    ├── TemplateMatcher          灰度绝对差匹配
    ├── RegionTemplateDetector   区域滑动模板检测
    ├── ScreenCapture            检测截图边界
    └── cookCornThread           旧类名兼容适配器
```

`nearby` 现有检测类的职责边界和算法没有为了目录统一而重写或大范围搬迁。
`Main` 和 `ConfigService` 暂时保留原包名，以避免无业务收益的入口类、Spring Boot 元数据和历史引用改名；职责已经通过服务边界迁出。

## 核心类职责

核心类规模变化（行数用于说明职责迁移，不作为设计目标）：

| 类 | 重构前 | 重构后 |
|---|---:|---:|
| `Main` | 1290 | 886 |
| `xiangzi` | 1515 | 25（兼容适配器）；活跃编排 `TrainingWorker` 202 |
| `cookCornThread` | 377 | 17（兼容适配器）；单轮实现 `CookingWorker` 368，生命周期移至 `CookingService` |

### Main

- JavaFX `Application` 生命周期和控件初始化。
- 组装 `TrainingService`、`CookingService`、`RestartScheduler` 和 `GlobalHotkeyService`。
- 将 UI 值转换为 `TrainingSettings`，调用业务服务的 `start/stop`。
- 保留 UI 相关的配置绑定、提示音和训练时长展示。
- 不再直接创建 Robot、训练线程、做饭线程、附近检测器或重启线程池。

### TrainingWorker

训练核心只表达原有顺序：

1. 根据启动模式切屏或等待重连。
2. 检查聊天静音。
3. 站起。
4. 修手套。
5. 检查饮食并按需补水/恢复。
6. 修鞋。
7. 执行本轮储物箱摧毁。
8. 坐下或趴下恢复。

具体坐标动作由对应业务服务与 `TrainingRobot` 承担。

### CookingService / CookingWorker

- `CookingService` 负责多轮控制线程、`NearbyItemDetector` 复用、当前 `CookingWorker` 引用和停止清理。
- `CookingWorker` 只负责一轮原材料装锅、开始烹饪、等待完成和取出食物。
- 历史 `cookCornThread` 只作为兼容构造器存在，不再拥有业务实现。

### GameRobot / TrainingRobot

- `GameRobot` 提供做饭与重启模块使用的通用键鼠、像素、截图和可中断延时。
- `TrainingRobot` 在相同底层能力上保留训练使用的 `running + interrupt` 双重停止检查。
- 本次只迁移涉及的能力，没有为追求统一而批量改写所有坐标。

## 线程生命周期归属

| 所有者 | 创建/持有 | 停止与清理 |
|---|---|---|
| `TrainingService` | `TrainingWorker`、咖啡检测单线程 `ExecutorService` | `requestStop()`、`interrupt()`、`shutdownNow()`；自然结束回调也关闭线程池 |
| `FoodService` | 强制营养恢复期间的局部 `ScheduledExecutorService` | `latch.await()` 的 `finally` 中 `shutdownNow()` |
| `CookingService` | 多轮 `scum-cook-controller`、当前 `CookingWorker` | 中断外层线程并调用 worker `requestStop()` |
| `CookingWorker` | 做饭状态轮询的局部 `ScheduledExecutorService` | `latch.await()` 的 `finally` 中 `shutdownNow()` |
| `RestartScheduler` | 重启检查任务、延迟恢复任务、单线程调度池 | generation 失效、`Future.cancel(true)`、`shutdownNow()` |
| `Main` | JavaFX 训练时长 `Timeline` | 窗口关闭时显式 `stop()`，不参与业务线程所有权 |

`Future` 不再由 `Main` 保存；训练咖啡检测 Future 只在 `StorageService` 内创建和消费。

## 重要状态归属

| 状态 | 唯一所有者 |
|---|---|
| 当前训练 worker、咖啡检测 executor | `TrainingService` |
| 训练停止标志 `running` | `TrainingWorker` |
| 营养恢复需求 | `FoodService` |
| 咖啡边界、最后摧毁时长、是否跳过第四次、每轮咖啡限制 | `StorageService` |
| 当前做饭控制线程和当前单轮 worker | `CookingService` |
| 做饭状态轮询计数 | `CookingWorker` |
| 重启 enabled、generation、检查/恢复 Future | `RestartScheduler` |
| UI 配置值和累计训练时长展示 | `Main` / `AppConfig` |

## 原职责迁移

### Main 原职责

- 训练创建/停止、辅助线程池 → `TrainingService`
- 做饭外层循环、附近识别、当前做饭线程 → `CookingService`
- JNativeHook 注册和按键分发 → `GlobalHotkeyService`
- 重启检查、generation、自动恢复 Future、重连 Robot 操作 → `RestartScheduler`

### xiangzi 原职责

- 主流程 → `TrainingWorker`
- 模板配置与检测器缓存 → `TrainingDetectionService`
- 饮食、补水、营养恢复 → `FoodService`
- 修手套、修鞋 → `RepairService`
- 站起、切屏、体力恢复 → `RestService`
- 物品栏和水桶转移 → `InventoryService`
- 储物箱、摧毁时间、咖啡检测 → `StorageService`
- Robot 停止检查和基础操作 → `TrainingRobot`

历史 `xiangzi` 类保留为 25 行兼容适配器，已有构造调用仍可编译。

### cookCornThread 原职责

- 多轮生命周期和附近识别 → `CookingService`
- 单轮烹饪动作 → `CookingWorker`
- 通用 Robot 能力 → `GameRobot`

历史 `cookCornThread` 类保留为兼容适配器。

## 本次明确修复的 bug

- 做饭未识别到所选原材料时，原代码会对 `null` 调用 `size()`；现在按空集合处理并结束本轮。
- 原材料迭代器在锅槽多于剩余材料时直接 `next()`；现在先检查 `hasNext()`。
- 平底锅识别异常曾返回 `null`，且未识别到锅时外层循环可能永远不消耗材料；现在返回空集合并明确结束本轮。
- 附近物品识别失败曾向调用方返回 `null`；现在返回空集合。
- 储物对象识别异常时 `Optional` 曾保持为 `null`；现在返回 `Optional.empty()`。
- 训练 worker 自然结束后，辅助检测线程池现在由 `TrainingService` 立即关闭，不再依赖 UI 定时器稍后清理。
- 窗口关闭时显式停止训练时长 `Timeline` 并释放 `MediaPlayer`。
- 删除了无引用的 `coffeeCheckDomin` 和整文件注释掉的 `NearbyItemRobotThread`；二者均可从 Git 历史恢复。

## 刻意没有修改的内容

- 所有既有鼠标坐标、键盘顺序和 delay 数值。
- `TemplateMatcher`、`RegionTemplateDetector`、`NearbyItemDetector` 的识别算法和阈值。
- `fitness_config.properties`、`nearby-item-detector.properties`、`arrow-detector.properties` 的文件名、key 和兼容格式。
- 图片、音频和 `JNativeHook.dll` 的资源路径。
- Spring Boot 入口以及 JNativeHook/JNA 依赖。
- `build-exe.cmd` 的 Maven → jlink → DLL 复制 → jpackage → 外置配置复制流程。
- 当前业务中已经存在但本次不宜改变的规则，例如坐标固定为 1024×768、自动吃饭现行策略和最后一次摧毁时间判定细节。
- 没有引入 OpenCV、状态机、大型框架或批量 `Manager/Helper/Util` 抽象。

## 测试与验证

新增纯逻辑测试：

- `TemplateMatcherTest`：完全相同、黑白完全不同、尺寸不一致。
- `NearbyGridTest`：首格中心、末格中心、负数/行列越界。

最终验证结果：

- `mvn clean package`：通过；编译 41 个主源码文件和 2 个测试文件，6 项测试全部通过。
- Spring Boot 胖 JAR：生成成功；manifest 的 `Start-Class` 仍为 `com.tankM6n.DemoMain`。
- 胖 JAR 仍包含 JNA、JNA Platform 和 JNativeHook 依赖；JavaFX 依旧由 jlink runtime 提供，没有重复打入胖 JAR。
- `build-exe.cmd`：通过；jlink、JNativeHook.dll 复制、jpackage 和三份外置配置复制均成功。
- `target/scumRoadTo855/scumRoadTo855.exe`：已生成。
- `target/scumRoadTo855/runtime/bin/JNativeHook.dll`：存在。
- 三份外置 properties 与项目根目录源文件的 SHA-256 一致。
- 胖 JAR 中的图片、音频和 `JNativeHook.dll` 资源存在。
- 打包程序启动冒烟：隐藏启动后进程持续存活，日志确认 Java 17.0.12 下 Spring Boot `DemoMain` 正常启动；随后仅停止该测试进程。

## 后续建议（本次不自动实施）

1. 在真实 1024×768 游戏环境做一次人工冒烟测试并录制关键坐标截图基线。
2. 后续只在修改相关功能时逐步引入 `GameCoordinates`，不要一次性搬迁全部坐标。
3. 为配置值增加独立的解析/校验对象，继续减少 UI 中的字符串转换，但保持 properties key 不变。
4. 若未来做饭类型继续增加，可再把菜谱选择和槽位布局提取为小型值对象；当前两种菜谱不需要更复杂的模式层。
5. 为 `TrainingService`、`CookingService` 增加使用假 worker 的生命周期测试，避免 mock AWT Robot。
