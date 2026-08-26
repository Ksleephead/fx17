// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

/** 当前支持识别的附近物品类型。 */
public enum ItemType {
    PAN("pan"),
    STONE_FIRE("stoneFire"),
    RICE("rice"),
    WATER("water");

    private final String displayName;

    ItemType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
