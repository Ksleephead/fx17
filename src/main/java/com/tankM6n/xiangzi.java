// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n;

import com.tankM6n.training.TrainingWorker;

import java.util.concurrent.ExecutorService;

/**
 * @deprecated 训练实现已迁移到 {@link TrainingWorker}；保留旧类名兼容历史调用。
 */
@Deprecated
public final class xiangzi extends TrainingWorker {
    public xiangzi(double recoveryTime, double timePerHit,
                   boolean dropInsteadDestroy, String restType,
                   boolean enableAutoCaffeine, double caffeineMgValue,
                   boolean enableAutoEat,
                   String insideGameOrNot, String trainingEfficiency,
                   ExecutorService executor) {
        super(recoveryTime, timePerHit, dropInsteadDestroy, restType,
                enableAutoCaffeine, caffeineMgValue, enableAutoEat,
                insideGameOrNot, trainingEfficiency,
                executor);
    }
}
