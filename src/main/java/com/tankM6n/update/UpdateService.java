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
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class UpdateService {
    public static final String UPDATE_URL =
            "https://raw.giteeusercontent.com/ksleephead/scum-fx17-update/raw/master/latest.json";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration GITHUB_TIMEOUT = Duration.ofSeconds(4);
    public static final String GITHUB_UPDATE_URL =
            "https://raw.githubusercontent.com/Ksleephead/fx17/refs/heads/main/src/main/resources/latest.json";
    public static final String GITCODE_UPDATE_URL =
            "https://raw.gitcode.com/weixin_47793971/scum-fx17-update/raw/main/latest.json";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final List<UpdateSource> sources;

    record UpdateSource(URI uri, Duration timeout) { }

    public enum CheckStatus { UPDATE_AVAILABLE, UP_TO_DATE, FAILED }

    public record CheckResult(CheckStatus status, UpdateInfo updateInfo) { }

    public UpdateService() {
        this(HttpClient.newBuilder().connectTimeout(GITHUB_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL).build(),
                new ObjectMapper(), List.of(
                        new UpdateSource(URI.create(UPDATE_URL), TIMEOUT),
                        new UpdateSource(URI.create(GITHUB_UPDATE_URL), GITHUB_TIMEOUT),
                        new UpdateSource(URI.create(GITCODE_UPDATE_URL), TIMEOUT)));
    }

    UpdateService(HttpClient httpClient, ObjectMapper objectMapper, URI updateUri) {
        this(httpClient, objectMapper, List.of(new UpdateSource(updateUri, TIMEOUT)));
    }

    UpdateService(HttpClient httpClient, ObjectMapper objectMapper, List<UpdateSource> sources) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.sources = List.copyOf(sources);
    }

    public Optional<UpdateInfo> checkForUpdate() {
        return Optional.ofNullable(checkForUpdateResult().updateInfo());
    }

    public CheckResult checkForUpdateResult() {
        ConsoleLog.log("更新检查：开始检查，当前版本=" + AppVersion.CURRENT_VERSION);
        List<CompletableFuture<Optional<UpdateInfo>>> requests = new ArrayList<>();
        try {
            for (UpdateSource source : sources) {
                requests.add(fetchUpdate(source));
            }
            UpdateInfo latest = null;
            for (CompletableFuture<Optional<UpdateInfo>> request : requests) {
                UpdateInfo candidate = request.get().orElse(null);
                if (candidate != null && (latest == null
                        || VersionUtils.compareVersion(candidate.getVersion(), latest.getVersion()) > 0)) {
                    latest = candidate;
                }
            }
            if (latest != null && VersionUtils.isNewerVersion(AppVersion.CURRENT_VERSION, latest.getVersion())) {
                ConsoleLog.log("更新检查：检测到新版本=" + latest.getVersion());
                return new CheckResult(CheckStatus.UPDATE_AVAILABLE, latest);
            }
            ConsoleLog.log("更新检查：" + (latest == null ? "所有更新源均不可用" : "没有更新"));
            return new CheckResult(latest == null ? CheckStatus.FAILED : CheckStatus.UP_TO_DATE, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            ConsoleLog.log("更新检查：请求被中断");
        } catch (ExecutionException | RuntimeException exception) {
            ConsoleLog.log("更新检查：检查失败，" + safeMessage(exception));
        } finally {
            requests.forEach(request -> request.cancel(true));
        }
        return new CheckResult(CheckStatus.FAILED, null);
    }

    private CompletableFuture<Optional<UpdateInfo>> fetchUpdate(UpdateSource source) {
        try {
            HttpRequest request = HttpRequest.newBuilder(source.uri())
                    .timeout(source.timeout())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> parseResponse(source, response))
                    .exceptionally(exception -> {
                        ConsoleLog.log("更新检查：请求失败，地址=" + source.uri() + "，" + safeMessage(exception));
                        return Optional.empty();
                    });
        } catch (RuntimeException exception) {
            ConsoleLog.log("更新检查：请求失败，地址=" + source.uri() + "，" + safeMessage(exception));
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private Optional<UpdateInfo> parseResponse(UpdateSource source, HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            ConsoleLog.log("更新检查：地址=" + source.uri() + "，服务器返回非成功状态=" + response.statusCode());
            return Optional.empty();
        }

        UpdateInfo updateInfo = parseAndValidate(response.body()).orElse(null);
        if (updateInfo == null) {
            return Optional.empty();
        }

        ConsoleLog.log("更新检查：地址=" + source.uri() + "，远端版本=" + updateInfo.getVersion());
        try {
            VersionUtils.compareVersion(updateInfo.getVersion(), AppVersion.CURRENT_VERSION);
            return Optional.of(updateInfo);
        } catch (IllegalArgumentException exception) {
            ConsoleLog.log("更新检查：版本格式异常，" + exception.getMessage());
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

    private static String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName() : message;
    }
}
