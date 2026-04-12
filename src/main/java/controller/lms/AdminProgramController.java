package controller.lms;

import entities.Program;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import service.lms.ProgramService;
import util.AppNavigator;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ResourceBundle;

public class AdminProgramController implements Initializable {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private TableView<Program> programTable;
    @FXML private TableColumn<Program, String> colId;
    @FXML private TableColumn<Program, String> colName;
    @FXML private TableColumn<Program, String> colPublished;
    @FXML private TableColumn<Program, String> colCreated;
    @FXML private TableColumn<Program, String> colActions;
    @FXML private Label totalLabel;
    @FXML private Label publishedLabel;
    @FXML private Label draftLabel;

    private final ProgramService programService = new ProgramService();
    private ObservableList<Program> allPrograms;
    private FilteredList<Program> filteredPrograms;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        statusFilter.setItems(FXCollections.observableArrayList("All", "Published", "Draft"));
        statusFilter.setValue("All");

        colId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        colPublished.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPublished() == 1 ? "Published" : "Draft"));
        colPublished.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label badge = new Label(item);
                badge.getStyleClass().addAll("badge", "Published".equals(item) ? "badge-published" : "badge-draft");
                setGraphic(badge); setText(null);
            }
        });
        colCreated.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCreatedAt() != null ? sdf.format(c.getValue().getCreatedAt()) : ""));
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("View");
            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final HBox box = new HBox(6, viewBtn, editBtn, deleteBtn);
            {
                viewBtn.getStyleClass().add("ghost-button");
                editBtn.getStyleClass().add("ghost-button");
                deleteBtn.getStyleClass().add("danger-button");
                viewBtn.setOnAction(e -> { Program p = getTableView().getItems().get(getIndex()); AppNavigator.showProgramDetail(p); });
                editBtn.setOnAction(e -> { Program p = getTableView().getItems().get(getIndex()); AppNavigator.showProgramForm(p); });
                deleteBtn.setOnAction(e -> { Program p = getTableView().getItems().get(getIndex()); onDelete(p); });
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box); setText(null);
            }
        });

        searchField.textProperty().addListener((o, ov, nv) -> applyFilter());
        statusFilter.valueProperty().addListener((o, ov, nv) -> applyFilter());
        loadData();
    }

    private void loadData() {
        allPrograms = FXCollections.observableArrayList(programService.listAll());
        filteredPrograms = new FilteredList<>(allPrograms, p -> true);
        programTable.setItems(filteredPrograms);
        updateStats();
    }

    private void applyFilter() {
        String search = searchField.getText() != null ? searchField.getText().toLowerCase() : "";
        String status = statusFilter.getValue();
        filteredPrograms.setPredicate(p -> {
            boolean matchName = search.isEmpty() || p.getName().toLowerCase().contains(search);
            boolean matchStatus = "All".equals(status) || status == null
                    || ("Published".equals(status) && p.getPublished() == 1)
                    || ("Draft".equals(status) && p.getPublished() == 0);
            return matchName && matchStatus;
        });
        updateStats();
    }

    private void updateStats() {
        totalLabel.setText(String.valueOf(allPrograms.size()));
        publishedLabel.setText(String.valueOf(allPrograms.stream().filter(p -> p.getPublished() == 1).count()));
        draftLabel.setText(String.valueOf(allPrograms.stream().filter(p -> p.getPublished() == 0).count()));
    }

    @FXML
    private void onNewProgram() { AppNavigator.showProgramForm(null); }

    private void onDelete(Program p) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete program '" + p.getName() + "'?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) { programService.deleteProgram(p.getId()); loadData(); }
        });
    }
}
