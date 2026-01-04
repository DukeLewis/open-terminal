package com.open.terminal.openterminal.model;

import com.open.terminal.openterminal.DownloadFileListController;
import com.open.terminal.openterminal.util.FileUtil;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * @description: TODO
 * @author huangjialong
 * @date 2026/1/4 16:42
 * @version 1.0
 */
public class DownloadTask {
    private final StringProperty fileName = new SimpleStringProperty();
    private final StringProperty sizeStr = new SimpleStringProperty();
    private final DoubleProperty progress = new SimpleDoubleProperty(0.0);
    private final StringProperty status = new SimpleStringProperty(TaskStatus.PENDING);

    // 原始数据
    private final long totalSize;
    private long currentSize = 0;

    public DownloadTask(String fileName, long totalSize, boolean isCompleted) {
        this.fileName.set(fileName);
        this.totalSize = totalSize;
        this.sizeStr.set(FileUtil.humanReadableByteCountBin(totalSize));

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
                this.status.set(TaskStatus.COMPLETED);
            } else {
                this.status.set(TaskStatus.IN_PROGRESS);
            }
        });
    }

    // Getters for Property (用于 FXML 绑定)
    public StringProperty fileNameProperty() { return fileName; }
    public StringProperty sizeStrProperty() { return sizeStr; }
    public DoubleProperty progressProperty() { return progress; }
    public StringProperty statusProperty() { return status; }

    public String getFileName() { return fileName.get(); }

    public static class TaskStatus {
        public static final String PENDING = "等待中";
        public static final String IN_PROGRESS = "下载中...";
        public static final String COMPLETED = "已完成";
        public static final String FAILED = "下载失败";
    }
}
