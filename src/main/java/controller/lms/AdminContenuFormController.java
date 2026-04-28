package controller.lms;

import entities.Contenu;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.web.HTMLEditor;
import javafx.stage.FileChooser;
import service.lms.ContenuService;
import service.lms.FileUploadService;
import util.AppNavigator;
import validation.LmsValidator;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class AdminContenuFormController implements Initializable {

    @FXML private Label formTitle;
    @FXML private Label breadcrumb;
    @FXML private Label errorLabel;
    @FXML private Label fileLabel;
    @FXML private TextField titleField;
    @FXML private ComboBox<String> typeField;
    @FXML private CheckBox publishedCheck;
    @FXML private RadioButton editorRadio;
    @FXML private RadioButton uploadRadio;
    @FXML private VBox editorSection;
    @FXML private VBox fileSection;
    @FXML private Button chooseFileButton;
    @FXML private HTMLEditor contentEditor;

    private final ContenuService svc = new ContenuService();
    private final FileUploadService fus = new FileUploadService();

    private Contenu editing;
    private File selectedFile;

    @Override
    public void initialize(URL u, ResourceBundle r) {
        typeField.setItems(FXCollections.observableArrayList("VIDEO", "QUIZ", "TEXT", "EXERCICE", "COURS"));
        typeField.setValue("TEXT");
        editorRadio.setSelected(true);
        refreshSourceVisibility();
    }

    public void setContenu(Contenu c) {
        editing = c;
        if (c == null) {
            formTitle.setText("Create New Content");
            breadcrumb.setText("New Content");
            titleField.clear();
            typeField.setValue("TEXT");
            publishedCheck.setSelected(false);
            fileLabel.setText("No file selected");
            contentEditor.setHtmlText("");
            editorRadio.setSelected(true);
        } else {
            formTitle.setText("Edit Content");
            breadcrumb.setText("Edit: " + c.getTitle());
            titleField.setText(c.getTitle());
            typeField.setValue(c.getType());
            publishedCheck.setSelected(c.getPublished() == 1);
            contentEditor.setHtmlText(c.getContentHtml() == null ? "" : c.getContentHtml());
            if (c.getFileName() != null && !c.getFileName().isBlank()) {
                fileLabel.setText(c.getFileName());
                uploadRadio.setSelected(true);
            } else {
                fileLabel.setText("No file selected");
                editorRadio.setSelected(true);
            }
        }
        selectedFile = null;
        refreshSourceVisibility();
    }

    @FXML
    private void onSourceChanged() {
        refreshSourceVisibility();
    }

    @FXML
    private void onChooseFile() {
        uploadRadio.setSelected(true);
        refreshSourceVisibility();

        FileChooser fc = new FileChooser();
        fc.setTitle("Select Content File");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Supported", "*.pdf", "*.doc", "*.docx", "*.ppt", "*.pptx", "*.xls", "*.xlsx", "*.mp4", "*.mp3", "*.jpg", "*.jpeg", "*.png", "*.gif"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File f = fc.showOpenDialog(titleField.getScene().getWindow());
        if (f != null) {
            if (f.length() > LmsValidator.MAX_FILE_SIZE) {
                new Alert(Alert.AlertType.ERROR, "File exceeds 50 MB limit.").showAndWait();
                return;
            }
            selectedFile = f;
            fileLabel.setText(f.getName());
        }
    }

    @FXML
    private void onSave() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        boolean writeMode = editorRadio.isSelected();
        String editorHtml = contentEditor.getHtmlText();
        boolean hasFile = selectedFile != null || (editing != null && editing.getFileName() != null && !editing.getFileName().isBlank() && uploadRadio.isSelected());

        List<String> errs = LmsValidator.validateContenuForm(titleField.getText(), typeField.getValue(), writeMode, editorHtml, hasFile);
        if (!errs.isEmpty()) {
            errorLabel.setText(String.join("\n", errs));
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
            return;
        }

        try {
            String fileName = null;
            String fileType = null;
            Integer fileSize = null;
            String contentHtml = writeMode ? editorHtml : null;

            if (!writeMode) {
                if (selectedFile != null) {
                    fileName = fus.saveFile(selectedFile, selectedFile.getName());
                    fileType = guessType(selectedFile.getName());
                    fileSize = (int) selectedFile.length();
                } else if (editing != null) {
                    fileName = editing.getFileName();
                    fileType = editing.getFileType();
                    fileSize = editing.getFileSize();
                }
            }

            if (editing == null) {
                svc.createContenu(titleField.getText(), typeField.getValue(), publishedCheck.isSelected(), fileName, fileType, fileSize, contentHtml);
            } else {
                svc.updateContenu(editing.getId(), titleField.getText(), typeField.getValue(), publishedCheck.isSelected(), fileName, fileType, fileSize, contentHtml);
            }

            AppNavigator.showContenu();
        } catch (Exception e) {
            errorLabel.setText(e.getMessage());
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }

    @FXML
    private void onBack() {
        AppNavigator.showContenu();
    }

    private void refreshSourceVisibility() {
        boolean writeMode = editorRadio.isSelected();
        editorSection.setVisible(writeMode);
        editorSection.setManaged(writeMode);
        fileSection.setVisible(!writeMode);
        fileSection.setManaged(!writeMode);
        chooseFileButton.setDisable(writeMode);
        if (writeMode) {
            selectedFile = null;
        }
    }

    private String guessType(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }
}
