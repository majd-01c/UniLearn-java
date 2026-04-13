package controller.lms;

import entities.Module;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import service.lms.ModuleService;
import util.AppNavigator;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ResourceBundle;

public class AdminModuleController implements Initializable {
    @FXML private TextField searchField;
    @FXML private TableView<Module> table;
    @FXML private TableColumn<Module, String> colId, colName, colDuration, colCreated, colActions;
    private final ModuleService svc = new ModuleService();
    private FilteredList<Module> filtered;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    @Override public void initialize(URL url, ResourceBundle rb) {
        colId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        colDuration.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDuration() + " " + c.getValue().getPeriodUnit()));
        colCreated.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCreatedAt() != null ? sdf.format(c.getValue().getCreatedAt()) : ""));
        colActions.setCellFactory(col -> new TableCell<>() {
            final Button edit = new Button("Edit"), del = new Button("Delete");
            final HBox box = new HBox(6, edit, del);
            { edit.getStyleClass().add("ghost-button"); del.getStyleClass().add("danger-button");
              edit.setOnAction(e -> AppNavigator.showModuleForm(getTableView().getItems().get(getIndex())));
              del.setOnAction(e -> onDel(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(String i, boolean empty) { super.updateItem(i, empty); setGraphic(empty ? null : box); setText(null); }
        });
        searchField.textProperty().addListener((o,ov,nv) -> filtered.setPredicate(m -> nv == null || nv.isEmpty() || m.getName().toLowerCase().contains(nv.toLowerCase())));
        loadData();
    }
    private void loadData() { filtered = new FilteredList<>(FXCollections.observableArrayList(svc.listAll())); table.setItems(filtered); }
    @FXML private void onNew() { AppNavigator.showModuleForm(null); }
    private void onDel(Module m) {
        new Alert(Alert.AlertType.CONFIRMATION, "Delete '" + m.getName() + "'?", ButtonType.YES, ButtonType.NO)
                .showAndWait().ifPresent(b -> { if (b == ButtonType.YES) { svc.deleteModule(m.getId()); loadData(); } });
    }
}
