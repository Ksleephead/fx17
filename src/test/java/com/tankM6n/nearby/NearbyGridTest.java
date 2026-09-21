// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NearbyGridTest {
    private final NearbyGrid grid = new NearbyGrid(config());

    @Test
    void firstSlotUsesConfiguredScreenOrigin() {
        assertEquals(new Point(398, 80), grid.screenCenter(0, 0));
    }

    @Test
    void lastSlotIncludesAllSlotGaps() {
        assertEquals(new Point(566, 696), grid.screenCenter(11, 3));
    }

    @Test
    void outOfBoundsSlotIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> grid.screenCenter(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> grid.screenCenter(12, 0));
        assertThrows(IllegalArgumentException.class, () -> grid.screenCenter(0, 4));
    }

    private static NearbyItemDetectorConfig config() {
        return new NearbyItemDetectorConfig(
                372, 53, 223, 700,
                12, 4, 52, 54, 4, 2,
                4, 14,
                4, 18,
                6, 3,
                10, 2,
                7, 6,
                4, 10,
                1,
                "pan", "fire", "rice", "water", "corn", "fish",
                0.9, 0.9, 0.9, 0.92, 0.92, 0.92);
    }
}
