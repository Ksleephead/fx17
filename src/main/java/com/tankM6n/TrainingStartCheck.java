// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n;

import java.util.Optional;

/**
 * A check that runs before a user-initiated training session starts.
 *
 * <p>Return an empty result when training may start normally, or a warning when
 * the user should explicitly decide whether to continue.</p>
 */
@FunctionalInterface
interface TrainingStartCheck {
    Optional<TrainingStartWarning> validate();
}
