package com.open.terminal.openterminal;


import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

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

    private int connectionCount = 0;

    private Tab initWelcomeTab;

    @FXML
    public void initialize() {
        addWelcomeTab();
    }

    @FXML
    public void handleNewConnection() {
        showConnectionDialog();
    }

    private void addWelcomeTab() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("welcome-tab.fxml"));
            Tab welcomeTab = new Tab("欢迎", loader.load());
            welcomeTab.setClosable(false);

            // 保存欢迎页成员变量，便于创建连接之后关闭
            initWelcomeTab = welcomeTab;

            WelcomeController controller = loader.getController();
            controller.setMainController(this);

            tabPane.getTabs().add(welcomeTab);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showConnectionDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("connection-dialog.fxml"));
            BorderPane dialogContent = loader.load();

            ConnectionDialogController controller = loader.getController();

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("SSH 连接配置");
            dialog.setScene(new Scene(dialogContent));

            controller.setDialogStage(dialog);
            controller.setMainController(this);

            dialog.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void createSSHConnection(String name, String host, int port,
                                    String user, String password) {
        connectionCount++;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("terminal-tab.fxml"));
            Tab sshTab = new Tab(name, loader.load());

            TerminalController controller = loader.getController();
            controller.connectSSH(host, port, user, password);

            // 建立连接之后如果欢迎页面存在则删除
            if (initWelcomeTab != null) {
                log.info("remover welcome tab");
                tabPane.getTabs().remove(initWelcomeTab);
            }

            tabPane.getTabs().add(sshTab);
            tabPane.getSelectionModel().select(sshTab);

            // 当tab关闭时断开连接
            sshTab.setOnClosed(e -> controller.disconnect());

        } catch (IOException e) {
            e.printStackTrace();
            log.error("无法创建终端标签页: {}", e.getMessage());
            showAlert("错误", "无法创建终端标签页: " + e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

}
