# fx17 · SCUM 创可贴免费炼体器

面向 Windows 的 SCUM 炼体与做饭工具，使用 JavaFX 提供界面，通过屏幕识别和模拟键鼠操作执行游戏内流程。

主要功能：

- 自动训练，以及训练过程中的饮食、补水、装备修理和休息。
- 烤玉米、烤鱼，可在界面选择制作次数。
- 按服务器重启时间暂停训练，并在延迟后自动恢复。
- 全局快捷键、配置保存、训练时长显示和更新检查。

## 使用准备

程序依赖固定坐标和图像模板。开始前请按以下方式设置：

1. 将游戏设置为 **全屏 1024 × 768**，将 Windows 显示缩放设置为 **100%**。
2. 关闭所有游戏画面滤镜，避免影响像素判断和模板匹配。
3. 按下表放置快捷栏物品。
4. 打开程序，点击“编辑”调整训练参数，保存后切回游戏再使用快捷键启动。

| 游戏快捷栏 | 物品 |
| --- | --- |
| 4 | 烤玉米 |
| 6 | 鞋 |
| 7 | 建筑手套 |
| 8 | 装咖啡的瓶子 |
| 9 | 烤鱼 |
| 0 | 鲍勃糖 |

砸箱子时间默认 **32 秒**。如果出现箱子被砸坏或角色晕倒，可适当缩短；无特殊情况保持默认值。

## 操作方式

| 操作 | 全局快捷键 |
| --- | --- |
| 在游戏中开始训练 | `↑` 或 `Page Up` |
| 停止训练、停止做饭，并取消待执行的重启恢复任务 | `↓` 或 `Page Down` |
| 按界面所选类型和次数开始做饭 | `←` |

界面也提供“开始训练”和“停止训练”按钮。“停止训练”按钮同时取消重启调度；需要同时停止做饭时，使用全局停止快捷键。

做饭流程会打开相应面板，并在每轮制作前识别附近物品。`←` 启动的是完整做饭流程；仅调试识别时，请使用下文的独立识别入口。

### 服务器重启与恢复

在界面填写下一次服务器重启的小时（24 小时制）及重启间隔。训练启动后，程序每分钟检查一次本机时间：

1. 到达目标小时的第 **2 分钟**时，暂停训练并执行游戏界面点击。
2. 点击完成后等待 **2 分钟**，再发起恢复训练。
3. 安排恢复任务后，按 `(当前重启小时 + 重启间隔) % 24` 更新下一次重启小时，并保存配置。

例如，当前设置为 22 点、间隔为 4 小时，推进后的时间为次日 2 点。手动停止训练或使用全局停止快捷键，会取消尚未执行的自动恢复。

## 配置文件

以下三份文件由打包脚本复制到应用目录，运行时按工作目录中的相对路径读取。移动程序时请保留完整应用目录；从命令行启动时，请先切换到应用目录。

| 文件 | 用途 |
| --- | --- |
| [fitness_config.properties](fitness_config.properties) | 训练参数、重启时间与间隔、累计训练时长等；日常参数优先通过界面修改 |
| [nearby-item-detector.properties](nearby-item-detector.properties) | 附近物品栏截图范围、网格、物品模板与阈值，以及修理、饮水等菜单识别区域 |
| [arrow-detector.properties](arrow-detector.properties) | 箭头模板搜索区域、匹配阈值及结果坐标偏移 |

图像模板位于 [src/main/resources/image](src/main/resources/image)，配置中的 `classpath:/image/...` 表示读取 JAR 内的资源。调整识别参数后建议重启程序，以便重新加载检测器配置。

## 开发与构建

### 环境要求

- Windows。
- JDK 17，包含 `jlink` 和 `jpackage`。
- Maven，可在命令行执行 `mvn`。
- JavaFX 17：IDE 运行需要 JavaFX 模块；打包需要 JavaFX JMODs。

在项目根目录执行：

```powershell
mvn test
mvn clean package
```

打包后的 Spring Boot JAR 位于 `target/dist/fx17.jar`。JavaFX 依赖声明为 `provided`，由打包运行时提供；单独使用普通 JDK 执行 `java -jar` 仍需额外配置 JavaFX 模块。

使用 IDE 调试主程序时，运行 `com.tankM6n.DemoMain`，将工作目录设为项目根目录，并在 VM 参数中配置本机 JavaFX SDK 路径：

```text
--module-path "你的 JavaFX SDK 目录/lib" --add-modules javafx.controls,javafx.media
```

### 构建 Windows 应用

先修改 [build-exe.cmd](build-exe.cmd) 中的 `JDKHOME` 和 `JAVAFX_HOME`，分别指向本机 JDK 17 和 JavaFX JMODs 目录，然后执行：

```powershell
.\build-exe.cmd
```

脚本依次执行 Maven 打包、`jlink` 裁剪运行时、`jpackage` 生成应用目录，并复制外置配置。产物入口为：

```text
target/scumRoadTo855/scumRoadTo855.exe
```

分发时应保留整个 `target/scumRoadTo855` 目录，其中包含运行时和配置文件，使用者无需另装 JDK。`bulid-exe.cmd` 是兼容旧拼写的转发入口，统一使用 `build-exe.cmd` 即可。

### 独立调试附近物品识别

将游戏停留在需要识别的“附近”物品栏界面，按实际画面校准 `nearby-item-detector.properties`，再从项目根目录执行：

```powershell
mvn compile
java -cp target/classes com.tankM6n.nearby.NearbyItemDetector nearby-item-detector.properties
```

该入口只截图并识别一次，在控制台输出各槽位的相似度信息，不执行做饭操作。当前支持六种模板：`pan.png`、`stoneFire.png`、`rice.png`、`water.png`、`corn.png`、`fish.png`。匹配时，每个槽位选择超过阈值且相似度最高的已知物品，识别结果包含物品类型和屏幕中心坐标。

### 代码入口

| 位置 | 职责 |
| --- | --- |
| `DemoMain` / `Main` | 应用启动、界面与服务组装 |
| `training` | 训练编排、饮食、修理、休息与储物操作 |
| `cooking` | 做饭轮次控制和单轮制作流程 |
| `nearby` | 截图、网格定位与图像模板匹配 |
| `restart/RestartScheduler` | 重启检查、延迟恢复和任务取消 |
| `hotkey/GlobalHotkeyService` | 全局快捷键注册与映射 |
| `update` | 版本检查与更新界面 |

更详细的重构背景与职责说明见 [架构重构记录](ARCHITECTURE_REFACTOR.md)。

## 常见问题

- **鼠标点击位置偏移**：先检查全屏分辨率和 Windows 缩放，再检查界面布局与配置坐标。
- **附近物品识别不到**：关闭滤镜，确认物品在截图范围内，用独立入口查看相似度，再校准网格、图标偏移和阈值。
- **快捷键无效**：查看启动输出中的 `NativeHook 注册失败` 等信息；打包时留意 `JNativeHook.dll` 缺失提示。
- **移动程序后配置丢失或识别失败**：确认保留了三份外置配置，并检查启动时的工作目录。主程序启动日志会打印日志文件位置及工作目录，可据此排查。

## 许可证

Copyright (C) 2026 Ksleephead

本项目以 **GNU General Public License v3.0 only**（`GPL-3.0-only`）发布，完整条款见 [LICENSE](LICENSE)。第三方依赖分别适用其各自的许可证。
