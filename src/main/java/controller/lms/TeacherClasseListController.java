package controller.lms;
import entities.*;import javafx.fxml.FXML;import javafx.fxml.Initializable;import javafx.geometry.Insets;import javafx.scene.control.*;import javafx.scene.layout.*;
import service.lms.TeacherAssignmentService;import util.AppNavigator;import java.net.URL;import java.util.List;import java.util.ResourceBundle;
public class TeacherClasseListController implements Initializable {
    @FXML private FlowPane classesPane;@FXML private Label emptyLabel;
    private final TeacherAssignmentService svc=new TeacherAssignmentService();private User teacher;
    @Override public void initialize(URL u,ResourceBundle r){}
    public void setTeacher(User t){this.teacher=t;loadData();}
    private void loadData(){
        classesPane.getChildren().clear();
        List<dto.lms.TeacherAssignmentRowDto> assignments = svc.getActiveClassesForTeacherDto(teacher.getId());
        if(assignments.isEmpty()){
            emptyLabel.setVisible(true);
            emptyLabel.setManaged(true);
            return;
        }
        emptyLabel.setVisible(false);
        emptyLabel.setManaged(false);
        for(dto.lms.TeacherAssignmentRowDto tc:assignments){
            VBox card=new VBox(8);
            card.getStyleClass().add("lms-card");
            card.setPrefWidth(280);
            card.setPadding(new Insets(16));
            Label name=new Label(tc.getClasseName());
            name.getStyleClass().add("card-title");
            Label prog=new Label("Program: "+tc.getClasseProgram());
            prog.getStyleClass().add("card-text");
            Label lvl=new Label(tc.getClasseLevel()+" • "+tc.getClasseSpecialty());
            lvl.getStyleClass().add("card-text");
            Label modStatus=new Label("Yes".equals(tc.getHasCreatedModule())?"Module: "+tc.getModuleName():"Module: Not Created Yet");
            modStatus.getStyleClass().addAll("badge","Yes".equals(tc.getHasCreatedModule())?"badge-published":"badge-draft");
            Button btn=new Button("Open Workspace");
            btn.getStyleClass().add("primary-button");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setOnAction(e->AppNavigator.showTeacherWorkspace(tc));
            card.getChildren().addAll(name,prog,lvl,modStatus,btn);
            classesPane.getChildren().add(card);
        }
    }
}
