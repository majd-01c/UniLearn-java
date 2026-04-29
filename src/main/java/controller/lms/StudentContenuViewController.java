package controller.lms;

import dto.lms.ContenuRowDto;
import entities.Contenu;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import service.lms.ContenuService;
import service.lms.FileUploadService;
import util.AppNavigator;

import java.awt.Desktop;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class StudentContenuViewController implements Initializable {

    @FXML private Label contenuTitle;
    @FXML private Label typeBadge;
    @FXML private Label fileInfo;
    @FXML private Label noFileLabel;
    @FXML private Label positionLabel;
    @FXML private VBox fileSection;
    @FXML private VBox editorSection;
    @FXML private Button downloadBtn;
    @FXML private Button prevBtn;
    @FXML private Button nextBtn;
    @FXML private WebView contentViewer;

    private final FileUploadService fus = new FileUploadService();
    private final ContenuService contenuSvc = new ContenuService();

    private List<ContenuRowDto> allVisible;
    private int currentIndex;

    @Override
    public void initialize(URL u, ResourceBundle r) {
    }

    public void init(ContenuRowDto c, List<ContenuRowDto> all, int idx) {
        this.allVisible = all;
        this.currentIndex = idx;
        render(c);
    }

    private void render(ContenuRowDto dto) {
        Contenu c = contenuSvc.findById(dto.getContenuId()).orElse(null);
        if (c == null) {
            new Alert(Alert.AlertType.ERROR, "Content not found").showAndWait();
            AppNavigator.showStudentLearning();
            return;
        }

        contenuTitle.setText(c.getTitle());
        typeBadge.setText(c.getType());
        typeBadge.getStyleClass().setAll("badge", "badge-" + c.getType().toLowerCase());

        boolean hasFile = c.getFileName() != null && !c.getFileName().isBlank();
        boolean hasEditorContent = c.getContentHtml() != null && !c.getContentHtml().isBlank();

        if (hasFile) {
            fileSection.setVisible(true);
            fileSection.setManaged(true);
            fileInfo.setText("File: " + c.getFileName() + (c.getFileSize() != null ? " (" + formatSize(c.getFileSize()) + ")" : ""));
            downloadBtn.setDisable(false);
        } else {
            fileSection.setVisible(false);
            fileSection.setManaged(false);
            downloadBtn.setDisable(true);
        }

        if (hasEditorContent) {
            editorSection.setVisible(true);
            editorSection.setManaged(true);
            contentViewer.getEngine().loadContent(c.getContentHtml());
        } else {
            editorSection.setVisible(false);
            editorSection.setManaged(false);
        }

        noFileLabel.setVisible(!hasFile && !hasEditorContent);
        noFileLabel.setManaged(!hasFile && !hasEditorContent);

        positionLabel.setText((currentIndex + 1) + " / " + allVisible.size());
        prevBtn.setDisable(currentIndex <= 0);
        nextBtn.setDisable(currentIndex >= allVisible.size() - 1);
    }

    @FXML
    private void onPrev() {
        if (currentIndex > 0) {
            currentIndex--;
            AppNavigator.showStudentContenuView(allVisible.get(currentIndex), allVisible, currentIndex);
        }
    }

    @FXML
    private void onNext() {
        if (currentIndex < allVisible.size() - 1) {
            currentIndex++;
            AppNavigator.showStudentContenuView(allVisible.get(currentIndex), allVisible, currentIndex);
        }
    }

    @FXML
    private void onDownload() {
        ContenuRowDto dto = allVisible.get(currentIndex);
        Contenu c = contenuSvc.findById(dto.getContenuId()).orElse(null);
        if (c == null || c.getFileName() == null) {
            return;
        }

        File f = fus.getFile(c.getFileName());
        if (f != null && f.exists()) {
            try {
                Desktop.getDesktop().open(f);
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, "Cannot open file: " + e.getMessage()).showAndWait();
            }
        } else {
            new Alert(Alert.AlertType.WARNING, "File not found on disk.").showAndWait();
        }
    }

    @FXML
    private void onBackToCourse() {
        AppNavigator.showStudentLearning();
    }

    private String formatSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / 1048576.0);
    }
}
