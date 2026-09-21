// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.nearby;

import com.tankM6n.cooking.CookingWorker;

import java.util.List;

/**
 * @deprecated 活跃实现已迁移到 {@link CookingWorker}；保留此适配器兼容历史调用。
 */
@Deprecated
public final class cookCornThread extends CookingWorker {
    public cookCornThread(List<ItemMatch> itemMatches, String cookType) {
        super(itemMatches, cookType);
    }
}
