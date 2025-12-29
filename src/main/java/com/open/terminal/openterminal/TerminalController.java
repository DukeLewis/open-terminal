package com.open.terminal.openterminal;

import com.jcraft.jsch.*;
import com.open.terminal.openterminal.util.FileUtil;
import com.open.terminal.openterminal.util.ThreadUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.CharacterIterator;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.Vector;

public class TerminalController {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(TerminalController.class);

    @FXML
    private TitledPane filePanel;

    @FXML
    private TextArea terminalOutput;
    @FXML
    private TextField commandInput;

    /**********左侧信息栏*******/
    @FXML
    private Label hostLabel;
    @FXML
    private Label portLabel;
    @FXML
    private Label userLabel;
    @FXML
    private Label statusLabel;

    /*********文件管理相关***********/
    @FXML
    private Label currentPathLabel;
    @FXML
    private TableView<RemoteFile> fileTableView; // 泛型指定为 RemoteFile

    // SSH 相关对象
    private Session session;
    private Channel channel; // Shell 通道
    private ChannelSftp sftpChannel; // SFTP 通道
    private OutputStream outputStream;

    // 当前所在远程目录
    private String currentPath = ".";

    // 全局下载任务列表
    private final ObservableList<DownloadFileListController.DownloadTask> downloadList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        commandInput.setOnAction(e -> sendCommand());

        // 初始化表格列绑定 (确保 FXML 中的 TableColumn 顺序与这里一致，或者你可以在 FXML 中绑定)
        // 假设 FXML 中有4列，我们这里动态获取列并设置工厂
        if (fileTableView.getColumns().size() >= 4) {
            fileTableView.getColumns().get(0).setCellValueFactory(new PropertyValueFactory<>("fileName"));
            fileTableView.getColumns().get(1).setCellValueFactory(new PropertyValueFactory<>("size"));
            fileTableView.getColumns().get(2).setCellValueFactory(new PropertyValueFactory<>("permissions"));
            fileTableView.getColumns().get(3).setCellValueFactory(new PropertyValueFactory<>("modificationTime"));
        }

        // TableView 每一行（TableRow）的创建方式
        fileTableView.setRowFactory(tv -> {
            TableRow<RemoteFile> row = new TableRow<>();
            // 给行添加双击进入目录事件
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    RemoteFile rowData = row.getItem();
                    if (rowData.isDirectory()) {
                        loadRemoteFiles(currentPath + "/" + rowData.getFileName());
                    } else {
                        openRemoteFileWithChooser(rowData);
                    }
                }
            });
            return row;
        });
    }

    private void openRemoteFileWithChooser(RemoteFile file) {
        if (sftpChannel == null || !sftpChannel.isConnected()) {
            return;
        }
        ThreadUtil.submitTask(() -> {
            Path localDownloadDir = FileUtil.localDownloadDir;
            try {
                if (!Files.exists(localDownloadDir)) {
                    Files.createDirectories(localDownloadDir);
                }

                Path localFile = localDownloadDir.resolve(file.getFileName());

                // 即使文件存在，如果用户点击的是下载/打开，我们通常检查是否需要覆盖
                // 这里为了简单，假设每次都重新下载，或者你可以加判断
                // 如果需要覆盖，则执行下载：

                // 获取文件大小 (RemoteFile 对象里是字符串，这里最好解析一下，或者重新lstat)
                // 简单起见，假设 file.getSize() 能转回 long，或者重新获取属性
                SftpATTRS attrs = sftpChannel.lstat(currentPath + "/" + file.getFileName());
                long fileSize = attrs.getSize();

                downloadRemoteFileWithProgress(
                        currentPath + "/" + file.getFileName(),
                        localFile,
                        fileSize
                );

                // 下载完成后打开
                Platform.runLater(() -> {
                    try {
                        FileUtil.openWithSystemChooser(localFile.toFile());
                    } catch (IOException e) {
                        appendOutput("打开文件失败: " + e.getMessage());
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> appendOutput("操作失败：" + e.getMessage() + "\n"));
            }
        });
    }

    // 带进度的下载方法
    private void downloadRemoteFileWithProgress(String remotePath, Path localPath, long totalSize) throws Exception {
        String fileName = localPath.getFileName().toString();

        // 1. 创建任务并加入列表 (必须在 UI 线程添加，或者用 Platform.runLater)
        DownloadFileListController.DownloadTask task = new DownloadFileListController.DownloadTask(fileName, totalSize, false);
        Platform.runLater(() -> downloadList.add(0, task)); // 加到最前面

        // 2. 创建 JSch 进度监听器
        SftpProgressMonitor monitor = new SftpProgressMonitor() {
            @Override
            public void init(int op, String src, String dest, long max) {
                // 开始下载
            }

            @Override
            public boolean count(long count) {
                // count 是本次传输的字节增量
                task.updateProgress(count);
                return true; // 返回 false 会取消传输
            }

            @Override
            public void end() {
                // 结束
            }
        };

        // 3. 执行下载
        // mode: ChannelSftp.OVERWRITE 完全覆盖
        sftpChannel.get(remotePath, localPath.toString(), monitor, ChannelSftp.OVERWRITE);
    }

    @FXML
    public void handleFileList() {
        try {
            Path localDownloadDir = FileUtil.localDownloadDir;
            // 1. 确保目录存在
            if (!Files.exists(localDownloadDir)) {
                Files.createDirectories(localDownloadDir);
            }

            // 2. 扫描本地目录，将已存在的文件（且不在当前列表中的）加入列表
            File[] existingFiles = localDownloadDir.toFile().listFiles();
            if (existingFiles != null) {
                for (File file : existingFiles) {
                    boolean alreadyInList = downloadList.stream()
                            .anyMatch(t -> t.getFileName().equals(file.getName()));

                    if (!alreadyInList) {
                        // 添加历史文件，状态设为已完成
                        downloadList.add(new DownloadFileListController.DownloadTask(file.getName(), file.length(), true));
                    }
                }
            }

            // 3. 加载弹窗
            FXMLLoader loader = new FXMLLoader(getClass().getResource("download-file-list.fxml"));
            BorderPane dialogContent = loader.load();

            DownloadFileListController controller = loader.getController();

            // 4. 【关键】注入数据列表
            controller.setDownloadTasks(downloadList);

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL); // 模态窗口
            dialog.setTitle("文件下载列表");
            dialog.setScene(new Scene(dialogContent));
            controller.setDialogStage(dialog);

            dialog.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            appendOutput("无法打开下载列表: " + e.getMessage() + "\n");
        }
    }

    /**
     * 上传文件逻辑
     */
    @FXML
    public void handleUploadFile() {
        if (sftpChannel == null || !sftpChannel.isConnected()) {
            appendOutput("错误：SFTP 未连接，无法上传。\n");
            return;
        }

        // 打开文件选择器
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择要上传的文件");
        Stage stage = (Stage) terminalOutput.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            ThreadUtil.submitTask(() -> {
                try {
                    Platform.runLater(() -> appendOutput("开始上传: " + selectedFile.getName() + "...\n"));

                    // 执行上传
                    sftpChannel.put(new FileInputStream(selectedFile), selectedFile.getName());

                    Platform.runLater(() -> {
                        appendOutput("上传成功: " + selectedFile.getName() + "\n");
                        // 上传完成后刷新列表
                        handleRefreshFiles();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> appendOutput("上传失败: " + e.getMessage() + "\n"));
                    e.printStackTrace();
                }
            });
        }
    }

    /**
     * 刷新文件列表
     */
    @FXML
    public void handleRefreshFiles() {
        loadRemoteFiles(currentPath);
    }

    /**
     * 连接 SSH 并初始化 SFTP
     */
    public void connectSSH(String host, int port, String user, String password) {
        ThreadUtil.submitTask(() -> {
            try {
                JSch jsch = new JSch();
                session = jsch.getSession(user, host, port);
                session.setPassword(password);

                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                session.setConfig(config);

                Platform.runLater(() ->
                        appendOutput("正在连接到 " + host + ":" + port + "...\n")
                );

                session.connect(10000);

                // 1. 初始化 Shell 通道
                channel = session.openChannel("shell");
                InputStream inputStream = channel.getInputStream();
                outputStream = channel.getOutputStream();
                channel.connect();

                // 2. 初始化 SFTP 通道 (用于文件管理)
                sftpChannel = (ChannelSftp) session.openChannel("sftp");
                sftpChannel.connect();

                Platform.runLater(() -> {
                    appendOutput("连接成功!\n\n");
                    initConnectionInfo(host, port, user);
                    statusLabel.setText("已连接");
                    statusLabel.setStyle("-fx-text-fill: #4caf50;");
                });

                // 3. 加载初始目录文件
                loadRemoteFiles(".");

                // 使用虚拟线程读取 Shell 输出
                ThreadUtil.submitTask(() -> readOutput(inputStream));

            } catch (Exception e) {
                Platform.runLater(() -> {
                    appendOutput("连接失败: " + e.getMessage() + "\n");
                    statusLabel.setText("连接失败");
                    statusLabel.setStyle("-fx-text-fill: red;");
                });
                e.printStackTrace();
            }
        });
    }

    /**
     * 加载指定路径的远程文件
     */
    private void loadRemoteFiles(String path) {
        if (sftpChannel == null || !sftpChannel.isConnected()) {
            return;
        }

        ThreadUtil.submitTask(() -> {
            try {
                // 切换目录并获取绝对路径
                sftpChannel.cd(path);
                String pwd = sftpChannel.pwd();
                this.currentPath = pwd; // 更新当前路径变量

                Vector<ChannelSftp.LsEntry> entries = sftpChannel.ls(".");
                ObservableList<RemoteFile> fileList = FXCollections.observableArrayList();

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                for (ChannelSftp.LsEntry entry : entries) {
                    String filename = entry.getFilename();
                    SftpATTRS attrs = entry.getAttrs();

                    // 排除当前目录 "."
                    if (filename.equals(".")) continue;

                    String sizeStr = humanReadableByteCountBin(attrs.getSize());
                    String dateStr = sdf.format(new Date(attrs.getMTime() * 1000L));
                    boolean isDir = attrs.isDir();

                    // 对目录添加特殊标记或颜色 (这里简单处理文件名)
                    String displayName = isDir ? filename + "/" : filename;

                    fileList.add(new RemoteFile(
                            displayName,
                            isDir ? "" : sizeStr, // 目录不显示大小
                            attrs.getPermissionsString(),
                            dateStr,
                            filename, // 原始文件名，用于操作
                            isDir
                    ));
                }

                // 排序：目录在前，文件在后
                fileList.sort((f1, f2) -> {
                    if (f1.isDirectory() && !f2.isDirectory()) return -1;
                    if (!f1.isDirectory() && f2.isDirectory()) return 1;
                    return f1.getFileName().compareToIgnoreCase(f2.getFileName());
                });

                Platform.runLater(() -> {
                    currentPathLabel.setText(pwd);
                    fileTableView.setItems(fileList);
                });

            } catch (SftpException e) {
                Platform.runLater(() -> appendOutput("无法获取文件列表: " + e.getMessage() + "\n"));
                e.printStackTrace();
            }
        });
    }

    // ... (readOutput, sendCommand, appendOutput, initConnectionInfo 保持不变) ...
    private void readOutput(InputStream inputStream) {
        try {
            byte[] buffer = new byte[1024];
            while (true) {
                while (inputStream.available() > 0) {
                    int bytesRead = inputStream.read(buffer, 0, 1024);
                    if (bytesRead < 0) break;

                    String text = new String(buffer, 0, bytesRead);
                    Platform.runLater(() -> appendOutput(text));
                }

                if (channel.isClosed()) {
                    if (inputStream.available() > 0) continue;
                    Platform.runLater(() -> appendOutput("\n连接已关闭\n"));
                    break;
                }
                Thread.sleep(100);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void sendCommand() {
        String command = commandInput.getText();
        if (!command.isEmpty() && outputStream != null) {
            ThreadUtil.submitTask(() -> {
                try {
                    outputStream.write((command + "\n").getBytes());
                    outputStream.flush();
                    Platform.runLater(() -> commandInput.clear());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private void appendOutput(String text) {
        terminalOutput.appendText(text);
        terminalOutput.setScrollTop(Double.MAX_VALUE);
    }

    private void initConnectionInfo(String host, int port, String user) {
        this.hostLabel.setText(host);
        this.portLabel.setText(String.valueOf(port));
        this.userLabel.setText(user);
    }

    public void disconnect() {
        log.info("终端连接断开...");
        if (sftpChannel != null && sftpChannel.isConnected()) {
            sftpChannel.disconnect();
        }
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    // 辅助方法：格式化文件大小，转换为二进制文件大小：KiB，MiB，GiB 等
    private static String humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new java.text.StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            // 位运算，二进制数右移位10位，相当于value/(2^10 = 1024)
            value >>= 10;
            ci.next();
        }
        // 恢复符号（正 / 负），bytes 可能是负数（比如差值、剩余空间）
        value *= Long.signum(bytes);
        return String.format("%.1f %ciB", value / 1024.0, ci.current());
    }

    // ================== 内部数据模型类 ==================
    public static class RemoteFile {
        private final SimpleStringProperty fileName;
        private final SimpleStringProperty size;
        private final SimpleStringProperty permissions;
        private final SimpleStringProperty modificationTime;

        private final String rawName; // 原始文件名(不含装饰)
        private final boolean isDirectory;

        public RemoteFile(String fileName, String size, String permissions, String modificationTime, String rawName, boolean isDirectory) {
            this.fileName = new SimpleStringProperty(fileName);
            this.size = new SimpleStringProperty(size);
            this.permissions = new SimpleStringProperty(permissions);
            this.modificationTime = new SimpleStringProperty(modificationTime);
            this.rawName = rawName;
            this.isDirectory = isDirectory;
        }

        public String getFileName() {
            return fileName.get();
        }

        public String getSize() {
            return size.get();
        }

        public String getPermissions() {
            return permissions.get();
        }

        public String getModificationTime() {
            return modificationTime.get();
        }

        public String getRawName() {
            return rawName;
        }

        public boolean isDirectory() {
            return isDirectory;
        }
    }
}
