// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.training;

/** Immutable snapshot of the UI settings used by one training session. */
public record TrainingSettings(
        double recoveryTime,
        double timePerHit,
        boolean dropInsteadDestroy,
        String restType,
        boolean enableAutoCaffeine,
        double caffeineMg,
        boolean enableAutoEat,
        String trainingEfficiency) {
}
