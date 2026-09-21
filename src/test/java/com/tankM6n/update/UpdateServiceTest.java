// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateServiceTest {
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
