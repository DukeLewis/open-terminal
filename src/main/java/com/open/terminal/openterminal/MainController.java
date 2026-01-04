package com.open.terminal.openterminal;


import com.open.terminal.openterminal.manage.ConnectionManager;
import com.open.terminal.openterminal.model.SavedConnection;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @description:
 * @author：dukelewis
 * @date: 2025/12/28
 * @Copyright： https://github.com/DukeLewis
 */
public class MainController {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(MainController.class);
    @FXML
    private TabPane tabPane;

    // 缓存连接管理器的 Controller，以便刷新数据
    private ConnectionManagerController connectionManagerController;

    @FXML private Tab newConnectionTab; // 对应 FXML 中的 + 号 Tab

    @FXML
    public void initialize() {
        // 1. 监听 + 号点击
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == newConnectionTab) {
                // A.不仅是切换视图，而是创建一个新的“连接管理 Tab”
                openConnectionManagerTab();

                // B. 恢复选中之前的 Tab (防止 + 号看起来被选中)
                Platform.runLater(() -> {
                    // 如果刚才有选中的（比如从终端页点的+），就切回去；
                    // 但因为我们马上要打开新Tab并选中它，所以这里其实主要为了视觉不闪烁
                    if (oldTab != null && oldTab != newConnectionTab) {
                        // tabPane.getSelectionModel().select(oldTab); // 可选
                    }
                });
            }
        });

        // 2. 初始状态：打开一个连接管理页
        openConnectionManagerTab();
    }

    /**
     * 打开一个新的“连接管理器” Tab
     */
    private void openConnectionManagerTab() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("connection-manager.fxml"));
            Node content = loader.load();

            ConnectionManagerController controller = loader.getController();
            controller.setMainController(this);

            Tab tab = new Tab("新连接");
            tab.setContent(content);
            // 给这个 Tab 一个特殊的 ID 或 UserData，方便后续识别（可选）
            tab.setUserData(SavedConnection.CONN_MANAGER);

            addTabBeforePlus(tab);

        } catch (IOException e) {
            log.error("无法打开连接管理页面: {}", e.getMessage());
        }
    }

    /**
     * 通用方法：将 Tab 插入到 + 号前面并选中
     */
    private void addTabBeforePlus(Tab tab) {
        // 获取当前 Tab 列表的大小
        int size = tabPane.getTabs().size();
        // 插入到倒数第二个位置 (即 "+" 的前面)
        int insertIndex = size > 0 ? size - 1 : 0;

        tabPane.getTabs().add(insertIndex, tab);
        tabPane.getSelectionModel().select(tab);
    }

    // 处理新建连接（来自弹窗的确定按钮回调）
    public void connect(SavedConnection savedConnection) {
        // 1. 尝试保存逻辑
        checkAndSaveConnection(savedConnection);

        // 2. 创建 Tab 并连接
        createTab(savedConnection);
    }

    // 公开给管理页调用，不弹保存提示（因为是已保存的）
    public void createTab(SavedConnection savedConnection) {
        try {
            String name = savedConnection.getName();
            String host = savedConnection.getHost();
            int port = savedConnection.getPort();
            String user = savedConnection.getUser();
            String password = savedConnection.getPassword();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("terminal-tab.fxml"));
            Node terminalContent = loader.load();
            TerminalController terminalController = loader.getController();
            // 初始化连接...
            terminalController.connectSSH(host, port, user, password);

            Tab newTerminalTab = new Tab(name);
            newTerminalTab.setContent(terminalContent);

            // A. 获取当前正在操作的 Tab (也就是那个连接管理器 Tab)
            Tab oldManagerTab = tabPane.getSelectionModel().getSelectedItem();

            // B. 插入新 Tab 并选中
            addTabBeforePlus(newTerminalTab);

            // C. 检查旧 Tab 是否是连接管理器，是则删除
            if (oldManagerTab != null && SavedConnection.CONN_MANAGER.equals(oldManagerTab.getUserData())) {
                // 直接从 TabPane 的列表中移除对象，不需要 Map
                tabPane.getTabs().remove(oldManagerTab);
                log.info("连接管理器 Tab 已关闭");
            }

            // 当tab关闭时断开连接
            newTerminalTab.setOnClosed(e -> terminalController.disconnect());

        } catch (IOException e) {
            log.error("无法创建终端标签页: {}", e.getMessage());
            showAlert("错误", "无法创建终端标签页: " + e.getMessage());
        }
    }

    /**
     * 【核心需求】询问是否保存凭证
     */
    private void checkAndSaveConnection(SavedConnection savedConnection) {
        // 如果已经有 ID 了，说明是已保存的，直接保存
        if (StringUtils.isNotBlank(savedConnection.getId())) {
            ConnectionManager.getInstance().addOrUpdate(savedConnection);
            return;
        }
        String name = savedConnection.getName();
        String host = savedConnection.getHost();
        int port = savedConnection.getPort();
        String user = savedConnection.getUser();
        String password = savedConnection.getPassword();
        // 检查是否已经存在完全相同的连接，如果存在就不问了
        boolean exists = ConnectionManager.getInstance().getAll().stream()
                .anyMatch(c -> c.getHost().equals(host) && c.getUser().equals(user) && c.getPort() == port);

        if (exists) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("保存连接");
        alert.setHeaderText("是否保存此连接？");
        alert.setContentText("保存后下次可直接在列表点击连接，无需输入密码。");

        ButtonType btnSave = new ButtonType("保存", ButtonBar.ButtonData.YES);
        ButtonType btnNo = new ButtonType("仅本次", ButtonBar.ButtonData.NO);
        alert.getButtonTypes().setAll(btnSave, btnNo);

        alert.showAndWait().ifPresent(type -> {
            if (type == btnSave) {
                // 如果用户没填名字，默认用 host
                String finalName = (name == null || name.isEmpty()) ? host : name;
                SavedConnection conn = new SavedConnection(finalName, host, port, user, password);
                ConnectionManager.getInstance().addOrUpdate(conn);
            }
        });
    }

    @FXML
    public void handleNewConnection() {
        showConnectionDialog();
    }

    public void showConnectionDialog(SavedConnection savedConnection) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("connection-dialog.fxml"));
            BorderPane dialogContent = loader.load();

            ConnectionDialogController controller = loader.getController();

            if (savedConnection == null) {
                savedConnection = new SavedConnection();
                controller.setSavedConnection(savedConnection);
            } else {
                controller.setConnectionInfo(savedConnection);
            }

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("SSH 连接配置");
            dialog.setScene(new Scene(dialogContent));

            controller.setDialogStage(dialog);
            controller.setMainController(this);

            dialog.showAndWait();
        } catch (IOException e) {
            log.error("无法打开连接对话框: {}", e.getMessage());
        }
    }

    private void showConnectionDialog() {
        this.showConnectionDialog(null);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

}
