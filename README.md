# fx17

scum炼体软件

## 构建环境

- JDK 17
- Maven

## 许可证

Copyright (C) 2026 Ksleephead

本项目以 **GNU General Public License v3.0 only**（`GPL-3.0-only`）发布。完整条款见 [LICENSE](LICENSE)。

你可以运行、研究、修改、分发以及收费分发本项目，但分发本项目或其衍生作品时，必须遵守 GPL v3 的要求，包括提供相应源代码并继续采用 GPL v3。

本项目所使用的第三方依赖仍分别适用其各自的许可证。

## 附近物品图像识别（独立入口）

当前支持 `pan.png`、`stoneFire.png`、`rice.png` 和 `water.png`
四个 classpath 模板。先校准
`nearby-item-detector.properties`，然后执行：

```powershell
mvn compile
java -cp target/classes com.tankM6n.nearby.NearbyItemDetector nearby-item-detector.properties
```

识别器执行一次“附近”区域截图，每个槽位选择相似度最高且超过阈值的
已知物品模板，返回物品类型和屏幕中心坐标。

在主程序中，每按一次 `←` 会执行一轮附近物品识别，不会启动后台定时任务。
