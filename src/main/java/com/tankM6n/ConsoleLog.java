// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 将标准输出和标准错误同时写入控制台与日志文件。
 */
public final class ConsoleLog {
    private static final byte[] UTF_8_BOM = {
            (byte) 0xEF, (byte) 0xBB, (byte) 0xBF
    };
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static boolean initialized;

    private ConsoleLog() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        try {
            Path logFile = resolveLogFile();
            ensureUtf8Bom(logFile);

            PrintStream fileOutput = new PrintStream(
                    Files.newOutputStream(
                            logFile,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.APPEND
                    ),
                    true,
                    StandardCharsets.UTF_8
            );

            System.setOut(createTeePrintStream(originalOut, fileOutput));
            System.setErr(createTeePrintStream(originalErr, fileOutput));
            initialized = true;

            System.out.println();
            System.out.println("============================================================");
            System.out.println("程序启动时间: " + LocalDateTime.now().format(TIME_FORMAT));
            System.out.println("日志文件: " + logFile.toAbsolutePath());
            System.out.println("程序工作目录: " + System.getProperty("user.dir"));
            System.out.println("Java 版本: " + System.getProperty("java.version"));
            System.out.println("============================================================");
        } catch (IOException | RuntimeException e) {
            originalErr.println("初始化日志文件失败，程序将只输出到控制台: " + e.getMessage());
            e.printStackTrace(originalErr);
        }
    }

    private static Path resolveLogFile() {
        String launcherPath = System.getProperty("jpackage.app-path");
        if (launcherPath != null && !launcherPath.isBlank()) {
            Path launcherDirectory = Path.of(launcherPath).toAbsolutePath().normalize().getParent();
            if (launcherDirectory != null) {
                return launcherDirectory.resolve("console.log");
            }
        }

        return Path.of(System.getProperty("user.dir"), "console.log")
                .toAbsolutePath()
                .normalize();
    }

    /**
     * 为 UTF-8 日志添加 BOM，避免 Windows 记事本按系统默认编码读取中文。
     * 已存在的日志内容会原样保留。
     */
    private static void ensureUtf8Bom(Path logFile) throws IOException {
        Path parent = logFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (Files.notExists(logFile) || Files.size(logFile) == 0) {
            Files.write(
                    logFile,
                    UTF_8_BOM,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            return;
        }

        byte[] prefix = new byte[UTF_8_BOM.length];
        try (var input = Files.newInputStream(logFile)) {
            if (input.read(prefix) == UTF_8_BOM.length
                    && java.util.Arrays.equals(prefix, UTF_8_BOM)) {
                return;
            }
        }

        Path temporaryLog = Files.createTempFile(parent, "console-", ".log.tmp");
        try {
            try (var output = Files.newOutputStream(temporaryLog);
                 var input = Files.newInputStream(logFile)) {
                output.write(UTF_8_BOM);
                input.transferTo(output);
            }
            try {
                Files.move(
                        temporaryLog,
                        logFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(
                        temporaryLog,
                        logFile,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } finally {
            Files.deleteIfExists(temporaryLog);
        }
    }

    private static PrintStream createTeePrintStream(
            PrintStream consoleOutput,
            PrintStream fileOutput
    ) {
        return new PrintStream(
                new TeeOutputStream(consoleOutput, fileOutput),
                true,
                StandardCharsets.UTF_8
        );
    }

    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream consoleOutput;
        private final PrintStream fileOutput;

        private TeeOutputStream(OutputStream consoleOutput, PrintStream fileOutput) {
            this.consoleOutput = consoleOutput;
            this.fileOutput = fileOutput;
        }

        @Override
        public void write(int value) throws IOException {
            consoleOutput.write(value);
            synchronized (fileOutput) {
                fileOutput.write(value);
            }
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            consoleOutput.write(buffer, offset, length);
            synchronized (fileOutput) {
                fileOutput.write(buffer, offset, length);
            }
        }

        @Override
        public void flush() throws IOException {
            consoleOutput.flush();
            synchronized (fileOutput) {
                fileOutput.flush();
            }
        }
    }
}
