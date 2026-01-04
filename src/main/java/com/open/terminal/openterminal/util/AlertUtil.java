package com.open.terminal.openterminal.util;

import javafx.scene.control.Alert;

/**
 * @description: TODO
 * @author huangjialong
 * @date 2026/1/4 16:57
 * @version 1.0
 */
public class AlertUtil {
    public static final String ERROR_TITLE = "错误";
    public static void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void showErrorAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(ERROR_TITLE);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
