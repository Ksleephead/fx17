// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** 仅包含第一版识别需要的裁剪、灰度化和缩放操作。 */
public final class ImageProcessor {
    private ImageProcessor() {
    }

    public static BufferedImage crop(BufferedImage source, Rectangle area) {
        if (source == null) {
            throw new IllegalArgumentException("source 不能为 null");
        }
        if (area == null || area.x < 0 || area.y < 0
                || area.width <= 0 || area.height <= 0
                || (long) area.x + area.width > source.getWidth()
                || (long) area.y + area.height > source.getHeight()) {
            throw new IllegalArgumentException(
                    "裁剪区域越界: crop=" + area
                            + ", image=" + source.getWidth() + "x" + source.getHeight());
        }
        // 所有边界条件检查通过后才允许调用 getSubimage。
        return source.getSubimage(area.x, area.y, area.width, area.height);
    }

    public static BufferedImage grayscale(BufferedImage source) {
        if (source == null) {
            throw new IllegalArgumentException("source 不能为 null");
        }
        BufferedImage result = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);
                int red = (rgb >>> 16) & 0xff;
                int green = (rgb >>> 8) & 0xff;
                int blue = rgb & 0xff;
                // 使用需求中指定的亮度灰度公式，不做二值化或边缘处理。
                int gray = (int) Math.round(0.299 * red + 0.587 * green + 0.114 * blue);
                result.setRGB(x, y, (gray << 16) | (gray << 8) | gray);
            }
        }
        return result;
    }

    public static BufferedImage resize(BufferedImage source, int width, int height) {
        if (source == null) {
            throw new IllegalArgumentException("source 不能为 null");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("resize 的宽和高必须大于 0");
        }
        if (source.getWidth() == width && source.getHeight() == height) {
            return source;
        }

        // 当前槽位 icon 尺寸不同时，将它缩放到模板尺寸后再逐像素比较。
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }
}
