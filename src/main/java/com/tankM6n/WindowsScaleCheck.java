// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n;

import java.util.Locale;
import java.util.Optional;
import java.util.function.DoubleSupplier;

/** Warns when the Windows output scale of the application window is not 100%. */
final class WindowsScaleCheck implements TrainingStartCheck {
    private static final double SCALE_100_PERCENT = 1.0;
    private static final double SCALE_TOLERANCE = 0.001;

    private final String osName;
    private final DoubleSupplier outputScaleX;
    private final DoubleSupplier outputScaleY;

    WindowsScaleCheck(String osName, DoubleSupplier outputScaleX, DoubleSupplier outputScaleY) {
        this.osName = osName;
        this.outputScaleX = outputScaleX;
        this.outputScaleY = outputScaleY;
    }

    @Override
    public Optional<TrainingStartWarning> validate() {
        if (osName == null || !osName.toLowerCase(Locale.ROOT).contains("windows")) {
            ConsoleLog.log("Windows 缩放校验：当前系统不是 Windows，已跳过");
            return Optional.empty();
        }

        double scaleX = outputScaleX.getAsDouble();
        double scaleY = outputScaleY.getAsDouble();
        String currentScale = formatScale(scaleX, scaleY);
        if (isOneHundredPercent(scaleX) && isOneHundredPercent(scaleY)) {
            ConsoleLog.log("Windows 缩放校验：当前缩放=" + currentScale + "，校验通过");
            return Optional.empty();
        }

        ConsoleLog.log("Windows 缩放校验：当前缩放=" + currentScale
                + "，不是要求的 100%，等待用户确认");
        return Optional.of(new TrainingStartWarning(
                "训练前设置检查",
                "Windows 缩放不是 100%",
                "当前检测到的缩放为 " + currentScale
                        + "。这可能导致鼠标定位不准确。\n\n"
                        + "请在 Windows 显示设置中将缩放调整为 100%，然后点击“确定”；"
                        + "也可以点击“仍要继续”忽略此提示并开始训练。"));
    }

    private static boolean isOneHundredPercent(double scale) {
        return Double.isFinite(scale) && Math.abs(scale - SCALE_100_PERCENT) <= SCALE_TOLERANCE;
    }

    private static String formatScale(double scaleX, double scaleY) {
        if (!Double.isFinite(scaleX) || !Double.isFinite(scaleY)) {
            return "未知";
        }
        long percentX = Math.round(scaleX * 100.0);
        long percentY = Math.round(scaleY * 100.0);
        return percentX == percentY
                ? percentX + "%"
                : percentX + "% × " + percentY + "%";
    }
}
