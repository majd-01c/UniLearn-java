package controller.lms;
import entities.Contenu;import javafx.collections.FXCollections;import javafx.fxml.FXML;import javafx.fxml.Initializable;import javafx.scene.control.*;import javafx.stage.FileChooser;
import service.lms.ContenuService;import service.lms.FileUploadService;import util.AppNavigator;import validation.LmsValidator;
import java.io.File;import java.net.URL;import java.util.List;import java.util.ResourceBundle;
public class AdminContenuFormController implements Initializable {
    @FXML private Label formTitle,breadcrumb,errorLabel,fileLabel;@FXML private TextField titleField;@FXML private ComboBox<String> typeField;@FXML private CheckBox publishedCheck;
    private final ContenuService svc=new ContenuService();private final FileUploadService fus=new FileUploadService();
    private Contenu editing;private File selectedFile;
    @Override public void initialize(URL u,ResourceBundle r){typeField.setItems(FXCollections.observableArrayList("VIDEO","QUIZ","TEXT","EXERCICE","COURS"));typeField.setValue("TEXT");}
    public void setContenu(Contenu c){editing=c;if(c!=null){formTitle.setText("Edit Content");breadcrumb.setText("Edit: "+c.getTitle());titleField.setText(c.getTitle());typeField.setValue(c.getType());publishedCheck.setSelected(c.getPublished()==1);if(c.getFileName()!=null)fileLabel.setText(c.getFileName());}}
    @FXML private void onChooseFile(){
        FileChooser fc=new FileChooser();fc.setTitle("Select Content File");
        fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("All Supported","*.pdf","*.doc","*.docx","*.ppt","*.pptx","*.xls","*.xlsx","*.mp4","*.mp3","*.jpg","*.jpeg","*.png","*.gif"),new FileChooser.ExtensionFilter("All Files","*.*"));
        File f=fc.showOpenDialog(titleField.getScene().getWindow());
        if(f!=null){if(f.length()>LmsValidator.MAX_FILE_SIZE){new Alert(Alert.AlertType.ERROR,"File exceeds 50 MB limit.").showAndWait();return;}selectedFile=f;fileLabel.setText(f.getName());}
    }
    @FXML private void onSave(){errorLabel.setVisible(false);errorLabel.setManaged(false);
        List<String> errs=LmsValidator.validateContenuForm(titleField.getText(),typeField.getValue());if(!errs.isEmpty()){errorLabel.setText(String.join("\n",errs));errorLabel.setVisible(true);errorLabel.setManaged(true);return;}
        try{String fn=editing!=null?editing.getFileName():null;String ft=editing!=null?editing.getFileType():null;Integer fs=editing!=null?editing.getFileSize():null;
            if(selectedFile!=null){fn=fus.saveFile(selectedFile,selectedFile.getName());ft=guessType(selectedFile.getName());fs=(int)selectedFile.length();}
            if(editing==null)svc.createContenu(titleField.getText(),typeField.getValue(),publishedCheck.isSelected(),fn,ft,fs);
            else svc.updateContenu(editing.getId(),titleField.getText(),typeField.getValue(),publishedCheck.isSelected(),fn,ft,fs);
            AppNavigator.showContenu();
        }catch(Exception e){errorLabel.setText(e.getMessage());errorLabel.setVisible(true);errorLabel.setManaged(true);}
    }
    @FXML private void onBack(){AppNavigator.showContenu();}
    private String guessType(String name){String n=name.toLowerCase();if(n.endsWith(".pdf"))return"application/pdf";if(n.endsWith(".mp4"))return"video/mp4";if(n.endsWith(".mp3"))return"audio/mpeg";if(n.endsWith(".jpg")||n.endsWith(".jpeg"))return"image/jpeg";if(n.endsWith(".png"))return"image/png";return"application/octet-stream";}
}
