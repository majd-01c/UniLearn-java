package controller.lms;

import entities.Module;
import entities.Program;
import entities.ProgramModule;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import service.lms.ModuleService;
import service.lms.ProgramService;
import util.AppNavigator;

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
    @FXML private TableView<ProgramModule> moduleTable;
    @FXML private TableColumn<ProgramModule, String> colModId;
    @FXML private TableColumn<ProgramModule, String> colModName;
    @FXML private TableColumn<ProgramModule, String> colModDuration;
    @FXML private TableColumn<ProgramModule, String> colModActions;

    private final ProgramService programService = new ProgramService();
    private final ModuleService moduleService = new ModuleService();
    private Program program;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        colModId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getModule().getId())));
        colModName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getModule().getName()));
        colModDuration.setCellValueFactory(c -> {
            Module m = c.getValue().getModule();
            return new SimpleStringProperty(m.getDuration() + " " + m.getPeriodUnit());
        });
        colModActions.setCellFactory(col -> new TableCell<>() {
            private final Button removeBtn = new Button("Remove");
            { removeBtn.getStyleClass().add("danger-button"); removeBtn.setOnAction(e -> onRemoveModule(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(String item, boolean empty) { super.updateItem(item, empty); setGraphic(empty ? null : removeBtn); setText(null); }
        });

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
        moduleTable.setItems(FXCollections.observableArrayList(assigned));

        Set<Integer> assignedIds = assigned.stream().map(pm -> pm.getModule().getId()).collect(Collectors.toSet());
        List<Module> available = moduleService.listAll().stream().filter(m -> !assignedIds.contains(m.getId())).collect(Collectors.toList());
        moduleSelector.setItems(FXCollections.observableArrayList(available));
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
