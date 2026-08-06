// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n;

import javafx.application.Application;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoMain {
    public static void main(String[] args) {
        ConsoleLog.initialize();

        SpringApplication app = new SpringApplication(DemoMain.class);
        // 关键：关闭 headless
        app.setHeadless(false);
        app.run(args);

        Application.launch(Main.class, args);
    }
}
