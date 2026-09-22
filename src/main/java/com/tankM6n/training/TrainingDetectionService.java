// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.training;

import com.tankM6n.nearby.ArrowDetectorConfig;
import com.tankM6n.nearby.RegionDetectorConfig;
import com.tankM6n.nearby.RegionTemplateDetector;
import com.tankM6n.nearby.ScreenTemplateMatch;

import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns all template-detector configuration and lazy detector caches used by training.
 * Matching itself remains delegated to the unchanged RegionTemplateDetector algorithm.
 */
final class TrainingDetectionService {
    private ArrowDetectorConfig arrowConfig;
    private RegionTemplateDetector arrowDetector;
    private RegionDetectorConfig repairConfig;
    private RegionTemplateDetector repairDetector;
    private RegionDetectorConfig drinkOnceConfig;
    private RegionTemplateDetector drinkOnceDetector;
    private RegionDetectorConfig caseConfig;
    private RegionTemplateDetector caseDetector;
    private RegionDetectorConfig fridgeConfig;
    private RegionTemplateDetector fridgeDetector;
    private RegionDetectorConfig destroyConfig;
    private RegionTemplateDetector cookedFishDetector;
    private RegionTemplateDetector cookedCornDetector;

    ScreenTemplateMatch detectArrowOnce() throws Exception {
        if (arrowDetector == null) {
            arrowConfig = ArrowDetectorConfig.load(Path.of("arrow-detector.properties"));
            arrowDetector = new RegionTemplateDetector(
                    arrowConfig.searchArea(),
                    arrowConfig.templatePath(),
                    arrowConfig.similarityThreshold());
        }
        return adjustedBest(arrowDetector, arrowConfig.resultOffsetX(), arrowConfig.resultOffsetY());
    }

    ScreenTemplateMatch detectRepairOnce() throws Exception {
        if (repairDetector == null) {
            repairConfig = RegionDetectorConfig.load(
                    Path.of("nearby-item-detector.properties"), "xiuli");
            repairDetector = detector(repairConfig);
        }
        return adjustedBest(
                repairDetector, repairConfig.resultOffsetX(), repairConfig.resultOffsetY());
    }

    ScreenTemplateMatch detectDrinkOnce() throws Exception {
        if (drinkOnceDetector == null) {
            drinkOnceConfig = RegionDetectorConfig.load(
                    Path.of("nearby-item-detector.properties"), "drinkOnce");
            drinkOnceDetector = detector(drinkOnceConfig);
        }
        return adjustedBest(
                drinkOnceDetector,
                drinkOnceConfig.resultOffsetX(),
                drinkOnceConfig.resultOffsetY());
    }

    List<StorageItemMatch> detectStorageItemsOnce() throws Exception {
        if (caseDetector == null || fridgeDetector == null) {
            Path configPath = Path.of("nearby-item-detector.properties");
            caseConfig = RegionDetectorConfig.load(configPath, "case");
            fridgeConfig = RegionDetectorConfig.load(configPath, "fridge");
            caseDetector = detector(caseConfig);
            fridgeDetector = detector(fridgeConfig);
        }

        List<StorageItemMatch> matches = new ArrayList<>();
        addStorageMatches(matches, "case", caseDetector, caseConfig);
        addStorageMatches(matches, "fridge", fridgeDetector, fridgeConfig);
        return List.copyOf(matches);
    }

    List<ScreenTemplateMatch> detectCookedFishOnce() throws Exception {
        if (cookedFishDetector == null) {
            Rectangle searchArea = new Rectangle(370, 260, 625 - 370, 516 - 260);
            cookedFishDetector = new RegionTemplateDetector(
                    searchArea, "classpath:/image/cookedFish.jpg", 0.900);
        }
        return cookedFishDetector.detectOnce();
    }

    List<ScreenTemplateMatch> detectCookedCornOnce() throws Exception {
        if (cookedCornDetector == null) {
            Rectangle searchArea = new Rectangle(370, 260, 625 - 370, 516 - 260);
            cookedCornDetector = new RegionTemplateDetector(
                    searchArea, "classpath:/image/cookedCorn.jpg", 0.900);
        }
        return cookedCornDetector.detectOnce();
    }

    ScreenTemplateMatch detectDestroyOnce(int screenX, int screenY) throws Exception {
        if (destroyConfig == null) {
            destroyConfig = RegionDetectorConfig.load(
                    Path.of("nearby-item-detector.properties"), "destroy");
        }

        Rectangle searchArea = new Rectangle(
                screenX + destroyConfig.searchX(),
                screenY + destroyConfig.searchY(),
                destroyConfig.searchWidth(),
                destroyConfig.searchHeight());
        RegionTemplateDetector detector = new RegionTemplateDetector(
                searchArea,
                destroyConfig.templatePath(),
                destroyConfig.similarityThreshold());
        return adjustedBest(
                detector, destroyConfig.resultOffsetX(), destroyConfig.resultOffsetY());
    }

    private static RegionTemplateDetector detector(RegionDetectorConfig config) throws Exception {
        return new RegionTemplateDetector(
                config.searchArea(), config.templatePath(), config.similarityThreshold());
    }

    private static ScreenTemplateMatch adjustedBest(
            RegionTemplateDetector detector, int offsetX, int offsetY) {
        ScreenTemplateMatch best = null;
        for (ScreenTemplateMatch match : detector.detectOnce()) {
            if (best == null || match.similarity() > best.similarity()) {
                best = match;
            }
        }
        if (best == null) {
            return null;
        }
        return new ScreenTemplateMatch(
                best.similarity(), best.screenX() + offsetX, best.screenY() + offsetY);
    }

    private static void addStorageMatches(
            List<StorageItemMatch> result,
            String type,
            RegionTemplateDetector detector,
            RegionDetectorConfig config) {
        for (ScreenTemplateMatch match : detector.detectOnce()) {
            result.add(new StorageItemMatch(
                    type,
                    match.similarity(),
                    match.screenX() + config.resultOffsetX(),
                    match.screenY() + config.resultOffsetY()));
        }
    }
}
