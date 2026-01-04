package com.open.terminal.openterminal;

import com.jcraft.jsch.*;
import com.jediterm.terminal.ui.JediTermWidget;
import com.open.terminal.openterminal.component.terminal.DefaultTerminalSettings;
import com.open.terminal.openterminal.component.terminal.SshTtyConnector;
import com.open.terminal.openterminal.fun.FileProcessInterface;
import com.open.terminal.openterminal.model.DownloadTask;
import com.open.terminal.openterminal.model.RemoteFile;
import com.open.terminal.openterminal.util.FileUtil;
import com.open.terminal.openterminal.util.ThreadUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingNode;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class TerminalController implements FileProcessInterface {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(TerminalController.class);

    @FXML
    private TitledPane filePanel;

    // UI 组件
    @FXML
    private StackPane terminalContainer;

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
    private TableView<RemoteFile> fileTableView;

    // SSH 相关对象
    private Session session;
    private ChannelShell channel;
    private ChannelSftp sftpChannel;
    private JediTermWidget terminalWidget;

    // 当前所在远程目录
    private String currentPath = ".";

    // 全局下载任务列表
    private final ObservableList<DownloadTask> downloadList = FXCollections.observableArrayList();

    // 监控 UI 控件
    @FXML
    private Label cpuLabel;
    @FXML
    private ProgressBar cpuProgress;
    @FXML
    private Label ramLabel;
    @FXML
    private ProgressBar ramProgress;
    @FXML
    private Label diskLabel;
    @FXML
    private ProgressBar diskProgress;
    @FXML
    private Label netDownLabel;
    @FXML
    private Label netUpLabel;

    // 定时任务标志
    private volatile boolean isMonitoring = false;

    // 监控状态记录
    private long prevIdleTime = 0;
    private long prevTotalTime = 0;
    private long prevRxBytes = 0;
    private long prevTxBytes = 0;
    private long lastCheckTime = 0;


    private SshTtyConnector ttyConnector;

    @FXML
    public void initialize() {

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

    @Override
    public void openRemoteFileWithChooser(RemoteFile file) {
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
                        printErrorToTerminal("打开文件失败: " + e.getMessage());
                    }
                });

            } catch (Exception e) {
                log.error("下载远程文件失败: {}", e.getMessage());
                printErrorToTerminal("操作失败：" + e.getMessage() + "\n");
            }
        });
    }

    @Override
    // 带进度的下载方法
    public void downloadRemoteFileWithProgress(String remotePath, Path localPath, long totalSize) throws Exception {
        String fileName = localPath.getFileName().toString();

        // 1. 创建任务并加入列表 (必须在 UI 线程添加，或者用 Platform.runLater)
        DownloadTask task = new DownloadTask(fileName, totalSize, false);
        Platform.runLater(() -> downloadList.addFirst(task)); // 加到最前面

        // 2. 创建 JSch 进度监听器
        SftpProgressMonitor monitor = new SftpProgressMonitor() {
            @Override
            public void init(int op, String src, String dest, long max) {
                // 开始下载
                log.info("开始下载文件: {}", fileName);
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
                log.info("文件下载完成: {}", fileName);
            }
        };

        // 3. 执行下载
        // mode: ChannelSftp.OVERWRITE 完全覆盖
        sftpChannel.get(remotePath, localPath.toString(), monitor, ChannelSftp.OVERWRITE);
    }

    @FXML
    @Override
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
                        downloadList.add(new DownloadTask(file.getName(), file.length(), true));
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
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("文件下载列表");
            dialog.setScene(new Scene(dialogContent));
            controller.setDialogStage(dialog);

            dialog.showAndWait();

        } catch (IOException e) {
            log.error("无法打开下载列表: {}", e.getMessage());
            printErrorToTerminal("无法打开下载列表: " + e.getMessage() + "\n");
        }
    }

    /**
     * 上传文件逻辑,包括单个文件和目录递归上传，上传到远程机器的当前目录
     */
    @FXML
    @Override
    public void handleUploadFile() {
        if (sftpChannel == null || !sftpChannel.isConnected()) {
            printErrorToTerminal("错误：SFTP 未连接，无法上传。\n");
            return;
        }

        Stage stage = (Stage) terminalContainer.getScene().getWindow();

        // 1. 创建一个确认对话框，让用户选择上传类型
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("选择上传类型");
        alert.setHeaderText("请选择上传内容");
        alert.setContentText("您想要上传单个文件还是整个文件夹？");

        ButtonType btnFile = new ButtonType("📄 上传文件");
        ButtonType btnDir = new ButtonType("📁 上传文件夹");
        ButtonType btnCancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(btnFile, btnDir, btnCancel);

        // 2. 获取用户选择
        java.util.Optional<ButtonType> result = alert.showAndWait();

        File selectedFile = null;

        if (result.isPresent()) {
            if (result.get() == btnFile) {
                // === 选项 A: 文件选择器 ===
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("选择要上传的文件");
                selectedFile = fileChooser.showOpenDialog(stage);
            } else if (result.get() == btnDir) {
                // === 选项 B: 目录选择器 ===
                DirectoryChooser directoryChooser = new DirectoryChooser();
                directoryChooser.setTitle("选择要上传的文件夹");
                selectedFile = directoryChooser.showDialog(stage);
            }
        }

        // 3. 如果用户没有取消，且选择了文件/目录，则执行之前的上传逻辑
        if (selectedFile != null) {
            final File finalFile = selectedFile;

            ThreadUtil.submitTask(() -> {
                try {
                    printErrorToTerminal("开始上传: " + finalFile.getName() + "...\n");

                    // 1. 单个文件上传
                    if (finalFile.isFile()) {
                        try (FileInputStream fis = new FileInputStream(finalFile)) {
                            sftpChannel.put(fis, finalFile.getName());
                        }
                    }
                    // 2. 目录递归上传
                    else {
                        Path rootPath = finalFile.toPath();
                        String remoteBaseDir = finalFile.getName();

                        FileUtil.safeSftpMkdir(sftpChannel, remoteBaseDir); // 确保远程根目录存在

                        java.nio.file.Files.walkFileTree(rootPath, new java.nio.file.SimpleFileVisitor<Path>() {
                            @NotNull
                            @Override
                            public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
                                Path relative = rootPath.relativize(dir);
                                if (relative.toString().isEmpty()) {
                                    return FileVisitResult.CONTINUE;
                                }

                                String remotePath = remoteBaseDir + "/" + relative.toString().replace("\\", "/");
                                try {
                                    FileUtil.safeSftpMkdir(sftpChannel, remotePath);
                                } catch (SftpException e) {
                                    throw new IOException("无法创建远程目录: " + remotePath, e);
                                }
                                return java.nio.file.FileVisitResult.CONTINUE;
                            }

                            @NotNull
                            @Override
                            public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                                Path relative = rootPath.relativize(file);
                                String remoteFilePath = remoteBaseDir + "/" + relative.toString().replace("\\", "/");

                                try (FileInputStream fis = new FileInputStream(file.toFile())) {
                                    sftpChannel.put(fis, remoteFilePath);
                                    Platform.runLater(() -> printErrorToTerminal("已上传: " + remoteFilePath + "\n"));
                                } catch (SftpException e) {
                                    throw new IOException("上传文件失败: " + file, e);
                                }
                                return java.nio.file.FileVisitResult.CONTINUE;
                            }
                        });
                    }
                    printErrorToTerminal("上传成功: " + finalFile.getName() + "\n");
                    Platform.runLater(() -> {
                        handleRefreshFiles();
                    });

                } catch (Exception e) {
                    log.error("上传失败", e);
                    printErrorToTerminal("上传失败: " + e.getMessage() + "\n");
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
                // 1. 创建 Session
                JSch jsch = new JSch();
                session = jsch.getSession(user, host, port);
                session.setPassword(password);

                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                // 跳过耗时的 GSSAPI 认证，加快连接速度
                config.put("PreferredAuthentications", "publickey,keyboard-interactive,password");
                session.setConfig(config);
                // 设置保活心跳，防止长时间未操作断开
                session.setServerAliveInterval(30000);
                session.connect(10000);

                // 2. 初始化 Shell 通道 (必须开启 PTY)
                channel = (ChannelShell) session.openChannel("shell");
                channel.setPty(true);
                channel.setPtyType("xterm"); // 必须设置，否则 vim 报错
                channel.connect();


                // 3. 初始化 SFTP 通道 (用于文件管理)
                sftpChannel = (ChannelSftp) session.openChannel("sftp");
                sftpChannel.connect();

                // 4. 初始化 JediTerm 终端组件
                // 注意：JediTerm 是 Swing 组件，必须在 Swing 线程 (EDT) 初始化
                // JavaFX 操作必须在 Platform 线程
                SwingUtilities.invokeLater(() -> {
                    try {
                        // 创建连接器
                        ttyConnector = new SshTtyConnector(channel);

                        // 创建终端 Widget
                        terminalWidget = new JediTermWidget(new DefaultTerminalSettings());
                        terminalWidget.setTtyConnector(ttyConnector);
                        terminalWidget.start();

                        // 获取 TerminalPanel (实际处理键盘事件的组件)
                        var terminalPanel = terminalWidget.getTerminalPanel();

                        // 全局事件拦截
                        // 获取当前 Swing 线程的焦点管理器
                        // 处理 ctrl+c 事件，发送 ASCII 3 到远程
                        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                                .addKeyEventDispatcher(new java.awt.KeyEventDispatcher() {
                                    @Override
                                    public boolean dispatchKeyEvent(java.awt.event.KeyEvent e) {
                                        // 1. 确保事件是针对当前终端面板的 (防止影响其他 Swing 组件)
                                        if (terminalWidget == null || e.getComponent() != terminalWidget.getTerminalPanel()) {
                                            return false; // 不处理，放行
                                        }

                                        // 2. 只处理 "按下" 事件 (KEY_PRESSED)
                                        if (e.getID() == java.awt.event.KeyEvent.KEY_PRESSED) {

                                            // 3. 检测 Ctrl + C
                                            if (e.getKeyCode() == java.awt.event.KeyEvent.VK_C
                                                    && e.isControlDown()
                                                    && !e.isShiftDown()
                                                    && !e.isAltDown()) {

                                                log.info(">>> 全局拦截器捕获到 Ctrl+C !");

                                                try {
                                                    // 发送 ASCII 3 (SIGINT)
                                                    ttyConnector.write(new byte[]{3});
                                                } catch (java.io.IOException ex) {
                                                    log.error("发送 Ctrl+C 失败: {}", ex.getMessage());
                                                }

                                                // 4. 【核心】返回 true 表示 "事件已被我处理，不要再分发给组件"
                                                // 这样 JediTerm 内部的 InputMap 就永远收不到这个事件了
                                                return true;
                                            }
                                        }

                                        // 返回 false 表示 "我没处理，继续按正常流程分发"
                                        return false;
                                    }
                                });

                        // 嵌入到 JavaFX
                        Platform.runLater(() -> {
                            SwingNode swingNode = new SwingNode();
                            swingNode.setContent(terminalWidget);
                            terminalContainer.getChildren().add(swingNode);

                            // 更新状态 UI
                            initConnectionInfo(host, port, user);
                        });

                    } catch (Exception e) {
                        log.error("初始化终端失败: {}", e.getMessage());
                    }
                });

                // 5. 加载初始目录文件
                // 注意：如果 loadRemoteFiles 内部也是异步的，这里直接调用没问题
                // 如果内部是同步 IO，建议包裹在 submitTask 中，或者确认 loadRemoteFiles 实现方式
                loadRemoteFiles(".");

                // 6. 启动系统监控 (CPU/内存等)
                startSystemMonitoring();

            } catch (Exception e) {
                log.error("SSH 连接失败: {}", e.getMessage());
                // 连接失败时，确保清理资源
                disconnect();
                printErrorToTerminal("连接失败: " + e.getMessage() + "\n");
                Platform.runLater(() -> {
                    statusLabel.setText("连接失败");
                    statusLabel.setStyle("-fx-text-fill: red;");
                });
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
                    if (filename.equals(".")) {
                        continue;
                    }

                    String sizeStr = FileUtil.humanReadableByteCountBin(attrs.getSize());
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
                    if (f1.isDirectory() && !f2.isDirectory()) {
                        return -1;
                    }
                    if (!f1.isDirectory() && f2.isDirectory()) {
                        return 1;
                    }
                    return f1.getFileName().compareToIgnoreCase(f2.getFileName());
                });

                Platform.runLater(() -> {
                    currentPathLabel.setText(pwd);
                    fileTableView.setItems(fileList);
                });

            } catch (SftpException e) {
                log.error("无法获取文件列表: {}", e.getMessage());
                printErrorToTerminal("无法获取文件列表: " + e.getMessage() + "\n");
            }
        });
    }

    private void startSystemMonitoring() {
        isMonitoring = true;
        ThreadUtil.submitTask(() -> {
            try {
                while (isMonitoring && session != null && session.isConnected()) {
                    long startTime = System.currentTimeMillis();
                    log.info("执行系统监控命令...");

                    // 构造组合命令：使用 "echo '#####';" 作为分隔符
                    // 1. 内存 (Total Used)
                    // 2. 磁盘 (Use%)
                    // 3. CPU (从 /proc/stat 读取第一行)
                    // 4. 网络 (从 /proc/net/dev 读取所有非 lo 接口)
                    String batchCommand =
                            "free -b | awk 'NR==2{print $2,$3}'; echo '#####'; " +
                                    "df -h / | awk 'NR==2{print $5}'; echo '#####'; " +
                                    "head -n 1 /proc/stat; echo '#####'; " +
                                    "cat /proc/net/dev | grep -v 'lo:' | awk '{rx+=$2; tx+=$10} END {print rx, tx}'";

                    String rawOutput = runSingleCommand(batchCommand);

                    if (!rawOutput.isEmpty()) {
                        parseAndUpdateMonitor(rawOutput);
                    }

                    // 计算休眠时间，保持间隔稳定 (例如 3秒刷新一次)
                    long elapsed = System.currentTimeMillis() - startTime;
                    long sleepTime = 3000 - elapsed;
                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    }
                    log.info("系统监控命令执行完毕。");
                }
            } catch (Exception e) {
                log.error("系统监控线程异常: {}", e.getMessage());
            }
        });
    }

    /**
     * 解析组合命令的返回结果并更新 UI
     */
    private void parseAndUpdateMonitor(String rawOutput) {
        try {
            log.info("监控命令输出:\n{}", rawOutput);
            String[] sections = rawOutput.split("#####");
            if (sections.length < 4) {
                return;
            }

            // --- 1. 解析内存 ---
            // 格式: "totalBytes usedBytes"
            String memStr = sections[0].trim();
            String[] memParts = memStr.split("\\s+");
            if (memParts.length >= 2) {
                log.info("内存数据: total={} used={}", memParts[0], memParts[1]);
                long totalMem = Long.parseLong(memParts[0]);
                long usedMem = Long.parseLong(memParts[1]);
                double usedGb = usedMem / 1024.0 / 1024.0 / 1024.0;
                double totalGb = totalMem / 1024.0 / 1024.0 / 1024.0;
                Platform.runLater(() -> {
                    ramLabel.setText(String.format("%.1f/%.1f GB", usedGb, totalGb));
                    ramProgress.setProgress((double) usedMem / totalMem);
                });
            }

            // --- 2. 解析磁盘 ---
            String diskStr = sections[1].trim().replace("%", ""); // 格式: "45"
            if (!diskStr.isEmpty()) {
                log.info("磁盘使用率: {}%", diskStr);
                double diskUsage = Double.parseDouble(diskStr) / 100.0;
                Platform.runLater(() -> {
                    diskLabel.setText(diskStr + "%");
                    diskProgress.setProgress(diskUsage);
                });
            }

            // --- 3. 解析 CPU ---
            // 格式: cpu  2255 34 2290 22625563 6290 127 456 0 0 0
            String cpuStr = sections[2].trim();
            String[] cpuParts = cpuStr.split("\\s+");
            if (cpuParts.length >= 5) {
                log.info("CPU 数据: {}", cpuStr);
                // user + nice + system + idle + iowait + irq + softirq ...
                long idle = Long.parseLong(cpuParts[4]); // 第5列通常是 idle
                long total = 0;
                for (int i = 1; i < cpuParts.length; i++) {
                    total += Long.parseLong(cpuParts[i]);
                }

                if (prevTotalTime != 0) {
                    long deltaTotal = total - prevTotalTime;
                    long deltaIdle = idle - prevIdleTime;
                    // 防止除零错误
                    if (deltaTotal > 0) {
                        double cpuUsage = 1.0 - ((double) deltaIdle / deltaTotal);
                        Platform.runLater(() -> {
                            cpuLabel.setText(String.format("%.0f%%", cpuUsage * 100));
                            cpuProgress.setProgress(cpuUsage);
                        });
                    }
                }
                prevIdleTime = idle;
                prevTotalTime = total;
            }

            // --- 4. 解析网络 ---
            // 格式: "totalRx totalTx" (字节)
            String netStr = sections[3].trim();
            String[] netParts = netStr.split("\\s+");
            if (netParts.length >= 2) {
                log.info("网络数据: rx={} tx={}", netParts[0], netParts[1]);
                long currentRx = Long.parseLong(netParts[0]);
                long currentTx = Long.parseLong(netParts[1]);
                long now = System.currentTimeMillis();

                if (lastCheckTime != 0) {
                    long timeDelta = (now - lastCheckTime) / 1000; // 秒
                    if (timeDelta > 0) {
                        long rxSpeed = (currentRx - prevRxBytes) / timeDelta;
                        long txSpeed = (currentTx - prevTxBytes) / timeDelta;

                        String rxStr = FileUtil.humanReadableByteCountBin(rxSpeed) + "/s";
                        String txStr = FileUtil.humanReadableByteCountBin(txSpeed) + "/s";

                        Platform.runLater(() -> {
                            netDownLabel.setText(rxStr);
                            netUpLabel.setText(txStr);
                        });
                    }
                }
                prevRxBytes = currentRx;
                prevTxBytes = currentTx;
                lastCheckTime = now;
            }

        } catch (Exception e) {
            // 解析错误忽略，避免单次格式异常中断监控
            log.warn("监控数据解析异常: {}", e.getMessage());
        }
    }

    // 辅助方法：执行单条命令并返回结果
    private String runSingleCommand(String command) {
        StringBuilder result = new StringBuilder();
        ChannelExec exec = null;
        try {
            log.info("执行监控命令：{}", command);
            exec = (ChannelExec) session.openChannel("exec");
            exec.setCommand(command);

            // 获取输入流
            InputStream in = exec.getInputStream();
            // 必须在 connect 之前获取流，channel open 后再读取
            exec.connect();

            // 使用 BufferedReader 读取，效率更高
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line).append("\n");
                }
            }
        } catch (Exception e) {
            log.error("执行监控命令失败: {}", e.getMessage());
        } finally {
            if (exec != null && exec.isConnected()) {
                exec.disconnect();
            }
        }
        return result.toString();
    }

    /**
     * 向终端打印系统消息（红色高亮）
     */
    private void printErrorToTerminal(String message) {
        if (terminalWidget == null) {
            return;
        }

        // JediTerm 是 Swing 组件，必须在 Swing 线程操作
        SwingUtilities.invokeLater(() -> {
            // 获取 Terminal 实例
            var terminal = terminalWidget.getTerminal();

            // 构造带颜色的字符串
            // \r\n       : 换行回车，确保在行首
            // \u001b[31m : ANSI 红色
            // \u001b[0m  : 重置颜色 (避免后续 SSH 输出也变红)
            String formattedMessage = "\r\n\u001b[31m[System Error] " + message + "\u001b[0m\r\n";

            // 直接写入终端缓冲区
            terminal.writeCharacters(formattedMessage);
        });
    }

    @FXML
    public void handleShowHistory() {
        loadRemoteHistory();
    }

    /**
     * 获取远程历史命令列表
     */
    private void loadRemoteHistory() {
        if (sftpChannel == null || !sftpChannel.isConnected()) {
            printErrorToTerminal("SFTP 未连接，无法获取历史记录");
            return;
        }

        ThreadUtil.submitTask(() -> {
            java.util.List<String> historyLines = new ArrayList<>();
            try {
                // 1. 尝试读取 .bash_history
                try {
                    // 获取当前用户主目录 (SFTP 默认登录就在主目录，直接读文件名即可)
                    // 或者更稳健的做法是 sftpChannel.getHome() + "/.bash_history"
                    InputStream is = sftpChannel.get(sftpChannel.getHome() + "/.bash_history");
                    historyLines.addAll(readHistoryStream(is, false)); // false 表示不需要特殊解析
                } catch (Exception ignored) {
                    // 如果没有 bash_history，可能用的是 zsh
                }

                // 2. 尝试读取 .zsh_history (如果 bash 没读到或者想都读)
                if (historyLines.isEmpty()) {
                    try {
                        InputStream is = sftpChannel.get(sftpChannel.getHome() + "/.zsh_history");
                        historyLines.addAll(readHistoryStream(is, true)); // true 表示需要解析 zsh 格式
                    } catch (Exception ignored) {
                    }
                }

                // 3. 去重并反转 (最近的在最上面)
                // 使用 LinkedHashSet 保持顺序并去重
                java.util.List<String> uniqueHistory = new ArrayList<>(new LinkedHashSet<>(historyLines));
                Collections.reverse(uniqueHistory);

                // 4. 弹出 UI 展示
                Platform.runLater(() -> showHistoryDialog(uniqueHistory));

            } catch (Exception e) {
                log.error("读取历史命令失败", e);
                printErrorToTerminal("读取历史命令失败: " + e.getMessage());
            }
        });
    }

    /**
     * 解析流工具方法
     * @param is 输入流
     * @param isZsh 是否是 zsh (zsh 历史文件带有时间戳元数据，需要清洗)
     */
    private java.util.List<String> readHistoryStream(InputStream is, boolean isZsh) {
        java.util.List<String> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String cmd = line;
                // Zsh 历史格式通常是: ": 1678888888:0;command"
                if (isZsh) {
                    int semiIndex = line.indexOf(';');
                    if (semiIndex > 0) {
                        cmd = line.substring(semiIndex + 1);
                    }
                }
                list.add(cmd);
            }
        } catch (Exception e) {
            log.error("解析历史命令流失败: {}", e.getMessage());
        }
        return list;
    }

    private void showHistoryDialog(java.util.List<String> commands) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("历史命令");
        dialog.initOwner(terminalContainer.getScene().getWindow());

        BorderPane pane = new BorderPane();

        // 1. 搜索框
        TextField searchField = new TextField();
        searchField.setPromptText("搜索命令...");
        pane.setTop(searchField);
        BorderPane.setMargin(searchField, new Insets(0, 0, 10, 0));

        // 2. 列表
        ListView<String> listView = new ListView<>();
        listView.getItems().addAll(commands);
        pane.setCenter(listView);

        // 3. 搜索逻辑
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty()) {
                listView.getItems().setAll(commands);
            } else {
                // 过滤列表
                java.util.List<String> filtered = commands.stream()
                        .filter(c -> c.toLowerCase().contains(newVal.toLowerCase()))
                        .collect(Collectors.toList());
                listView.getItems().setAll(filtered);
            }
        });

        // 双击执行
        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String cmd = listView.getSelectionModel().getSelectedItem();
                if (cmd != null) {
                    executeCommandFromHistory(cmd);
                    dialog.close();
                }
            }
        });

        dialog.getDialogPane().setContent(pane);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.show();
    }

    /**
     * 将命令发送到终端
     */
    private void executeCommandFromHistory(String command) {
        if (channel != null && channel.isConnected()) {
            try {
                // 发送命令 + 回车
                ttyConnector.write(command);

                // 让终端重获焦点
                SwingUtilities.invokeLater(() -> {
                    if (terminalWidget != null) {
                        terminalWidget.getTerminalPanel().requestFocusInWindow();
                    }
                });
            } catch (IOException e) {
                log.error("发送历史命令失败: {}", e.getMessage());
            }
        }
    }

    private void initConnectionInfo(String host, int port, String user) {
        this.hostLabel.setText(host);
        this.portLabel.setText(String.valueOf(port));
        this.userLabel.setText(user);
        this.statusLabel.setText("已连接");
        this.statusLabel.setStyle("-fx-text-fill: #4caf50;");
    }

    public void disconnect() {
        log.info("终端连接断开...");
        // 停止监控循环
        isMonitoring = false;
        if (sftpChannel != null && sftpChannel.isConnected()) {
            sftpChannel.disconnect();
        }
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        if (terminalWidget != null) {
            terminalWidget.close();
        }
    }
}
