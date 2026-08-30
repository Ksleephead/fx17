// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n;

public class AppConfig {
    private String drinkWaterAfter = "";
    private String recoveryTime = "";
    private String timePerHit = "32";
    private boolean dropInsteadDestroy;
    private String restType = "趴下";
    private String caffeineMg = "";
    private boolean enableAutoCaffeine;
    private boolean enableAutoEat;
    private String trainingEfficiency = "效率优先";
    private String serverRestartTime = "12";
    private long accumulatedTrainingMillis;
    // Codex生成：服务器每次重启完成后，用该间隔计算下一次重启时间。
    private String serverRestartInterval = "0";

    public String getDrinkWaterAfter() {
        return drinkWaterAfter;
    }

    public void setDrinkWaterAfter(String drinkWaterAfter) {
        this.drinkWaterAfter = drinkWaterAfter;
    }

    public String getRecoveryTime() {
        return recoveryTime;
    }

    public void setRecoveryTime(String recoveryTime) {
        this.recoveryTime = recoveryTime;
    }

    public String getTimePerHit() {
        return timePerHit;
    }

    public void setTimePerHit(String timePerHit) {
        this.timePerHit = timePerHit;
    }

    public boolean isDropInsteadDestroy() {
        return dropInsteadDestroy;
    }

    public void setDropInsteadDestroy(boolean dropInsteadDestroy) {
        this.dropInsteadDestroy = dropInsteadDestroy;
    }

    public String getRestType() {
        return restType;
    }

    public void setRestType(String restType) {
        this.restType = restType;
    }

    public String getCaffeineMg() {
        return caffeineMg;
    }

    public void setCaffeineMg(String caffeineMg) {
        this.caffeineMg = caffeineMg;
    }

    public boolean isEnableAutoCaffeine() {
        return enableAutoCaffeine;
    }

    public void setEnableAutoCaffeine(boolean enableAutoCaffeine) {
        this.enableAutoCaffeine = enableAutoCaffeine;
    }

    public boolean isEnableAutoEat() {
        return enableAutoEat;
    }

    public void setEnableAutoEat(boolean enableAutoEat) {
        this.enableAutoEat = enableAutoEat;
    }

    public String getTrainingEfficiency() {
        return trainingEfficiency;
    }

    public void setTrainingEfficiency(String trainingEfficiency) {
        this.trainingEfficiency = "敏捷优先".equals(trainingEfficiency)
                ? "敏捷优先"
                : "效率优先";
    }

    public String getServerRestartTime() {
        return serverRestartTime;
    }

    public void setServerRestartTime(String serverRestartTime) {
        this.serverRestartTime = serverRestartTime;
    }

    public String getServerRestartInterval() {
        return serverRestartInterval;
    }

    public void setServerRestartInterval(String serverRestartInterval) {
        this.serverRestartInterval = serverRestartInterval;
    }

    public long getAccumulatedTrainingMillis() {
        return accumulatedTrainingMillis;
    }

    public void setAccumulatedTrainingMillis(long accumulatedTrainingMillis) {
        this.accumulatedTrainingMillis = Math.max(0L, accumulatedTrainingMillis);
    }
}
