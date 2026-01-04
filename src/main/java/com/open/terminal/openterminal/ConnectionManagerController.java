package com.open.terminal.openterminal;

import com.open.terminal.openterminal.manage.ConnectionManager;
import com.open.terminal.openterminal.model.SavedConnection;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;

public class ConnectionManagerController {

    @FXML private TableView<SavedConnection> connectionTable;
    @FXML private TableColumn<SavedConnection, String> colName;
    @FXML private TableColumn<SavedConnection, String> colHost;
    @FXML private TableColumn<SavedConnection, String> colUser;
    @FXML private TableColumn<SavedConnection, Integer> colPort;
    @FXML private TableColumn<SavedConnection, String> colLastConnected;
    @FXML private TableColumn<SavedConnection, SavedConnection> colAction;
    @FXML private TextField searchField;

    private MainController mainController; // 用于回调打开Tab

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    @FXML
    public void initialize() {
        // 绑定列
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colHost.setCellValueFactory(new PropertyValueFactory<>("host"));
        colUser.setCellValueFactory(new PropertyValueFactory<>("user"));
        colPort.setCellValueFactory(new PropertyValueFactory<>("port"));

        colLastConnected.setCellValueFactory(cellData -> {
            long ts = cellData.getValue().getLastConnected();
            String time = ts > 0 ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(ts)) : "-";
            return new SimpleObjectProperty<>(time);
        });

        // 操作列 (连接、编辑、删除)
        colAction.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
        colAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnConnect = new Button("🚀 连接");
            private final Button btnEdit = new Button("✏️ 编辑");
            private final Button btnDel = new Button("🗑️ 删除");
            private final HBox pane = new HBox(5, btnConnect, btnEdit, btnDel);

            {
                btnConnect.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-size: 10px;");
                btnEdit.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-size: 10px;");
                btnDel.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 10px;");

                btnConnect.setOnAction(e -> handleConnect(getItem()));
                btnEdit.setOnAction(e -> handleEdit(getItem()));
                btnDel.setOnAction(e -> handleDelete(getItem()));
            }

            @Override
            protected void updateItem(SavedConnection item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });

        // 搜索过滤
        searchField.textProperty().addListener((obs, oldVal, newVal) -> loadData(newVal));

        // 双击行直接连接
        connectionTable.setRowFactory(tv -> {
            TableRow<SavedConnection> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    handleConnect(row.getItem());
                }
            });
            return row;
        });

        loadData(null);
    }

    private void loadData(String filter) {
        var all = ConnectionManager.getInstance().getAll();
        if (filter != null && !filter.isEmpty()) {
            String f = filter.toLowerCase();
            all.removeIf(c -> !c.getName().toLowerCase().contains(f) && !c.getHost().contains(f));
        }
        connectionTable.setItems(FXCollections.observableArrayList(all));
    }

    @FXML
    private void handleNew() {
        mainController.handleNewConnection();
    }

    private void handleConnect(SavedConnection conn) {
        if (mainController != null) {
            // 更新最后连接时间
            conn.setLastConnected(System.currentTimeMillis());
            ConnectionManager.getInstance().save();
            loadData(null); // 刷新界面

            // 调用主控制器打开 Tab
            mainController.createTab(conn);
        }
    }

    private void handleEdit(SavedConnection conn) {
        // 弹出编辑窗口 (复用新建连接窗口，回填数据)
        mainController.showConnectionDialog(conn);
    }

    private void handleDelete(SavedConnection conn) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认删除");
        alert.setHeaderText("删除连接: " + conn.getName());
        alert.setContentText("确定要删除此连接配置吗？");
        alert.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                ConnectionManager.getInstance().remove(conn.getId());
                loadData(null);
            }
        });
    }
}
