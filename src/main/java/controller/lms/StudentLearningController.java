package controller.lms;
import entities.*;import javafx.fxml.FXML;import javafx.fxml.Initializable;import javafx.geometry.Insets;import javafx.scene.control.*;import javafx.scene.layout.*;
import service.lms.EnrollmentService;import util.AppNavigator;import java.net.URL;import java.util.List;import java.util.ResourceBundle;
public class StudentLearningController implements Initializable {
    @FXML private FlowPane classesPane;@FXML private Label emptyLabel;
    private final EnrollmentService svc=new EnrollmentService();private User student;
    @Override public void initialize(URL u,ResourceBundle r){}
    public void setStudent(User s){this.student=s;loadData();}
    private void loadData(){
        classesPane.getChildren().clear();
        List<dto.lms.StudentClasseRowDto> enrollments=svc.getActiveEnrollmentsForStudentDto(student.getId());
        if(enrollments.isEmpty()){
            emptyLabel.setVisible(true);
            emptyLabel.setManaged(true);
            return;
        }
        emptyLabel.setVisible(false);
        emptyLabel.setManaged(false);
        for(dto.lms.StudentClasseRowDto c:enrollments){
            VBox card=new VBox(8);
            card.getStyleClass().add("lms-card");
            card.setPrefWidth(300);
            card.setPadding(new Insets(16));
            Label name=new Label(c.getClasseName());
            name.getStyleClass().add("card-title");
            Label prog=new Label("Program: "+c.getProgramName());
            prog.getStyleClass().add("card-text");
            Label info=new Label(c.getLevel()+" • "+c.getSpecialty());
            info.getStyleClass().add("card-text");
            Label stat=new Label(c.getStatus());
            stat.getStyleClass().addAll("badge","badge-"+c.getStatus());
            Button btn=new Button("Enter Class");
            btn.getStyleClass().add("primary-button");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setOnAction(e->AppNavigator.showStudentClasseView(c));
            card.getChildren().addAll(name,prog,info,stat,btn);
            classesPane.getChildren().add(card);
        }
    }
}
