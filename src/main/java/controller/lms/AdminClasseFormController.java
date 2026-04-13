package controller.lms;
import entities.Classe;import entities.Program;import javafx.collections.FXCollections;import javafx.fxml.FXML;import javafx.fxml.Initializable;import javafx.scene.control.*;import javafx.util.StringConverter;
import service.lms.ClasseService;import service.lms.ProgramService;import util.AppNavigator;import validation.LmsValidator;
import java.net.URL;import java.util.List;import java.util.ResourceBundle;
public class AdminClasseFormController implements Initializable {
    public static class EnumOption {
        public final String value; public final String label;
        public EnumOption(String v, String l) { this.value = v; this.label = l; }
        @Override public String toString() { return label; }
    }
    @FXML private Label formTitle,breadcrumb,errorLabel;@FXML private TextField nameField;
    @FXML private ComboBox<EnumOption> levelField,specialtyField;
    @FXML private ComboBox<Program> programField;@FXML private Spinner<Integer> capacityField;@FXML private DatePicker startDateField,endDateField;
    private final ClasseService svc=new ClasseService();private final ProgramService pSvc=new ProgramService();private Classe editing;

    @Override public void initialize(URL u,ResourceBundle r){
        levelField.setItems(FXCollections.observableArrayList(new EnumOption("L1","1st Year (L1)"),new EnumOption("L2","2nd Year (L2)"),new EnumOption("L3","3rd Year (L3)"),new EnumOption("M1","Master 1 (M1)"),new EnumOption("M2","Master 2 (M2)")));
        specialtyField.setItems(FXCollections.observableArrayList(new EnumOption("computer_science","Computer Science"),new EnumOption("mathematics","Mathematics"),new EnumOption("physics","Physics"),new EnumOption("chemistry","Chemistry"),new EnumOption("biology","Biology"),new EnumOption("engineering","Engineering"),new EnumOption("business","Business Administration"),new EnumOption("economics","Economics"),new EnumOption("law","Law"),new EnumOption("medicine","Medicine"),new EnumOption("languages","Languages & Literature"),new EnumOption("arts","Arts & Design"),new EnumOption("other","Other")));
        capacityField.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1,500,30));
        programField.setItems(FXCollections.observableArrayList(pSvc.listAll()));
        programField.setConverter(new StringConverter<>(){@Override public String toString(Program p){return p==null?"":p.getName();}@Override public Program fromString(String s){return null;}});
    }
    public void setClasse(Classe c){editing=c;if(c!=null){formTitle.setText("Edit Class");breadcrumb.setText("Edit: "+c.getName());nameField.setText(c.getName());levelField.getItems().stream().filter(o->o.value.equals(c.getLevel())).findFirst().ifPresent(levelField::setValue);specialtyField.getItems().stream().filter(o->o.value.equals(c.getSpecialty())).findFirst().ifPresent(specialtyField::setValue);capacityField.getValueFactory().setValue(c.getCapacity());if(c.getProgram()!=null)programField.getItems().stream().filter(p->p.getId().equals(c.getProgram().getId())).findFirst().ifPresent(programField::setValue);if(c.getStartDate()!=null)startDateField.setValue(new java.sql.Date(c.getStartDate().getTime()).toLocalDate());if(c.getEndDate()!=null)endDateField.setValue(new java.sql.Date(c.getEndDate().getTime()).toLocalDate());}}
    @FXML private void onSave(){errorLabel.setVisible(false);errorLabel.setManaged(false);
        java.sql.Date sd=startDateField.getValue()!=null?java.sql.Date.valueOf(startDateField.getValue()):null;java.sql.Date ed=endDateField.getValue()!=null?java.sql.Date.valueOf(endDateField.getValue()):null;
        String lv=levelField.getValue()!=null?levelField.getValue().value:"";
        String sp=specialtyField.getValue()!=null?specialtyField.getValue().value:"";
        List<String> errs=LmsValidator.validateClasseForm(nameField.getText(),lv,sp,capacityField.getValue(),sd,ed);
        if(programField.getValue()==null)errs.add("Program is required.");
        if(lv.isEmpty())errs.add("Level is required.");
        if(sp.isEmpty())errs.add("Specialty is required.");
        if(!errs.isEmpty()){errorLabel.setText(String.join("\n",errs));errorLabel.setVisible(true);errorLabel.setManaged(true);return;}
        try{if(editing==null)svc.createClasse(nameField.getText(),programField.getValue().getId(),lv,sp,capacityField.getValue(),sd,ed,null);
        else svc.updateClasse(editing.getId(),nameField.getText(),lv,sp,capacityField.getValue(),sd,ed,editing.getStatus(),null);AppNavigator.showClasses();}
        catch(Exception e){errorLabel.setText(e.getMessage());errorLabel.setVisible(true);errorLabel.setManaged(true);}
    }
    @FXML private void onBack(){AppNavigator.showClasses();}
}
