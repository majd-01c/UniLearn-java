package controller.lms;

import entities.Classe;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import service.lms.ClasseService;
import util.AppNavigator;

import java.net.URL;
import java.util.ResourceBundle;

public class AdminClasseController implements Initializable {
    
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private FlowPane cardsContainer;
    @FXML private Label totalLabel, activeLabel, fullLabel;
    
    private final ClasseService svc = new ClasseService();
    private FilteredList<Classe> filtered;

    @Override
    public void initialize(URL u, ResourceBundle r) {
        statusFilter.setItems(FXCollections.observableArrayList("All", "active", "full", "inactive"));
        statusFilter.setValue("All");
        
        searchField.textProperty().addListener((o, ov, nv) -> applyFilter());
        statusFilter.valueProperty().addListener((o, ov, nv) -> applyFilter());
        
        loadData();
    }

    private void loadData() {
        var all = FXCollections.observableArrayList(svc.listAll());
        filtered = new FilteredList<>(all);
        
        totalLabel.setText(String.valueOf(all.size()));
        activeLabel.setText(String.valueOf(all.stream().filter(c -> "active".equals(c.getStatus())).count()));
        fullLabel.setText(String.valueOf(all.stream().filter(c -> "full".equals(c.getStatus())).count()));
        
        filtered.predicateProperty().addListener((o, ov, nv) -> populateCards());
        populateCards();
    }

    private void applyFilter() {
        String s = searchField.getText() != null ? searchField.getText().toLowerCase() : "";
        String st = statusFilter.getValue();
        filtered.setPredicate(c -> 
            (s.isEmpty() || c.getName().toLowerCase().contains(s)) && 
            ("All".equals(st) || st == null || st.equals(c.getStatus()))
        );
    }
    
    private void populateCards() {
        cardsContainer.getChildren().clear();
        for (Classe c : filtered) {
            cardsContainer.getChildren().add(buildCard(c));
        }
    }

    private VBox buildCard(Classe c) {
        VBox card = new VBox(12);
        card.getStyleClass().add("lms-card");
        card.setMinWidth(280);
        card.setPrefWidth(280);
        card.setPadding(new Insets(16));

        // Header: Name & Status
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label name = new Label(c.getName());
        name.getStyleClass().add("card-title");
        name.setWrapText(true);
        name.setMaxWidth(160);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label status = new Label(c.getStatus());
        status.getStyleClass().addAll("badge", "badge-" + c.getStatus());
        
        header.getChildren().addAll(name, spacer, status);

        // Info block
        VBox infoBox = new VBox(4);
        Label program = new Label("Program: " + (c.getProgram() != null ? c.getProgram().getName() : "—"));
        program.getStyleClass().add("card-text");
        
        Label levelSpec = new Label(c.getLevel() + " - " + c.getSpecialty());
        levelSpec.getStyleClass().add("card-text");
        
        long activeCount = svc.countActiveStudents(c.getId());
        Label capacity = new Label("Capacity: " + activeCount + " / " + c.getCapacity());
        capacity.getStyleClass().add("card-text");
        
        infoBox.getChildren().addAll(program, levelSpec, capacity);
        
        Separator sep = new Separator();
        
        // Actions
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER);
        
        Button detail = new Button("Detail");
        detail.getStyleClass().add("ghost-button");
        detail.setOnAction(e -> AppNavigator.showClasseDetail(c));
        
        Button edit = new Button("Edit");
        edit.getStyleClass().add("ghost-button");
        edit.setOnAction(e -> AppNavigator.showClasseForm(c));
        
        Button delete = new Button("Delete");
        delete.getStyleClass().add("danger-button");
        delete.setOnAction(e -> onDel(c));
        
        actions.getChildren().addAll(detail, edit, delete);
        
        card.getChildren().addAll(header, infoBox, sep, actions);
        return card;
    }

    @FXML 
    private void onNew() {
        AppNavigator.showClasseForm(null);
    }
    
    private void onDel(Classe c) {
        new Alert(Alert.AlertType.CONFIRMATION, "Delete '" + c.getName() + "'?", ButtonType.YES, ButtonType.NO)
            .showAndWait()
            .ifPresent(b -> {
                if (b == ButtonType.YES) {
                    try {
                        svc.deleteClasse(c.getId());
                        loadData();
                    } catch (Exception ex) {
                        new Alert(Alert.AlertType.ERROR, "Cannot delete class: " + ex.getMessage()).showAndWait();
                    }
                }
            });
    }
}
