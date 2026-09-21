// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.training;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns the training worker and its auxiliary coffee-detection executor. */
public final class TrainingService implements AutoCloseable {
    private volatile TrainingWorker worker;
    private ExecutorService detectionExecutor;

    public synchronized void start(TrainingSettings settings, String startMode) {
        stop();

        detectionExecutor = Executors.newSingleThreadExecutor();
        TrainingWorker newWorker = new TrainingWorker(
                settings.recoveryTime(),
                settings.timePerHit(),
                settings.dropInsteadDestroy(),
                settings.restType(),
                settings.enableAutoCaffeine(),
                settings.caffeineMg(),
                settings.enableAutoEat(),
                settings.foodStorageIntoFridge(),
                startMode,
                settings.trainingEfficiency(),
                detectionExecutor,
                this::onWorkerFinished);
        worker = newWorker;
        newWorker.start();
    }

    public synchronized void stop() {
        TrainingWorker currentWorker = worker;
        worker = null;
        if (currentWorker != null) {
            currentWorker.requestStop();
        }

        if (detectionExecutor != null) {
            detectionExecutor.shutdownNow();
            detectionExecutor = null;
        }
    }

    private synchronized void onWorkerFinished() {
        if (worker != Thread.currentThread()) {
            return;
        }
        worker = null;
        if (detectionExecutor != null) {
            detectionExecutor.shutdownNow();
            detectionExecutor = null;
        }
    }

    public boolean isRunning() {
        TrainingWorker currentWorker = worker;
        return currentWorker != null && currentWorker.isAlive();
    }

    @Override
    public void close() {
        stop();
    }
}
