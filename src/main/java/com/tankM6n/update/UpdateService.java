// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tankM6n.ConsoleLog;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public final class UpdateService {
    public static final String UPDATE_URL =
            "https://raw.giteeusercontent.com/ksleephead/scum-fx17-update/raw/master/latest.json";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI updateUri;

    public UpdateService() {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(),
                new ObjectMapper(), URI.create(UPDATE_URL));
    }

    UpdateService(HttpClient httpClient, ObjectMapper objectMapper, URI updateUri) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.updateUri = updateUri;
    }

    public Optional<UpdateInfo> checkForUpdate() {
        ConsoleLog.log("更新检查：开始检查，当前版本=" + AppVersion.CURRENT_VERSION);
        try {
            HttpRequest request = HttpRequest.newBuilder(updateUri)
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                ConsoleLog.log("更新检查：服务器返回非成功状态=" + response.statusCode());
                return Optional.empty();
            }

            UpdateInfo updateInfo = parseAndValidate(response.body()).orElse(null);
            if (updateInfo == null) {
                return Optional.empty();
            }

            ConsoleLog.log("更新检查：远端版本=" + updateInfo.getVersion());
            try {
                if (VersionUtils.isNewerVersion(AppVersion.CURRENT_VERSION, updateInfo.getVersion())) {
                    ConsoleLog.log("更新检查：检测到新版本=" + updateInfo.getVersion());
                    return Optional.of(updateInfo);
                }
                ConsoleLog.log("更新检查：没有更新");
            } catch (IllegalArgumentException exception) {
                ConsoleLog.log("更新检查：版本格式异常，" + exception.getMessage());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            ConsoleLog.log("更新检查：请求被中断");
        } catch (IOException | RuntimeException exception) {
            ConsoleLog.log("更新检查：网络请求失败，" + safeMessage(exception));
        }
        return Optional.empty();
    }

    Optional<UpdateInfo> parseAndValidate(String json) {
        try {
            UpdateInfo updateInfo = objectMapper.readValue(json, UpdateInfo.class);
            if (updateInfo == null || !updateInfo.hasRequiredFields()) {
                ConsoleLog.log("更新检查：更新信息缺少 version 或 downloadUrl");
                return Optional.empty();
            }
            return Optional.of(updateInfo);
        } catch (IOException | RuntimeException exception) {
            ConsoleLog.log("更新检查：JSON 解析失败，" + safeMessage(exception));
            return Optional.empty();
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName() : message;
    }
}
