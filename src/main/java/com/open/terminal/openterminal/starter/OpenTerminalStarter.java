package com.open.terminal.openterminal.starter;

import com.open.terminal.openterminal.util.ThreadUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class OpenTerminalStarter extends Application {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(OpenTerminalStarter.class);


    @Override
    public void start(Stage primaryStage) throws Exception {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("main-window.fxml"));
            URL fxmlUrl = getClass().getResource("main-window.fxml");
            System.out.println("FXML URL: " + fxmlUrl);
            if (fxmlUrl == null) {
                throw new IOException("找不到FXML文件");
            }
            Scene scene = new Scene(loader.load(), 1200, 800);

            primaryStage.setTitle("Open Terminal 终端管理器");
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (Exception e) {
            log.error("OpenTerminalStarter, start详细错误: {}", e.getMessage());
            if (e.getCause() != null) {
                log.error("OpenTerminalStarter, start错误原因: {}", e.getCause().getMessage());
                e.getCause().printStackTrace();
            }
            throw e;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() throws Exception {
        // 程序退出时，关闭虚拟线程池
        log.info("应用程序正在关闭，停止虚拟线程池...");
        ThreadUtil.stopVirtualExecutorService();
        Platform.exit();
        System.exit(0);
    }
}
