// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** 附近物品网格、图标裁剪区域和模板路径配置。 */
public record NearbyItemDetectorConfig(
        int nearbyX,
        int nearbyY,
        int nearbyWidth,
        int nearbyHeight,
        int rows,
        int cols,
        int slotWidth,
        int slotHeight,
        int slotGapX,
        int slotGapY,
        int panIconOffsetX,
        int panIconOffsetY,
        int stoneFireIconOffsetX,
        int stoneFireIconOffsetY,
        int positionSearchRadius,
        int iconWidth,
        int iconHeight,
        String panTemplatePath,
        String stoneFireTemplatePath,
        double panSimilarityThreshold,
        double stoneFireSimilarityThreshold) {

    public static NearbyItemDetectorConfig load(Path configPath) throws IOException {
        Path absoluteConfigPath = configPath.toAbsolutePath().normalize();
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(absoluteConfigPath, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        NearbyItemDetectorConfig config = new NearbyItemDetectorConfig(
                integer(properties, "nearbyX"),
                integer(properties, "nearbyY"),
                integer(properties, "nearbyWidth"),
                integer(properties, "nearbyHeight"),
                integer(properties, "rows"),
                integer(properties, "cols"),
                integer(properties, "slotWidth"),
                integer(properties, "slotHeight"),
                integer(properties, "slotGapX"),
                integer(properties, "slotGapY"),
                integer(properties, "panIconOffsetX"),
                integer(properties, "panIconOffsetY"),
                integer(properties, "stoneFireIconOffsetX"),
                integer(properties, "stoneFireIconOffsetY"),
                integer(properties, "positionSearchRadius"),
                integer(properties, "iconWidth"),
                integer(properties, "iconHeight"),
                resolveTemplateLocation(properties, "panTemplatePath", absoluteConfigPath),
                resolveTemplateLocation(properties, "stoneFireTemplatePath", absoluteConfigPath),
                decimal(properties, "panSimilarityThreshold"),
                decimal(properties, "stoneFireSimilarityThreshold"));
        config.validate();
        return config;
    }

    /** classpath 路径保持原样，普通相对路径以配置文件所在目录为基准。 */
    private static String resolveTemplateLocation(
            Properties properties, String key, Path absoluteConfigPath) {
        String location = required(properties, key);
        if (location.startsWith("classpath:")) {
            return location;
        }

        Path templatePath = Path.of(location);
        if (!templatePath.isAbsolute()) {
            Path parent = absoluteConfigPath.getParent();
            templatePath = parent == null ? templatePath : parent.resolve(templatePath);
        }
        return templatePath.toAbsolutePath().normalize().toString();
    }

    private void validate() {
        requirePositive("nearbyWidth", nearbyWidth);
        requirePositive("nearbyHeight", nearbyHeight);
        requirePositive("rows", rows);
        requirePositive("cols", cols);
        requirePositive("slotWidth", slotWidth);
        requirePositive("slotHeight", slotHeight);
        requirePositive("iconWidth", iconWidth);
        requirePositive("iconHeight", iconHeight);
        requireNonNegative("slotGapX", slotGapX);
        requireNonNegative("slotGapY", slotGapY);
        requireNonNegative("positionSearchRadius", positionSearchRadius);
        validateIconArea("pan", panIconOffsetX, panIconOffsetY);
        validateIconArea("stoneFire", stoneFireIconOffsetX, stoneFireIconOffsetY);

        long gridWidth = (long) cols * slotWidth + (long) (cols - 1) * slotGapX;
        long gridHeight = (long) rows * slotHeight + (long) (rows - 1) * slotGapY;
        if (gridWidth > nearbyWidth || gridHeight > nearbyHeight) {
            throw new IllegalArgumentException(
                    "网格超出 nearby 区域: grid=" + gridWidth + "x" + gridHeight
                            + ", nearby=" + nearbyWidth + "x" + nearbyHeight);
        }

        validateThreshold("panSimilarityThreshold", panSimilarityThreshold);
        validateThreshold("stoneFireSimilarityThreshold", stoneFireSimilarityThreshold);
    }

    /** 不同物品在槽位内的位置不同，因此分别验证各自的裁剪偏移。 */
    private void validateIconArea(String name, int offsetX, int offsetY) {
        requireNonNegative(name + "IconOffsetX", offsetX);
        requireNonNegative(name + "IconOffsetY", offsetY);
        // 搜索范围内的每一个候选裁剪框都必须完整位于槽位中。
        if (offsetX - positionSearchRadius < 0
                || offsetY - positionSearchRadius < 0
                || (long) offsetX + positionSearchRadius + iconWidth > slotWidth
                || (long) offsetY + positionSearchRadius + iconHeight > slotHeight) {
            throw new IllegalArgumentException(
                    name + " icon 搜索区域必须完全位于单个槽位内");
        }
    }

    private static void validateThreshold(String name, double threshold) {
        if (!Double.isFinite(threshold) || threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException(name + " 必须在 0.0 到 1.0 之间");
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少配置项: " + key);
        }
        return value.trim();
    }

    private static int integer(Properties properties, String key) {
        try {
            return Integer.parseInt(required(properties, key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("配置项 " + key + " 必须是整数", e);
        }
    }

    private static double decimal(Properties properties, String key) {
        try {
            return Double.parseDouble(required(properties, key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("配置项 " + key + " 必须是小数", e);
        }
    }

    private static void requirePositive(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " 必须大于 0");
        }
    }

    private static void requireNonNegative(String name, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " 不能小于 0");
        }
    }
}
