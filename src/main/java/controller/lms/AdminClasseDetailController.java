package controller.lms;

import entities.Classe;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import service.UserService;
import service.lms.*;
import util.AppNavigator;
import dto.lms.*;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class AdminClasseDetailController implements Initializable {

    @FXML private Label breadcrumb, className, statusBadge, programLabel, capacityLabel, datesLabel;
    @FXML private ProgressBar capacityBar;

    @FXML private ComboBox<UserOptionDto> studentSelector, teacherSelector;

    @FXML private TableView<StudentEnrollmentRowDto> studentTable;
    @FXML private TableColumn<StudentEnrollmentRowDto, String> colStudentName, colStudentEnrolled, colStudentActive, colStudentActions;

    @FXML private TableView<TeacherAssignmentRowDto> teacherTable;
    @FXML private TableColumn<TeacherAssignmentRowDto, String> colTeacherName, colTeacherModule, colTeacherActive, colTeacherCreated, colTeacherActions;

    @FXML private TableView<ModuleRowDto> moduleTable;
    @FXML private TableColumn<ModuleRowDto, String> colModName, colModTeacher;

    private final ClasseService classeSvc = new ClasseService();
    private final EnrollmentService enrollSvc = new EnrollmentService();
    private final TeacherAssignmentService taSvc = new TeacherAssignmentService();
    private final ClassDeliveryService cdSvc = new ClassDeliveryService();
    private final UserService userSvc = new UserService();

    private Integer classeId;
    
    @Override
    public void initialize(URL u, ResourceBundle r) {
        // --- Student Table ---
        colStudentName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmail()));
        colStudentEnrolled.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEnrolledAt()));
        colStudentActive.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getActive()));
        colStudentActive.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String i, boolean e) {
                super.updateItem(i, e);
                if (e || i == null) { setGraphic(null); setText(null); return; }
                Label b = new Label(i);
                b.getStyleClass().addAll("badge", "Yes".equals(i) ? "badge-active" : "badge-inactive");
                setGraphic(b); setText(null);
            }
        });
        colStudentActions.setCellFactory(col -> new TableCell<>() {
            final Button tog = new Button("Toggle"), rem = new Button("Remove");
            final HBox bx = new HBox(4, tog, rem);
            {
                tog.getStyleClass().add("ghost-button");
                rem.getStyleClass().add("danger-button");
                tog.setOnAction(e -> toggleStudent(getTableView().getItems().get(getIndex())));
                rem.setOnAction(e -> removeStudent(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(String i, boolean e) {
                super.updateItem(i, e);
                setGraphic(e ? null : bx); setText(null);
            }
        });

        // --- Teacher Table ---
        colTeacherName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmail()));
        colTeacherModule.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getModuleName()));
        colTeacherActive.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getActive()));
        colTeacherCreated.setCellValueFactory(c -> new SimpleStringProperty("Yes".equals(c.getValue().getHasCreatedModule()) ? "✓" : "✗"));
        colTeacherActions.setCellFactory(col -> new TableCell<>() {
            final Button tog = new Button("Toggle"), rem = new Button("Remove");
            final HBox bx = new HBox(4, tog, rem);
            {
                tog.getStyleClass().add("ghost-button");
                rem.getStyleClass().add("danger-button");
                tog.setOnAction(e -> toggleTeacher(getTableView().getItems().get(getIndex())));
                rem.setOnAction(e -> removeTeacher(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(String i, boolean e) {
                super.updateItem(i, e);
                setGraphic(e ? null : bx); setText(null);
            }
        });

        // --- Module Table ---
        colModName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getModuleName()));
        colModTeacher.setCellValueFactory(c -> {
            Integer modId = c.getValue().getModuleId();
            if (modId == null) return new SimpleStringProperty("?");
            String teacherEmail = teacherTable.getItems().stream()
                    .filter(tc -> modId.equals(tc.getModuleId()))
                    .map(TeacherAssignmentRowDto::getEmail)
                    .findFirst().orElse("—");
            return new SimpleStringProperty(teacherEmail);
        });

        // --- Selectors ---
        StringConverter<UserOptionDto> uc = new StringConverter<>() {
            @Override public String toString(UserOptionDto u) { return u == null ? "" : u.getEmail(); }
            @Override public UserOptionDto fromString(String s) { return null; }
        };
        studentSelector.setConverter(uc);
        teacherSelector.setConverter(uc);
    }

    public void setClasse(Classe c) {
        this.classeId = c.getId();
        breadcrumb.setText(c.getName());
        className.setText(c.getName());
        statusBadge.setText(c.getStatus());
        statusBadge.getStyleClass().addAll("badge", "badge-" + c.getStatus());
        programLabel.setText("Program: " + (c.getProgram() != null ? c.getProgram().getName() : "None"));

        long active = classeSvc.countActiveStudents(c.getId());
        capacityLabel.setText("Capacity: " + active + "/" + c.getCapacity());
        double ratio = c.getCapacity() > 0 ? (double) active / c.getCapacity() : 0;
        capacityBar.setProgress(ratio);
        if (ratio >= 1.0) capacityBar.getStyleClass().add("capacity-bar-full");

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        datesLabel.setText((c.getStartDate() != null ? df.format(c.getStartDate()) : "?") + " - " + 
                           (c.getEndDate() != null ? df.format(c.getEndDate()) : "?"));

        loadTeachers(); // load teachers first so mod col works
        loadStudents();
        loadModules();
        loadSelectors();
    }

    private void loadStudents() {
        studentTable.setItems(FXCollections.observableArrayList(enrollSvc.getStudentsForClasseDto(classeId)));
    }

    private void loadTeachers() {
        teacherTable.setItems(FXCollections.observableArrayList(taSvc.getTeachersForClasseDto(classeId)));
    }

    private void loadModules() {
        moduleTable.setItems(FXCollections.observableArrayList(cdSvc.getModulesForClasseDto(classeId)));
    }

    private void loadSelectors() {
        Set<Integer> enrolled = studentTable.getItems().stream().map(StudentEnrollmentRowDto::getStudentId).collect(Collectors.toSet());
        List<UserOptionDto> students = userSvc.getAllUsers(1, 1000).stream()
            .filter(u -> {
                String r = u.getRole();
                if (r == null) return false;
                r = r.toUpperCase();
                if (r.startsWith("ROLE_")) r = r.substring(5);
                return "STUDENT".equals(r) && !enrolled.contains(u.getId());
            })
            .map(u -> new UserOptionDto(u.getId(), u.getEmail()))
            .collect(Collectors.toList());
        studentSelector.setItems(FXCollections.observableArrayList(students));

        Set<Integer> assigned = teacherTable.getItems().stream().map(TeacherAssignmentRowDto::getTeacherId).collect(Collectors.toSet());
        List<UserOptionDto> teachers = userSvc.getAllUsers(1, 1000).stream()
            .filter(u -> {
                String r = u.getRole();
                if (r == null) return false;
                r = r.toUpperCase();
                if (r.startsWith("ROLE_")) r = r.substring(5);
                return "TEACHER".equals(r) && !assigned.contains(u.getId());
            })
            .map(u -> new UserOptionDto(u.getId(), u.getEmail()))
            .collect(Collectors.toList());
        teacherSelector.setItems(FXCollections.observableArrayList(teachers));
    }

    @FXML private void onEnrollStudent() {
        UserOptionDto s = studentSelector.getValue();
        if (s == null) {
            new Alert(Alert.AlertType.WARNING, "Select a student.").showAndWait();
            return;
        }
        try {
            enrollSvc.enrollStudent(s.getUserId(), classeId);
            refreshAll();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }

    @FXML private void onAssignTeacher() {
        UserOptionDto t = teacherSelector.getValue();
        if (t == null) {
            new Alert(Alert.AlertType.WARNING, "Select a teacher.").showAndWait();
            return;
        }
        try {
            taSvc.assignTeacher(t.getUserId(), classeId);
            refreshAll();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }

    private void toggleStudent(StudentEnrollmentRowDto sc) {
        try {
            enrollSvc.toggleEnrollmentActive(sc.getId());
            refreshAll();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }

    private void removeStudent(StudentEnrollmentRowDto sc) {
        new Alert(Alert.AlertType.CONFIRMATION, "Unenroll this student?", ButtonType.YES, ButtonType.NO).showAndWait().ifPresent(b -> {
            if(b == ButtonType.YES) {
                enrollSvc.unenrollStudent(sc.getId());
                refreshAll();
            }
        });
    }

    private void toggleTeacher(TeacherAssignmentRowDto tc) {
        try {
            taSvc.toggleTeacherActive(tc.getId());
            refreshAll();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }

    private void removeTeacher(TeacherAssignmentRowDto tc) {
        taSvc.removeTeacher(tc.getId());
        refreshAll();
    }

    private void refreshAll() {
        Classe c = classeSvc.findById(classeId).orElse(null);
        if (c != null) {
            setClasse(c);
        }
    }

    @FXML private void onBackToList() {
        AppNavigator.showClasses();
    }
}
