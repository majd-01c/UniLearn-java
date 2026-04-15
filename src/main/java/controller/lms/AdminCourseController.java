package controller.lms;
import entities.Course;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;import javafx.fxml.Initializable;import javafx.scene.control.*;import javafx.scene.layout.HBox;
import service.lms.CourseService;import util.AppNavigator;import java.net.URL;import java.text.SimpleDateFormat;import java.util.ResourceBundle;
public class AdminCourseController implements Initializable {
    @FXML private TextField searchField; @FXML private TableView<Course> table;
    @FXML private TableColumn<Course,String> colId,colTitle,colCreated,colActions;
    private final CourseService svc = new CourseService(); private FilteredList<Course> filtered; private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    @Override public void initialize(URL u, ResourceBundle r) {
        colId.setCellValueFactory(c->new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        colTitle.setCellValueFactory(c->new SimpleStringProperty(c.getValue().getTitle()));
        colCreated.setCellValueFactory(c->new SimpleStringProperty(c.getValue().getCreatedAt()!=null?sdf.format(c.getValue().getCreatedAt()):""));
        colActions.setCellFactory(col->new TableCell<>(){final Button e=new Button("Edit"),d=new Button("Delete");final HBox b=new HBox(6,e,d);{e.getStyleClass().add("ghost-button");d.getStyleClass().add("danger-button");e.setOnAction(ev->AppNavigator.showCourseForm(getTableView().getItems().get(getIndex())));d.setOnAction(ev->onDel(getTableView().getItems().get(getIndex())));}@Override protected void updateItem(String i,boolean empty){super.updateItem(i,empty);setGraphic(empty?null:b);setText(null);}});
        searchField.textProperty().addListener((o,ov,nv)->filtered.setPredicate(c->nv==null||nv.isEmpty()||c.getTitle().toLowerCase().contains(nv.toLowerCase())));
        loadData();
    }
    private void loadData(){filtered=new FilteredList<>(FXCollections.observableArrayList(svc.listAll()));table.setItems(filtered);}
    @FXML private void onNew(){AppNavigator.showCourseForm(null);}
    private void onDel(Course c){new Alert(Alert.AlertType.CONFIRMATION,"Delete '"+c.getTitle()+"'?",ButtonType.YES,ButtonType.NO).showAndWait().ifPresent(b->{if(b==ButtonType.YES){svc.deleteCourse(c.getId());loadData();}});}
}
