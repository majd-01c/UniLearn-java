package controller.lms;
import entities.Contenu;import javafx.beans.property.SimpleStringProperty;import javafx.collections.FXCollections;import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;import javafx.fxml.Initializable;import javafx.scene.control.*;import javafx.scene.layout.HBox;
import service.lms.ContenuService;import util.AppNavigator;import java.net.URL;import java.util.ResourceBundle;
public class AdminContenuController implements Initializable {
    @FXML private TextField searchField;@FXML private ComboBox<String> typeFilter;@FXML private TableView<Contenu> table;
    @FXML private TableColumn<Contenu,String> colId,colTitle,colType,colPublished,colFile,colActions;
    private final ContenuService svc=new ContenuService();private FilteredList<Contenu> filtered;
    @Override public void initialize(URL u,ResourceBundle r){
        typeFilter.setItems(FXCollections.observableArrayList("All","VIDEO","QUIZ","TEXT","EXERCICE","COURS"));typeFilter.setValue("All");
        colId.setCellValueFactory(c->new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        colTitle.setCellValueFactory(c->new SimpleStringProperty(c.getValue().getTitle()));
        colType.setCellValueFactory(c->new SimpleStringProperty(c.getValue().getType()));
        colType.setCellFactory(col->new TableCell<>(){@Override protected void updateItem(String i,boolean e){super.updateItem(i,e);if(e||i==null){setGraphic(null);setText(null);return;}Label b=new Label(i);b.getStyleClass().addAll("badge","badge-"+i.toLowerCase());setGraphic(b);setText(null);}});
        colPublished.setCellValueFactory(c->new SimpleStringProperty(c.getValue().getPublished()==1?"Published":"Draft"));
        colPublished.setCellFactory(col->new TableCell<>(){@Override protected void updateItem(String i,boolean e){super.updateItem(i,e);if(e||i==null){setGraphic(null);setText(null);return;}Label b=new Label(i);b.getStyleClass().addAll("badge","Published".equals(i)?"badge-published":"badge-draft");setGraphic(b);setText(null);}});
        colFile.setCellValueFactory(c->new SimpleStringProperty(
            c.getValue().getFileName()!=null && !c.getValue().getFileName().isBlank() ? c.getValue().getFileName()
                : c.getValue().getContentHtml()!=null && !c.getValue().getContentHtml().isBlank() ? "In-app content"
                : "—"));
        colActions.setCellFactory(col->new TableCell<>(){final Button ed=new Button("Edit"),dl=new Button("Delete");final HBox bx=new HBox(6,ed,dl);{ed.getStyleClass().add("ghost-button");dl.getStyleClass().add("danger-button");ed.setOnAction(e->AppNavigator.showContenuForm(getTableView().getItems().get(getIndex())));dl.setOnAction(e->onDel(getTableView().getItems().get(getIndex())));}@Override protected void updateItem(String i,boolean e){super.updateItem(i,e);setGraphic(e?null:bx);setText(null);}});
        searchField.textProperty().addListener((o,ov,nv)->applyFilter());typeFilter.valueProperty().addListener((o,ov,nv)->applyFilter());
        loadData();
    }
    private void loadData(){filtered=new FilteredList<>(FXCollections.observableArrayList(svc.listAll()));table.setItems(filtered);}
    private void applyFilter(){String s=searchField.getText()!=null?searchField.getText().toLowerCase():"";String t=typeFilter.getValue();filtered.setPredicate(c->(s.isEmpty()||c.getTitle().toLowerCase().contains(s))&&("All".equals(t)||t==null||c.getType().equals(t)));}
    @FXML private void onNew(){AppNavigator.showContenuForm(null);}
    private void onDel(Contenu c){new Alert(Alert.AlertType.CONFIRMATION,"Delete '"+c.getTitle()+"'?",ButtonType.YES,ButtonType.NO).showAndWait().ifPresent(b->{if(b==ButtonType.YES){svc.deleteContenu(c.getId());loadData();}});}
}
