// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateServiceTest {
    @Test
    void requestsAllSourcesConcurrentlyAndSelectsNewestCompleteInformation() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newFixedThreadPool(3);
        server.setExecutor(executor);
        CountDownLatch arrived = new CountDownLatch(3);
        for (int index = 0; index < 3; index++) {
            String version = List.of("99.9.0", "99.10.0", "99.8.0").get(index);
            server.createContext("/" + index, exchange -> {
                arrived.countDown();
                boolean concurrent;
                try {
                    concurrent = arrived.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    concurrent = false;
                }
                byte[] body = ("{\"version\":\"" + version
                        + "\",\"downloadUrl\":\"https://example.com/" + version
                        + "\",\"extractCode\":\"abcd\",\"forceUpdate\":true}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(concurrent ? 200 : 500, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
        }
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var sources = List.of(0, 1, 2).stream().map(index ->
                    new UpdateService.UpdateSource(URI.create(base + "/" + index), Duration.ofSeconds(5))).toList();
            UpdateInfo info = new UpdateService(HttpClient.newHttpClient(), new ObjectMapper(), sources)
                    .checkForUpdate().orElseThrow();
            assertEquals("99.10.0", info.getVersion());
            assertEquals("https://example.com/99.10.0", info.getDownloadUrl());
            assertEquals("abcd", info.getExtractCode());
            assertTrue(info.isForceUpdate());
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void ignoresFailedInvalidAndTimedOutSources() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/valid", exchange -> {
            byte[] body = "{\"version\":\"99.1.0\",\"downloadUrl\":\"https://example.com\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.createContext("/invalid", exchange -> {
            byte[] body = "{\"version\":\"invalid\",\"downloadUrl\":\"https://example.com\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.createContext("/failed", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.createContext("/current", exchange -> {
            byte[] body = ("{\"version\":\"" + AppVersion.CURRENT_VERSION
                    + "\",\"downloadUrl\":\"https://example.com\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.createContext("/timeout", exchange -> {
            try { new CountDownLatch(1).await(2, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var sources = List.of("timeout", "failed", "invalid", "valid").stream().map(path ->
                    new UpdateService.UpdateSource(URI.create(base + "/" + path),
                            path.equals("timeout") ? Duration.ofMillis(300) : Duration.ofSeconds(5))).toList();
            assertEquals("99.1.0", new UpdateService(HttpClient.newHttpClient(), new ObjectMapper(), sources)
                    .checkForUpdate().orElseThrow().getVersion());
            var failedSources = sources.subList(0, 3);
            assertEquals(UpdateService.CheckStatus.FAILED,
                    new UpdateService(HttpClient.newHttpClient(), new ObjectMapper(), failedSources)
                            .checkForUpdateResult().status());
            var currentSources = List.of(sources.get(1), sources.get(2),
                    new UpdateService.UpdateSource(URI.create(base + "/current"), Duration.ofSeconds(5)));
            var currentResult = new UpdateService(HttpClient.newHttpClient(), new ObjectMapper(), currentSources)
                    .checkForUpdateResult();
            assertEquals(UpdateService.CheckStatus.UP_TO_DATE, currentResult.status());
            assertTrue(currentResult.updateInfo() == null);
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private final UpdateService service = new UpdateService(
            HttpClient.newHttpClient(), new ObjectMapper(), URI.create("https://example.invalid/latest.json"));

    @Test
    void parsesCompleteUpdateInformation() {
        String json = """
                {
                  "version": "1.42.0",
                  "downloadUrl": "https://pan.baidu.com/s/example",
                  "extractCode": "abcd",
                  "description": "新增功能\\n修复问题",
                  "forceUpdate": false
                }
                """;

        UpdateInfo info = service.parseAndValidate(json).orElseThrow();
        assertEquals("1.42.0", info.getVersion());
        assertEquals("abcd", info.getExtractCode());
        assertFalse(info.isForceUpdate());
    }

    @Test
    void acceptsMissingOptionalFields() {
        UpdateInfo info = service.parseAndValidate("""
                {"version":"1.42.0","downloadUrl":"https://pan.baidu.com/s/example"}
                """).orElseThrow();

        assertEquals("1.42.0", info.getVersion());
        assertTrue(info.getExtractCode() == null);
    }

    @Test
    void rejectsBrokenJsonAndMissingRequiredFields() {
        assertTrue(service.parseAndValidate("not-json").isEmpty());
        assertTrue(service.parseAndValidate("{\"version\":\"1.42.0\"}").isEmpty());
        assertTrue(service.parseAndValidate("{\"downloadUrl\":\"https://example.com\"}").isEmpty());
    }
}
