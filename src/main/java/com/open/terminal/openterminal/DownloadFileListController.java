package com.open.terminal.openterminal;

import com.open.terminal.openterminal.util.FileUtil;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;

/**
 * @description:
 * @author：dukelewis
 * @date: 2025/12/29
 * @Copyright： https://github.com/DukeLewis
 */
public class DownloadFileListController {
    @FXML
    private TableView<DownloadTask> downloadTable;

    @FXML private TableColumn<DownloadTask, String> fileNameCol;
    @FXML private TableColumn<DownloadTask, String> sizeCol;
    @FXML private TableColumn<DownloadTask, Double> progressCol;
    @FXML private TableColumn<DownloadTask, String> statusCol;
    @FXML private TableColumn<DownloadTask, String> actionCol;

    @FXML private Label statusLabel;
    @FXML private ProgressIndicator globalProgress;

    private Stage dialogStage;

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    // 接收 TerminalController 传来的列表
    public void setDownloadTasks(ObservableList<DownloadTask> tasks) {
        downloadTable.setItems(tasks);
        statusLabel.setText("共 " + tasks.size() + " 个任务");
    }

    @FXML
    public void initialize() {
        // 1. 进度条渲染
        progressCol.setCellFactory(column -> new TableCell<DownloadTask, Double>() {
            private final ProgressBar progressBar = new ProgressBar();
            { progressBar.setMaxWidth(Double.MAX_VALUE); }

            @Override
            protected void updateItem(Double progress, boolean empty) {
                super.updateItem(progress, empty);
                // 如果是空行则不渲染
                if (empty || progress == null) {
                    setGraphic(null);
                } else {
                    progressBar.setProgress(progress);
                    setGraphic(progressBar);
                }
            }
        });

        // 2. 操作按钮 (这里简化为打开文件所在的文件夹)
        actionCol.setCellFactory(column -> new TableCell<DownloadTask, String>() {
            private final Button btn = new Button("打开");
            {
                btn.setStyle("-fx-background-color: #3c3f41; -fx-text-fill: white; -fx-font-size: 10px;-fx-cursor: hand;");
                btn.setOnAction(e -> {
                    // 打开文件
                    DownloadTask downloadTask = getTableView().getItems().get(getIndex());
                    Path localFile = FileUtil.localDownloadDir.resolve(downloadTask.getFileName());
                    try {
                        FileUtil.openWithSystemChooser(localFile.toFile());
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else setGraphic(btn);
            }
        });
    }

    @FXML
    public void handleClearCompleted() {
        // 清空逻辑
    }

    public static class DownloadTask {
        private final StringProperty fileName = new SimpleStringProperty();
        private final StringProperty sizeStr = new SimpleStringProperty();
        private final DoubleProperty progress = new SimpleDoubleProperty(0.0);
        private final StringProperty status = new SimpleStringProperty("等待中");

        // 原始数据
        private final long totalSize;
        private long currentSize = 0;

        public DownloadTask(String fileName, long totalSize, boolean isCompleted) {
            this.fileName.set(fileName);
            this.totalSize = totalSize;
            this.sizeStr.set(humanReadableByteCountBin(totalSize));

            if (isCompleted) {
                this.progress.set(1.0);
                this.status.set("已完成");
                this.currentSize = totalSize;
            }
        }

        public void updateProgress(long increment) {
            this.currentSize += increment;
            double p = (double) currentSize / totalSize;
            // 确保 UI 更新在主线程，且不超过 1.0
            javafx.application.Platform.runLater(() -> {
                this.progress.set(Math.min(p, 1.0));
                if (p >= 1.0) {
                    this.status.set("已完成");
                } else {
                    this.status.set("下载中...");
                }
            });
        }

        // Getters for Property (用于 FXML 绑定)
        public StringProperty fileNameProperty() { return fileName; }
        public StringProperty sizeStrProperty() { return sizeStr; }
        public DoubleProperty progressProperty() { return progress; }
        public StringProperty statusProperty() { return status; }

        public String getFileName() { return fileName.get(); }

        // 辅助方法：格式化大小
        private static String humanReadableByteCountBin(long bytes) {
            long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
            if (absB < 1024) return bytes + " B";
            long value = absB;
            java.text.CharacterIterator ci = new java.text.StringCharacterIterator("KMGTPE");
            for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
                value >>= 10;
                ci.next();
            }
            value *= Long.signum(bytes);
            return String.format("%.1f %ciB", value / 1024.0, ci.current());
        }
    }


}
