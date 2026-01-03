package com.open.terminal.openterminal;


import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

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

    @FXML
    private void handleConnect() {
        String name = nameField.getText().trim();
        String host = hostField.getText().trim();
        String port = portField.getText().trim();
        String user = userField.getText().trim();
        String pass = passField.getText();

        if (name.isEmpty() || host.isEmpty() || user.isEmpty()) {
            showAlert("错误", "请填写所有必填字段");
            return;
        }

        try {
            int portNum = Integer.parseInt(port);
            mainController.createSSHConnection(name, host, portNum, user, pass);
            dialogStage.close();
        } catch (NumberFormatException e) {
            showAlert("错误", "端口必须是数字");
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
