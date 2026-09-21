// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemplateMatcherTest {
    @Test
    void identicalImagesHavePerfectSimilarity() {
        BufferedImage image = solidImage(3, 2, new Color(42, 42, 42));

        assertEquals(1.0, TemplateMatcher.compareSimilarity(image, image), 0.0);
    }

    @Test
    void blackAndWhiteImagesHaveZeroSimilarity() {
        BufferedImage black = solidImage(2, 2, Color.BLACK);
        BufferedImage white = solidImage(2, 2, Color.WHITE);

        assertEquals(0.0, TemplateMatcher.compareSimilarity(black, white), 0.0);
    }

    @Test
    void mismatchedDimensionsAreRejected() {
        BufferedImage first = solidImage(2, 2, Color.BLACK);
        BufferedImage second = solidImage(3, 2, Color.BLACK);

        assertThrows(
                IllegalArgumentException.class,
                () -> TemplateMatcher.compareSimilarity(first, second));
    }

    private static BufferedImage solidImage(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        return image;
    }
}
