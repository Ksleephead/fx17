// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n;

/** User-facing warning produced by a pre-training check. */
record TrainingStartWarning(String title, String header, String content) {
}
