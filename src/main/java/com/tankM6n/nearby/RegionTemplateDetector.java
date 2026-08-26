// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 在指定屏幕矩形中逐像素滑动灰度模板，返回所有去重后的匹配中心坐标。
 * 模板在构造时只加载一次，每次 detectOnce() 都重新截图。
 */
public final class RegionTemplateDetector {
    private final Rectangle screenArea;
    private final double similarityThreshold;
    private final ScreenCapture screenCapture;
    private final BufferedImage grayTemplate;

    public RegionTemplateDetector(
            Rectangle screenArea,
            String templateLocation,
            double similarityThreshold) throws Exception {
        if (screenArea == null || screenArea.width <= 0 || screenArea.height <= 0) {
            throw new IllegalArgumentException("识别区域宽高必须大于 0");
        }
        if (!Double.isFinite(similarityThreshold)
                || similarityThreshold < 0.0
                || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("similarityThreshold 必须在 0.0 到 1.0 之间");
        }

        this.screenArea = new Rectangle(screenArea);
        this.similarityThreshold = similarityThreshold;
        this.screenCapture = new ScreenCapture();
        this.grayTemplate = loadGrayTemplate(templateLocation);

        if (grayTemplate.getWidth() > screenArea.width
                || grayTemplate.getHeight() > screenArea.height) {
            throw new IllegalArgumentException("模板尺寸不能大于识别区域");
        }
    }

    public List<ScreenTemplateMatch> detectOnce() {
        BufferedImage screenshot = screenCapture.capture(screenArea);
        BufferedImage grayScreenshot = ImageProcessor.grayscale(screenshot);
        List<ScreenTemplateMatch> candidates = new ArrayList<>();

        int maxX = grayScreenshot.getWidth() - grayTemplate.getWidth();
        int maxY = grayScreenshot.getHeight() - grayTemplate.getHeight();
        for (int y = 0; y <= maxY; y++) {
            for (int x = 0; x <= maxX; x++) {
                BufferedImage candidate = ImageProcessor.crop(
                        grayScreenshot,
                        new Rectangle(
                                x, y,
                                grayTemplate.getWidth(),
                                grayTemplate.getHeight()));
                double similarity = TemplateMatcher.compareSimilarity(
                        candidate, grayTemplate);
                if (similarity >= similarityThreshold) {
                    candidates.add(new ScreenTemplateMatch(
                            similarity,
                            screenArea.x + x + grayTemplate.getWidth() / 2,
                            screenArea.y + y + grayTemplate.getHeight() / 2));
                }
            }
        }

        // 同一个文字图标周围通常会有多个重叠命中，按最高分优先合并。
        candidates.sort(Comparator.comparingDouble(
                ScreenTemplateMatch::similarity).reversed());
        List<ScreenTemplateMatch> matches = new ArrayList<>();
        for (ScreenTemplateMatch candidate : candidates) {
            if (!overlapsExistingMatch(candidate, matches)) {
                matches.add(candidate);
            }
        }
        matches.sort(Comparator.comparingInt(ScreenTemplateMatch::screenY));
        return List.copyOf(matches);
    }

    private boolean overlapsExistingMatch(
            ScreenTemplateMatch candidate,
            List<ScreenTemplateMatch> acceptedMatches) {
        for (ScreenTemplateMatch accepted : acceptedMatches) {
            if (Math.abs(candidate.screenX() - accepted.screenX()) < grayTemplate.getWidth()
                    && Math.abs(candidate.screenY() - accepted.screenY())
                    < grayTemplate.getHeight()) {
                return true;
            }
        }
        return false;
    }

    private static BufferedImage loadGrayTemplate(String templateLocation) throws IOException {
        BufferedImage template;
        if (templateLocation.startsWith("classpath:")) {
            String resourcePath = templateLocation.substring("classpath:".length());
            if (!resourcePath.startsWith("/")) {
                resourcePath = "/" + resourcePath;
            }
            try (InputStream input = RegionTemplateDetector.class.getResourceAsStream(resourcePath)) {
                if (input == null) {
                    throw new IOException("classpath 模板不存在: " + templateLocation);
                }
                template = ImageIO.read(input);
            }
        } else {
            Path templatePath = Path.of(templateLocation);
            if (!Files.isRegularFile(templatePath)) {
                throw new IOException("模板文件不存在: " + templatePath);
            }
            template = ImageIO.read(templatePath.toFile());
        }

        if (template == null) {
            throw new IOException("无法读取模板图片: " + templateLocation);
        }
        return ImageProcessor.grayscale(template);
    }
}
