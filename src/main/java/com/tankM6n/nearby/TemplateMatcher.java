// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

import java.awt.image.BufferedImage;

/** 使用灰度像素绝对差计算两张同尺寸图片的相似度。 */
public final class TemplateMatcher {
    private TemplateMatcher() {
    }

    public static double compareSimilarity(BufferedImage current, BufferedImage template) {
        if (current == null || template == null) {
            throw new IllegalArgumentException("待比较图片不能为 null");
        }
        if (current.getWidth() != template.getWidth()
                || current.getHeight() != template.getHeight()) {
            throw new IllegalArgumentException("待比较图片的尺寸必须一致");
        }

        // totalDiff 是每个对应像素灰度差绝对值的总和。
        long totalDiff = 0L;
        for (int y = 0; y < template.getHeight(); y++) {
            for (int x = 0; x < template.getWidth(); x++) {
                int currentGray = current.getRGB(x, y) & 0xff;
                int templateGray = template.getRGB(x, y) & 0xff;
                totalDiff += Math.abs(currentGray - templateGray);
            }
        }

        // 完全相同为 1.0；所有像素相差 255 时为 0.0。
        long pixelCount = (long) template.getWidth() * template.getHeight();
        return 1.0 - totalDiff / (pixelCount * 255.0);
    }
}
