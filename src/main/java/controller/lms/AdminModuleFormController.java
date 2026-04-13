package controller.lms;

import entities.Module;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import service.lms.ModuleService;
import util.AppNavigator;
import validation.LmsValidator;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class AdminModuleFormController implements Initializable {
    @FXML private Label formTitle, breadcrumb, errorLabel;
    @FXML private TextField nameField;
    @FXML private ComboBox<String> periodField;
    @FXML private Spinner<Integer> durationField;
    private final ModuleService svc = new ModuleService();
    private Module editing;

    @Override public void initialize(URL url, ResourceBundle rb) {
        periodField.setItems(FXCollections.observableArrayList("HOUR","DAY","WEEK","MONTH"));
        periodField.setValue("WEEK");
        durationField.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 1));
    }
    public void setModule(Module m) {
        editing = m;
        if (m != null) { formTitle.setText("Edit Module"); breadcrumb.setText("Edit: "+m.getName()); nameField.setText(m.getName()); periodField.setValue(m.getPeriodUnit()); durationField.getValueFactory().setValue(m.getDuration()); }
    }
    @FXML private void onSave() {
        errorLabel.setVisible(false); errorLabel.setManaged(false);
        List<String> errs = LmsValidator.validateModuleForm(nameField.getText(), periodField.getValue(), durationField.getValue());
        if (!errs.isEmpty()) { errorLabel.setText(String.join("\n",errs)); errorLabel.setVisible(true); errorLabel.setManaged(true); return; }
        try {
            if (editing == null) svc.createModule(nameField.getText(), periodField.getValue(), durationField.getValue());
            else svc.updateModule(editing.getId(), nameField.getText(), periodField.getValue(), durationField.getValue());
            AppNavigator.showModules();
        } catch (Exception e) { errorLabel.setText(e.getMessage()); errorLabel.setVisible(true); errorLabel.setManaged(true); }
    }
    @FXML private void onBack() { AppNavigator.showModules(); }
}
