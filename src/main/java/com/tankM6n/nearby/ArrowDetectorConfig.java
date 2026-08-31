// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

import java.awt.Rectangle;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** 箭头单模板识别的搜索区域、匹配阈值和结果坐标偏移配置。 */
public record ArrowDetectorConfig(
        int searchX,
        int searchY,
        int searchWidth,
        int searchHeight,
        String templatePath,
        double similarityThreshold,
        int resultOffsetX,
        int resultOffsetY) {

    public static ArrowDetectorConfig load(Path configPath) throws IOException {
        Path absolutePath = configPath.toAbsolutePath().normalize();
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(absolutePath, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        ArrowDetectorConfig config = new ArrowDetectorConfig(
                integer(properties, "searchX"),
                integer(properties, "searchY"),
                integer(properties, "searchWidth"),
                integer(properties, "searchHeight"),
                resolveTemplatePath(properties, absolutePath),
                decimal(properties, "similarityThreshold"),
                integer(properties, "resultOffsetX"),
                integer(properties, "resultOffsetY"));
        config.validate();
        return config;
    }

    public Rectangle searchArea() {
        return new Rectangle(searchX, searchY, searchWidth, searchHeight);
    }

    private void validate() {
        if (searchWidth <= 0 || searchHeight <= 0) {
            throw new IllegalArgumentException("箭头识别区域宽高必须大于0");
        }
        if (!Double.isFinite(similarityThreshold)
                || similarityThreshold < 0.0
                || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("similarityThreshold 必须在0.0到1.0之间");
        }
    }

    private static String resolveTemplatePath(Properties properties, Path configPath) {
        String value = required(properties, "templatePath");
        if (value.startsWith("classpath:")) {
            return value;
        }
        Path path = Path.of(value);
        if (!path.isAbsolute()) {
            Path parent = configPath.getParent();
            path = parent == null ? path : parent.resolve(path);
        }
        return path.toAbsolutePath().normalize().toString();
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
}
