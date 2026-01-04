package com.open.terminal.openterminal;

import com.open.terminal.openterminal.model.DownloadTask;
import com.open.terminal.openterminal.util.FileUtil;
import com.open.terminal.openterminal.util.ThreadUtil;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;

/**
 * @description:
 * @author：dukelewis
 * @date: 2025/12/29
 * @Copyright： https://github.com/DukeLewis
 */
public class DownloadFileListController {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(DownloadFileListController.class);
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

        // 2. 操作按钮
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
                        log.error("无法打开文件: {}", localFile, ex);
                    }
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(btn);
                }
            }
        });
    }

    @FXML
    public void handleClearCompleted() {
        // 1. 弹出警告框
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("清空缓存确认");
        alert.setHeaderText("危险操作");
        alert.setContentText("这将永久删除所有已下载的文件！确定要继续吗？");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                Path rootDir = FileUtil.localDownloadDir;
                // 如果目录不存在，直接返回
                if (Files.notExists(rootDir) || !Files.isDirectory(rootDir)) {
                    return;
                }
                // 2. 清空 UI (只清空非进行中的)
                downloadTable.getItems().removeIf(task -> !DownloadTask.TaskStatus.IN_PROGRESS.equals(task.statusProperty().get()));

                // 3. 后台线程执行物理删除
                ThreadUtil.submitTask(() -> {
                    try {
                        Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {

                            // 1. 处理文件
                            @NotNull
                            @Override
                            public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                                try {
                                    Files.delete(file);
                                    log.info("已删除文件: {}", file);
                                } catch (IOException e) {
                                    // 捕获异常，比如文件正在被占用，打印日志但不中断流程
                                    log.warn("无法删除文件 (可能正在被占用): {}, 错误: {}", file, e.getMessage());
                                }
                                return FileVisitResult.CONTINUE;
                            }


                            // 2. 处理目录 (后序遍历，确保目录为空后再删)
                            @NotNull
                            @Override
                            public FileVisitResult postVisitDirectory(@NotNull Path dir, @Nullable IOException exc) throws IOException {
                                // 如果遍历过程有异常，先抛出
                                if (exc != null) {
                                    log.error("遍历目录出错: {}", dir, exc);
                                    return FileVisitResult.CONTINUE;
                                }

                                // 【关键优化】如果是根目录，则不删除
                                if (dir.equals(rootDir)) {
                                    return FileVisitResult.CONTINUE;
                                }

                                try {
                                    Files.delete(dir); // 删除子目录
                                    log.info("已删除目录: {}", dir);
                                } catch (IOException e) {
                                    // 目录非空（可能有文件没删掉）或被占用
                                    log.warn("无法删除目录: {}", dir);
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    } catch (IOException e) {
                        log.error("清理下载目录失败", e);
                    }
                });
            }
        });
    }

}
