// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

import java.awt.image.BufferedImage;

/** 检测器内部使用的已缓存模板及其匹配参数。 */
record ItemTemplate(
        ItemType type,
        BufferedImage grayImage,
        int iconOffsetX,
        int iconOffsetY,
        double similarityThreshold) {
}
