// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

import javax.imageio.ImageIO;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * 通用附近物品识别器：截取固定网格，为每个槽位选择相似度最高的已知模板。
 * 当前注册平底锅、石头火堆、米和水四种模板，不执行任何鼠标或键盘操作。
 */
public final class NearbyItemDetector {
    private final NearbyItemDetectorConfig config;
    private final ScreenCapture screenCapture;
    private final NearbyGrid nearbyGrid;
    private final List<ItemTemplate> templates;

    public NearbyItemDetector(NearbyItemDetectorConfig config) throws Exception {
        this.config = config;
        this.screenCapture = new ScreenCapture();
        this.nearbyGrid = new NearbyGrid(config);

        // 所有模板只在识别器创建时读取和灰度化一次。
        this.templates = List.of(
                new ItemTemplate(
                        ItemType.PAN,
                        loadGrayTemplate(config.panTemplatePath()),
                        config.panIconOffsetX(),
                        config.panIconOffsetY(),
                        config.panSimilarityThreshold()),
                new ItemTemplate(
                        ItemType.STONE_FIRE,
                        loadGrayTemplate(config.stoneFireTemplatePath()),
                        config.stoneFireIconOffsetX(),
                        config.stoneFireIconOffsetY(),
                        config.stoneFireSimilarityThreshold()),
                new ItemTemplate(
                        ItemType.RICE,
                        loadGrayTemplate(config.riceTemplatePath()),
                        config.riceIconOffsetX(),
                        config.riceIconOffsetY(),
                        config.riceSimilarityThreshold()),
                new ItemTemplate(
                        ItemType.WATER,
                        loadGrayTemplate(config.waterTemplatePath()),
                        config.waterIconOffsetX(),
                        config.waterIconOffsetY(),
                        config.waterSimilarityThreshold()));

        // 模板尺寸各不相同，加载后逐个检查 ±radius 搜索范围是否仍在槽位内。
        for (ItemTemplate template : templates) {
            validateTemplateFitsSlot(template);
        }
    }

    /** 执行一轮识别并返回最终匹配集合。 */
    public List<ItemMatch> detectOnce() {
        return detectDetailedOnce().matches();
    }

    /**
     * 执行一轮详细识别，同时返回每格所有模板的相似度，供调试打印。
     * 每种模板使用自己的阈值；若多种均过阈值，则选择相似度最高的一种。
     */
    public DetectionResult detectDetailedOnce() {
        Rectangle nearbyArea = new Rectangle(
                config.nearbyX(),
                config.nearbyY(),
                config.nearbyWidth(),
                config.nearbyHeight());
        BufferedImage nearbyScreenshot = screenCapture.capture(nearbyArea);
        List<ItemMatch> matches = new ArrayList<>();
        List<SlotSimilarity> slotSimilarities = new ArrayList<>();

        for (int row = 0; row < config.rows(); row++) {
            for (int col = 0; col < config.cols(); col++) {
                ItemTemplate bestTemplate = null;
                double bestSimilarity = Double.NEGATIVE_INFINITY;
                EnumMap<ItemType, Double> similarities = new EnumMap<>(ItemType.class);

                for (ItemTemplate template : templates) {
                    // 在模板基准位置周围做小范围平移搜索，取最高相似度。
                    double similarity = findBestSimilarity(
                            nearbyScreenshot, row, col, template);

                    similarities.put(template.type(), similarity);

                    // 先使用模板自己的阈值过滤，再从合格模板中选择最高分。
                    if (similarity >= template.similarityThreshold()
                            && similarity > bestSimilarity) {
                        bestSimilarity = similarity;
                        bestTemplate = template;
                    }
                }

                Point center = nearbyGrid.screenCenter(row, col);
                ItemType detectedType = bestTemplate == null ? null : bestTemplate.type();
                slotSimilarities.add(new SlotSimilarity(
                        row, col, similarities,
                        detectedType, center.x, center.y));

                if (bestTemplate != null) {
                    matches.add(new ItemMatch(
                            bestTemplate.type(), row, col, bestSimilarity,
                            center.x, center.y));
                }
            }
        }

        return new DetectionResult(matches, slotSimilarities);
    }

    /**
     * 搜索模板基准偏移周围的候选位置。
     * radius=1 时一共比较 3×3=9 个裁剪区域，可容忍一像素位置变化。
     */
    private double findBestSimilarity(
            BufferedImage nearbyScreenshot,
            int row,
            int col,
            ItemTemplate template) {
        double bestSimilarity = Double.NEGATIVE_INFINITY;
        int radius = config.positionSearchRadius();

        for (int offsetDeltaY = -radius; offsetDeltaY <= radius; offsetDeltaY++) {
            for (int offsetDeltaX = -radius; offsetDeltaX <= radius; offsetDeltaX++) {
                BufferedImage icon = ImageProcessor.crop(
                        nearbyScreenshot,
                        nearbyGrid.iconArea(
                                row,
                                col,
                                template.iconOffsetX() + offsetDeltaX,
                                template.iconOffsetY() + offsetDeltaY,
                                template.grayImage().getWidth(),
                                template.grayImage().getHeight()));
                BufferedImage grayIcon = ImageProcessor.grayscale(icon);
                BufferedImage resizedIcon = ImageProcessor.resize(
                        grayIcon,
                        template.grayImage().getWidth(),
                        template.grayImage().getHeight());
                double similarity = TemplateMatcher.compareSimilarity(
                        resizedIcon, template.grayImage());
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                }
            }
        }

        return bestSimilarity;
    }

    private void validateTemplateFitsSlot(ItemTemplate template) {
        int radius = config.positionSearchRadius();
        if (template.iconOffsetX() - radius < 0
                || template.iconOffsetY() - radius < 0
                || (long) template.iconOffsetX() + radius
                + template.grayImage().getWidth() > config.slotWidth()
                || (long) template.iconOffsetY() + radius
                + template.grayImage().getHeight() > config.slotHeight()) {
            throw new IllegalArgumentException(
                    template.type() + " 模板搜索区域超出单个槽位");
        }
    }

    public static void main(String[] args) {
        Path configPath = args.length == 0
                ? Path.of("nearby-item-detector.properties")
                : Path.of(args[0]);

        try {
            NearbyItemDetectorConfig config = NearbyItemDetectorConfig.load(configPath);
            NearbyItemDetector detector = new NearbyItemDetector(config);
            DetectionResult result = detector.detectDetailedOnce();
            for (SlotSimilarity slot : result.slotSimilarities()) {
                System.out.println(slot);
            }
        } catch (Exception e) {
            System.err.println("附近物品识别器启动失败: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static BufferedImage loadGrayTemplate(String templateLocation) throws IOException {
        BufferedImage template;
        if (templateLocation.startsWith("classpath:")) {
            String resourcePath = templateLocation.substring("classpath:".length());
            if (!resourcePath.startsWith("/")) {
                resourcePath = "/" + resourcePath;
            }
            try (InputStream input = NearbyItemDetector.class.getResourceAsStream(resourcePath)) {
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
