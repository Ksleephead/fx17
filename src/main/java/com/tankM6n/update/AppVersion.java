// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.update;

import com.tankM6n.ConsoleLog;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppVersion {
    private static final String VERSION_RESOURCE = "/app-version.properties";
    public static final String CURRENT_VERSION = loadVersion();

    private AppVersion() {
    }

    private static String loadVersion() {
        Properties properties = new Properties();
        try (InputStream input = AppVersion.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (input == null) {
                throw new IOException("找不到版本资源 " + VERSION_RESOURCE);
            }
            properties.load(input);
            String version = properties.getProperty("app.version");
            if (version == null || version.isBlank()) {
                throw new IOException("版本资源中缺少 app.version");
            }
            return version.trim();
        } catch (IOException | RuntimeException exception) {
            ConsoleLog.log("读取应用版本失败，使用安全回退版本 0.0.0：" + exception.getMessage());
            return "0.0.0";
        }
    }
}
