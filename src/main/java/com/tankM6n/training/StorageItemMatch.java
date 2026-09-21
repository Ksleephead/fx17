// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.training;

/** A detected storage object with its type and adjusted screen coordinate. */
record StorageItemMatch(String type, double similarity, int screenX, int screenY) {
}
