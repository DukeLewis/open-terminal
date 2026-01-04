package com.open.terminal.openterminal;


import com.open.terminal.openterminal.model.SavedConnection;
import com.open.terminal.openterminal.util.AlertUtil;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.apache.commons.lang.StringUtils;

/**
 * @description:
 * @author：dukelewis
 * @date: 2025/12/28
 * @Copyright： https://github.com/DukeLewis
 */
public class ConnectionDialogController {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ConnectionDialogController.class);

    @FXML private TextField nameField;
    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField userField;
    @FXML private PasswordField passField;

    private Stage dialogStage;
    private MainController mainController;
    private SavedConnection savedConnection;

    @FXML
    public void initialize() {
        portField.setText("22");
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    public void setSavedConnection(SavedConnection savedConnection) {
        this.savedConnection = savedConnection;
    }

    @FXML
    private void handleConnect() {
        String name = nameField.getText().trim();
        String host = hostField.getText().trim();
        String port = portField.getText().trim();
        String user = userField.getText().trim();
        String pass = passField.getText();

        // 如果存在已有的连接信息，即更新连接信息配置情况，则将用户最新输入的连接信息更新到里面
        if (savedConnection != null && StringUtils.isNotBlank(savedConnection.getId())) {
            savedConnection.setName(name);
            savedConnection.setHost(host);
            savedConnection.setPort(Integer.parseInt(port));
            savedConnection.setUser(user);
            savedConnection.setPassword(pass);
        }

        if (name.isEmpty() || host.isEmpty() || user.isEmpty()) {
            AlertUtil.showAlert("错误", "请填写所有必填字段");
            return;
        }

        try {
            mainController.connect(savedConnection);
            dialogStage.close();
        } catch (NumberFormatException e) {
            AlertUtil.showAlert("错误", "端口必须是数字");
        }
    }

    public void setConnectionInfo(SavedConnection savedConnection) {
        this.savedConnection = savedConnection;
        nameField.setText(savedConnection.getName());
        hostField.setText(savedConnection.getHost());
        portField.setText(String.valueOf(savedConnection.getPort()));
        userField.setText(savedConnection.getUser());
        passField.setText(savedConnection.getPassword());
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }
}
