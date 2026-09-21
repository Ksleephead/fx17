// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.update;

public final class VersionUtils {
    private VersionUtils() {
    }

    public static boolean isNewerVersion(String currentVersion, String remoteVersion) {
        return compareVersion(remoteVersion, currentVersion) > 0;
    }

    public static int compareVersion(String firstVersion, String secondVersion) {
        int[] first = parse(firstVersion);
        int[] second = parse(secondVersion);
        for (int index = 0; index < first.length; index++) {
            int comparison = Integer.compare(first[index], second[index]);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static int[] parse(String version) {
        if (version == null || !version.matches("\\d+\\.\\d+\\.\\d+")) {
            throw new IllegalArgumentException("版本号必须使用 major.minor.patch 格式");
        }

        String[] parts = version.split("\\.");
        int[] values = new int[3];
        try {
            for (int index = 0; index < parts.length; index++) {
                values[index] = Integer.parseInt(parts[index]);
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("版本号包含超出范围的数字", exception);
        }
        return values;
    }
}
