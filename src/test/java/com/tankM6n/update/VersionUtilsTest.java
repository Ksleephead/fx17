// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionUtilsTest {
    @Test
    void recognizesNewerVersionsNumerically() {
        assertTrue(VersionUtils.isNewerVersion("1.9.0", "1.10.0"));
        assertTrue(VersionUtils.isNewerVersion("1.41.0", "1.42.0"));
        assertTrue(VersionUtils.isNewerVersion("1.42.0", "1.42.1"));
        assertTrue(VersionUtils.isNewerVersion("1.99.99", "2.0.0"));
    }

    @Test
    void rejectsEqualOrOlderRemoteVersions() {
        assertFalse(VersionUtils.isNewerVersion("1.42.0", "1.42.0"));
        assertFalse(VersionUtils.isNewerVersion("1.43.0", "1.42.0"));
    }

    @Test
    void rejectsMalformedVersionsWithoutCrashingCaller() {
        assertThrows(IllegalArgumentException.class,
                () -> VersionUtils.isNewerVersion("1.41.0", "1.42"));
        assertThrows(IllegalArgumentException.class,
                () -> VersionUtils.isNewerVersion("1.41.0", "latest"));
    }
}
