package controller.lms;
import entities.Classe;import javafx.beans.property.SimpleStringProperty;import javafx.collections.FXCollections;import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;import javafx.fxml.Initializable;import javafx.scene.control.*;import javafx.scene.layout.HBox;
import service.lms.ClasseService;import util.AppNavigator;import java.net.URL;import java.util.ResourceBundle;
public class AdminClasseController implements Initializable {
    @FXML private TextField searchField;@FXML private ComboBox<String> statusFilter;@FXML private TableView<Classe> table;
    @FXML private TableColumn<Classe,String> colId,colName,colProgram,colLevel,colCapacity,colStatus,colActions;
    @FXML private Label totalLabel,activeLabel,fullLabel;
    private final ClasseService svc=new ClasseService();private FilteredList<Classe> filtered;
    @Override public void initialize(URL u,ResourceBundle r){
        statusFilter.setItems(FXCollections.observableArrayList("All","active","full","inactive"));statusFilter.setValue("All");
        colId.setCellValueFactory(c->new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        colName.setCellValueFactory(c->new SimpleStringProperty(c.getValue().getName()));
        colProgram.setCellValueFactory(c->new SimpleStringProperty(c.getValue().getProgram()!=null?c.getValue().getProgram().getName():"—"));
        colLevel.setCellValueFactory(c->new SimpleStringProperty(c.getValue().getLevel()));
        colCapacity.setCellValueFactory(c->{long active=svc.countActiveStudents(c.getValue().getId());return new SimpleStringProperty(active+"/"+c.getValue().getCapacity());});
        colStatus.setCellValueFactory(c->new SimpleStringProperty(c.getValue().getStatus()));
        colStatus.setCellFactory(col->new TableCell<>(){@Override protected void updateItem(String i,boolean e){super.updateItem(i,e);if(e||i==null){setGraphic(null);setText(null);return;}Label b=new Label(i);b.getStyleClass().addAll("badge","badge-"+i);setGraphic(b);setText(null);}});
        colActions.setCellFactory(col->new TableCell<>(){final Button v=new Button("Detail"),ed=new Button("Edit"),dl=new Button("Delete");final HBox bx=new HBox(6,v,ed,dl);{v.getStyleClass().add("ghost-button");ed.getStyleClass().add("ghost-button");dl.getStyleClass().add("danger-button");v.setOnAction(e->AppNavigator.showClasseDetail(getTableView().getItems().get(getIndex())));ed.setOnAction(e->AppNavigator.showClasseForm(getTableView().getItems().get(getIndex())));dl.setOnAction(e->onDel(getTableView().getItems().get(getIndex())));}@Override protected void updateItem(String i,boolean e){super.updateItem(i,e);setGraphic(e?null:bx);setText(null);}});
        searchField.textProperty().addListener((o,ov,nv)->applyFilter());statusFilter.valueProperty().addListener((o,ov,nv)->applyFilter());loadData();
    }
    private void loadData(){var all=FXCollections.observableArrayList(svc.listAll());filtered=new FilteredList<>(all);table.setItems(filtered);totalLabel.setText(String.valueOf(all.size()));activeLabel.setText(String.valueOf(all.stream().filter(c->"active".equals(c.getStatus())).count()));fullLabel.setText(String.valueOf(all.stream().filter(c->"full".equals(c.getStatus())).count()));}
    private void applyFilter(){String s=searchField.getText()!=null?searchField.getText().toLowerCase():"";String st=statusFilter.getValue();filtered.setPredicate(c->(s.isEmpty()||c.getName().toLowerCase().contains(s))&&("All".equals(st)||st==null||st.equals(c.getStatus())));}
    @FXML private void onNew(){AppNavigator.showClasseForm(null);}
    private void onDel(Classe c){new Alert(Alert.AlertType.CONFIRMATION,"Delete '"+c.getName()+"'?",ButtonType.YES,ButtonType.NO).showAndWait().ifPresent(b->{if(b==ButtonType.YES){svc.deleteClasse(c.getId());loadData();}});}
}
