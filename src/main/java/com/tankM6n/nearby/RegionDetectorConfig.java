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

/** 从 properties 中按前缀加载区域模板识别配置。 */
public record RegionDetectorConfig(
        int searchX,
        int searchY,
        int searchWidth,
        int searchHeight,
        String templatePath,
        double similarityThreshold,
        int resultOffsetX,
        int resultOffsetY) {

    public static RegionDetectorConfig load(Path configPath, String prefix) throws IOException {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("识别配置前缀不能为空");
        }

        Path absolutePath = configPath.toAbsolutePath().normalize();
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(absolutePath, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        RegionDetectorConfig config = new RegionDetectorConfig(
                integer(properties, prefix + "SearchX"),
                integer(properties, prefix + "SearchY"),
                integer(properties, prefix + "SearchWidth"),
                integer(properties, prefix + "SearchHeight"),
                resolveTemplatePath(properties, prefix + "TemplatePath", absolutePath),
                decimal(properties, prefix + "SimilarityThreshold"),
                integer(properties, prefix + "ResultOffsetX"),
                integer(properties, prefix + "ResultOffsetY"));
        config.validate(prefix);
        return config;
    }

    public Rectangle searchArea() {
        return new Rectangle(searchX, searchY, searchWidth, searchHeight);
    }

    private void validate(String prefix) {
        if (searchWidth <= 0 || searchHeight <= 0) {
            throw new IllegalArgumentException(prefix + " 识别区域宽高必须大于 0");
        }
        if (!Double.isFinite(similarityThreshold)
                || similarityThreshold < 0.0
                || similarityThreshold > 1.0) {
            throw new IllegalArgumentException(
                    prefix + "SimilarityThreshold 必须在 0.0 到 1.0 之间");
        }
    }

    private static String resolveTemplatePath(
            Properties properties, String key, Path configPath) {
        String value = required(properties, key);
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
