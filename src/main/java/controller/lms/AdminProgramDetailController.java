package controller.lms;

import entities.Module;
import entities.Program;
import entities.ProgramModule;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import service.lms.ModuleService;
import service.lms.ProgramService;
import util.AppNavigator;
import javafx.geometry.Pos;
import javafx.geometry.Insets;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

public class AdminProgramDetailController implements Initializable {

    @FXML private Label breadcrumbCurrent;
    @FXML private Label programName;
    @FXML private Label statusBadge;
    @FXML private Label createdLabel;
    @FXML private Label updatedLabel;
    @FXML private ComboBox<Module> moduleSelector;
    @FXML private FlowPane modulesContainer;

    private final ProgramService programService = new ProgramService();
    private final ModuleService moduleService = new ModuleService();
    private Program program;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        moduleSelector.setConverter(new StringConverter<>() {
            @Override public String toString(Module m) { return m == null ? "" : m.getName() + " (" + m.getDuration() + " " + m.getPeriodUnit() + ")"; }
            @Override public Module fromString(String s) { return null; }
        });
    }

    public void setProgram(Program p) {
        this.program = p;
        breadcrumbCurrent.setText(p.getName());
        programName.setText(p.getName());
        statusBadge.setText(p.getPublished() == 1 ? "Published" : "Draft");
        statusBadge.getStyleClass().addAll("badge", p.getPublished() == 1 ? "badge-published" : "badge-draft");
        createdLabel.setText("Created: " + (p.getCreatedAt() != null ? sdf.format(p.getCreatedAt()) : "N/A"));
        updatedLabel.setText("Updated: " + (p.getUpdatedAt() != null ? sdf.format(p.getUpdatedAt()) : "N/A"));
        loadModules();
    }

    private void loadModules() {
        List<ProgramModule> assigned = programService.getModulesForProgram(program.getId());
        
        modulesContainer.getChildren().clear();
        if (assigned.isEmpty()) {
            Label placeholder = new Label("No modules assigned");
            placeholder.getStyleClass().add("empty-state");
            modulesContainer.getChildren().add(placeholder);
        } else {
            for (ProgramModule pm : assigned) {
                modulesContainer.getChildren().add(createModuleCard(pm));
            }
        }

        Set<Integer> assignedIds = assigned.stream().map(pm -> pm.getModule().getId()).collect(Collectors.toSet());
        List<Module> available = moduleService.listAll().stream().filter(m -> !assignedIds.contains(m.getId())).collect(Collectors.toList());
        moduleSelector.setItems(FXCollections.observableArrayList(available));
    }

    private VBox createModuleCard(ProgramModule pm) {
        Module m = pm.getModule();
        VBox card = new VBox(8);
        card.getStyleClass().add("lms-card");
        card.setMinWidth(280);
        card.setPrefWidth(280);
        card.setPadding(new Insets(12));

        Label nameLabel = new Label(m.getName());
        nameLabel.getStyleClass().add("card-title");
        nameLabel.setWrapText(true);

        Label idLabel = new Label("ID: " + m.getId());
        idLabel.getStyleClass().add("card-text-sm");

        Label durationLabel = new Label(m.getDuration() + " " + m.getPeriodUnit());
        durationLabel.getStyleClass().add("card-text");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button removeBtn = new Button("Remove");
        removeBtn.getStyleClass().add("danger-button");
        removeBtn.setMaxWidth(Double.MAX_VALUE);
        removeBtn.setOnAction(e -> onRemoveModule(pm));

        card.getChildren().addAll(nameLabel, idLabel, durationLabel, spacer, removeBtn);
        return card;
    }

    @FXML private void onAddModule() {
        Module selected = moduleSelector.getValue();
        if (selected == null) { new Alert(Alert.AlertType.WARNING, "Please select a module.").showAndWait(); return; }
        try {
            programService.assignModule(program.getId(), selected.getId());
            loadModules();
        } catch (Exception e) { new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait(); }
    }

    private void onRemoveModule(ProgramModule pm) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Remove module '" + pm.getModule().getName() + "' from program?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) { programService.removeModule(program.getId(), pm.getModule().getId()); loadModules(); }
        });
    }

    @FXML private void onEdit() { AppNavigator.showProgramForm(program); }
    @FXML private void onBackToList() { AppNavigator.showPrograms(); }
}