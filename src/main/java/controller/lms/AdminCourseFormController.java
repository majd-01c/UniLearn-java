package controller.lms;
import entities.Course;import javafx.fxml.FXML;import javafx.fxml.Initializable;import javafx.scene.control.*;
import service.lms.CourseService;import util.AppNavigator;import validation.LmsValidator;import java.net.URL;import java.util.List;import java.util.ResourceBundle;
public class AdminCourseFormController implements Initializable {
    @FXML private Label formTitle,breadcrumb,errorLabel; @FXML private TextField titleField;
    private final CourseService svc=new CourseService(); private Course editing;
    @Override public void initialize(URL u,ResourceBundle r){}
    public void setCourse(Course c){editing=c;if(c!=null){formTitle.setText("Edit Course");breadcrumb.setText("Edit: "+c.getTitle());titleField.setText(c.getTitle());}}
    @FXML private void onSave(){errorLabel.setVisible(false);errorLabel.setManaged(false);List<String> errs=LmsValidator.validateCourseForm(titleField.getText());if(!errs.isEmpty()){errorLabel.setText(String.join("\n",errs));errorLabel.setVisible(true);errorLabel.setManaged(true);return;}try{if(editing==null)svc.createCourse(titleField.getText());else svc.updateCourse(editing.getId(),titleField.getText());AppNavigator.showCourses();}catch(Exception e){errorLabel.setText(e.getMessage());errorLabel.setVisible(true);errorLabel.setManaged(true);}}
    @FXML private void onBack(){AppNavigator.showCourses();}
}
