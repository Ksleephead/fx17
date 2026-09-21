// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.update;

import com.tankM6n.ConsoleLog;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public final class UpdateDialog {
    private UpdateDialog() {
    }

    public static void show(Stage owner, HostServices hostServices, UpdateInfo updateInfo) {
        ButtonType download = new ButtonType("立即下载", ButtonBar.ButtonData.OK_DONE);
        ButtonType later = new ButtonType("暂不更新", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(updateInfo.isForceUpdate() ? "必须更新" : "发现新版本");
        alert.setHeaderText("发现新版本 " + updateInfo.getVersion());
        alert.initOwner(owner);
        alert.initModality(Modality.NONE);
        alert.getButtonTypes().setAll(updateInfo.isForceUpdate()
                ? new ButtonType[]{download} : new ButtonType[]{download, later});

        VBox content = new VBox(8,
                new Label("当前版本：" + AppVersion.CURRENT_VERSION),
                new Label("最新版本：" + updateInfo.getVersion()),
                new Label(updateInfo.isForceUpdate() ? "此版本标记为必须更新。" : "更新内容："));
        content.setPadding(new Insets(4, 0, 0, 0));

        TextArea description = new TextArea(defaultDescription(updateInfo.getDescription()));
        description.setEditable(false);
        description.setWrapText(true);
        description.setPrefRowCount(6);
        content.getChildren().add(description);
        if (updateInfo.getExtractCode() != null && !updateInfo.getExtractCode().isBlank()) {
            content.getChildren().add(new Label("百度网盘提取码：" + updateInfo.getExtractCode()));
        }
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setPrefWidth(480);

        Button downloadButton = (Button) alert.getDialogPane().lookupButton(download);
        downloadButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            copyExtractCode(updateInfo.getExtractCode());
            try {
                hostServices.showDocument(updateInfo.getDownloadUrl());
                ConsoleLog.log("更新提示：已打开下载地址");
            } catch (RuntimeException exception) {
                ConsoleLog.log("更新提示：打开下载地址失败，" + exception.getMessage());
            }
        });

        if (updateInfo.isForceUpdate()) {
            alert.setOnCloseRequest(event -> event.consume());
        }
        alert.show();
    }

    private static String defaultDescription(String description) {
        return description == null || description.isBlank() ? "暂无更新说明" : description;
    }

    private static void copyExtractCode(String extractCode) {
        if (extractCode == null || extractCode.isBlank()) {
            return;
        }
        try {
            ClipboardContent content = new ClipboardContent();
            content.putString(extractCode);
            Clipboard.getSystemClipboard().setContent(content);
            ConsoleLog.log("更新提示：提取码已复制到剪贴板");
        } catch (RuntimeException exception) {
            ConsoleLog.log("更新提示：复制提取码失败，" + exception.getMessage());
        }
    }
}
