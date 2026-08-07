// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigService {
    private static final String CONFIG_FILE = "fitness_config.properties";

    public AppConfig load() {
        AppConfig config = new AppConfig();
        Properties prop = new Properties();

        try (FileInputStream input = new FileInputStream(CONFIG_FILE)) {
            prop.load(input);
        } catch (IOException ex) {
            return config;
        }

        config.setRestAfterHits(prop.getProperty("restAfterHits", ""));
        config.setRepairGlovesAfter(prop.getProperty("repairGlovesAfter", ""));
        config.setDrinkWaterAfter(prop.getProperty("drinkWaterAfter", ""));
        config.setTimePerHit(prop.getProperty("timePerHit", ""));
        config.setRecoveryTime(prop.getProperty("recoveryTime", ""));
        config.setDropInsteadDestroy(Boolean.parseBoolean(prop.getProperty("dropInsteadDestroy", "false")));
        config.setRestType(prop.getProperty("restType", "趴下"));
        config.setCaffeineMg(prop.getProperty("caffeineMg", ""));
        config.setEnableAutoCaffeine(Boolean.parseBoolean(prop.getProperty("enableAutoCaffeine", "false")));
        config.setEnableAutoEat(Boolean.parseBoolean(prop.getProperty("enableAutoEat", "false")));
        config.setServerRestartTime(prop.getProperty("serverRestartTime", "12"));
        config.setAccumulatedTrainingMillis(parseNonNegativeLong(
                prop.getProperty("accumulatedTrainingMillis", "0")));
        // Codex生成：读取重启间隔；兼容没有该字段的旧配置。
        config.setServerRestartInterval(prop.getProperty("serverRestartInterval", "6"));

        return config;
    }

    public void save(AppConfig config) {
        Properties prop = new Properties();

        prop.setProperty("restAfterHits", valueOrEmpty(config.getRestAfterHits()));
        prop.setProperty("repairGlovesAfter", valueOrEmpty(config.getRepairGlovesAfter()));
        prop.setProperty("drinkWaterAfter", valueOrEmpty(config.getDrinkWaterAfter()));
        prop.setProperty("timePerHit", valueOrEmpty(config.getTimePerHit()));
        prop.setProperty("recoveryTime", valueOrEmpty(config.getRecoveryTime()));
        prop.setProperty("dropInsteadDestroy", Boolean.toString(config.isDropInsteadDestroy()));
        prop.setProperty("restType", valueOrDefault(config.getRestType(), "趴下"));
        prop.setProperty("enableAutoEat", Boolean.toString(config.isEnableAutoEat()));
        prop.setProperty("caffeineMg", valueOrEmpty(config.getCaffeineMg()));
        prop.setProperty("enableAutoCaffeine", Boolean.toString(config.isEnableAutoCaffeine()));
        prop.setProperty("serverRestartTime", valueOrDefault(config.getServerRestartTime(), "12"));
        prop.setProperty("accumulatedTrainingMillis",
                Long.toString(config.getAccumulatedTrainingMillis()));
        // Codex生成：保存服务器重启间隔，供下次启动程序时继续使用。
        prop.setProperty("serverRestartInterval", valueOrDefault(config.getServerRestartInterval(), "0"));

        try (FileOutputStream output = new FileOutputStream(CONFIG_FILE)) {
            prop.store(output, "Fitness Configuration");
            System.out.println("配置已保存");
        } catch (IOException io) {
            System.err.println("保存配置失败: " + io.getMessage());
        }
    }

    private String valueOrEmpty(String value) {
        return value != null ? value : "";
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }

    private long parseNonNegativeLong(String value) {
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
