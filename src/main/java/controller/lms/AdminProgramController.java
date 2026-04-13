package controller.lms;

import entities.Program;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import service.lms.ProgramService;
import util.AppNavigator;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ResourceBundle;

public class AdminProgramController implements Initializable {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private FlowPane cardsContainer;
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

        searchField.textProperty().addListener((o, ov, nv) -> applyFilter());
        statusFilter.valueProperty().addListener((o, ov, nv) -> applyFilter());
        
        loadData();
    }

    private void loadData() {
        allPrograms = FXCollections.observableArrayList(programService.listAll());
        filteredPrograms = new FilteredList<>(allPrograms, p -> true);
        
        filteredPrograms.predicateProperty().addListener((o, ov, nv) -> populateCards());
        updateStats();
        populateCards();
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
    
    private void populateCards() {
        cardsContainer.getChildren().clear();
        for (Program p : filteredPrograms) {
            cardsContainer.getChildren().add(buildCard(p));
        }
    }

    private VBox buildCard(Program p) {
        VBox card = new VBox(12);
        card.getStyleClass().add("lms-card");
        card.setMinWidth(280);
        card.setPrefWidth(280);
        card.setPadding(new Insets(16));

        // Header: Name & Status
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label name = new Label(p.getName());
        name.getStyleClass().add("card-title");
        name.setWrapText(true);
        name.setMaxWidth(160);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        String statusStr = p.getPublished() == 1 ? "Published" : "Draft";
        Label status = new Label(statusStr);
        status.getStyleClass().addAll("badge", statusStr.equals("Published") ? "badge-published" : "badge-draft");
        
        header.getChildren().addAll(name, spacer, status);

        // Info block
        VBox infoBox = new VBox(4);
        Label created = new Label("Created: " + (p.getCreatedAt() != null ? sdf.format(p.getCreatedAt()) : "—"));
        created.getStyleClass().add("card-text");
        infoBox.getChildren().addAll(created);
        
        Separator sep = new Separator();
        
        // Actions
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER);
        
        Button detail = new Button("Detail");
        detail.getStyleClass().add("ghost-button");
        detail.setOnAction(e -> AppNavigator.showProgramDetail(p));
        
        Button edit = new Button("Edit");
        edit.getStyleClass().add("ghost-button");
        edit.setOnAction(e -> AppNavigator.showProgramForm(p));
        
        Button delete = new Button("Delete");
        delete.getStyleClass().add("danger-button");
        delete.setOnAction(e -> onDelete(p));
        
        actions.getChildren().addAll(detail, edit, delete);
        
        card.getChildren().addAll(header, infoBox, sep, actions);
        return card;
    }

    @FXML
    private void onNewProgram() { 
        AppNavigator.showProgramForm(null); 
    }

    private void onDelete(Program p) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete program '" + p.getName() + "'?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) { 
                try {
                    programService.deleteProgram(p.getId()); 
                    loadData(); 
                } catch (Exception ex) {
                    new Alert(Alert.AlertType.ERROR, "Cannot delete program: " + ex.getMessage()).showAndWait();
                }
            }
        });
    }
}
