package controller.lms;

import entities.Program;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import service.lms.ProgramService;
import util.AppNavigator;
import validation.LmsValidator;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class AdminProgramFormController implements Initializable {

    @FXML private Label formTitle;
    @FXML private Label breadcrumbCurrent;
    @FXML private TextField nameField;
    @FXML private CheckBox publishedCheck;
    @FXML private Label nameError;
    @FXML private Label formError;

    private final ProgramService programService = new ProgramService();
    private Program editingProgram;

    @Override
    public void initialize(URL url, ResourceBundle rb) {}

    public void setProgram(Program program) {
        this.editingProgram = program;
        if (program != null) {
            formTitle.setText("Edit Program");
            breadcrumbCurrent.setText("Edit: " + program.getName());
            nameField.setText(program.getName());
            publishedCheck.setSelected(program.getPublished() == 1);
        }
    }

    @FXML
    private void onSave() {
        clearErrors();
        String name = nameField.getText();
        List<String> errors = LmsValidator.validateProgramForm(name);
        if (!errors.isEmpty()) {
            nameError.setText(String.join("\n", errors));
            nameError.setVisible(true); nameError.setManaged(true);
            return;
        }

        try {
            if (editingProgram == null) {
                programService.createProgram(name);
            } else {
                programService.updateProgram(editingProgram.getId(), name, publishedCheck.isSelected());
            }
            AppNavigator.showPrograms();
        } catch (Exception e) {
            formError.setText("Error: " + e.getMessage());
            formError.setVisible(true); formError.setManaged(true);
        }
    }

    @FXML
    private void onBackToList() { AppNavigator.showPrograms(); }

    private void clearErrors() {
        nameError.setVisible(false); nameError.setManaged(false);
        formError.setVisible(false); formError.setManaged(false);
    }
}
