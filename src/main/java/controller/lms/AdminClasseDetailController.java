package controller.lms;

import entities.Classe;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
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

    @FXML private Label studentCountBadge, teacherCountBadge, moduleCountBadge;
    @FXML private Label noStudentsLabel, noAvailableStudentsLabel;
    @FXML private Label noTeachersLabel, noAvailableTeachersLabel;
    @FXML private Label noModulesLabel;

    @FXML private VBox enrolledStudentsContainer;
    @FXML private FlowPane availableStudentsContainer;

    @FXML private VBox assignedTeachersContainer;
    @FXML private FlowPane availableTeachersContainer;

    @FXML private VBox modulesContainer;

    private final ClasseService classeSvc = new ClasseService();
    private final EnrollmentService enrollSvc = new EnrollmentService();
    private final TeacherAssignmentService taSvc = new TeacherAssignmentService();
    private final ClassDeliveryService cdSvc = new ClassDeliveryService();
    private final UserService userSvc = new UserService();

    private Integer classeId;
    
    // Cache teachers for module display
    private List<TeacherAssignmentRowDto> currentAssignedTeachers = new ArrayList<>();

    @Override
    public void initialize(URL u, ResourceBundle r) {
        // Nothing complex in init, everything happens when class is set
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
        else capacityBar.getStyleClass().remove("capacity-bar-full");

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        datesLabel.setText((c.getStartDate() != null ? df.format(c.getStartDate()) : "?") + " - " + 
                           (c.getEndDate() != null ? df.format(c.getEndDate()) : "?"));

        loadAllData();
    }

    private void loadAllData() {
        // Teachers first so modules can match them
        currentAssignedTeachers = taSvc.getTeachersForClasseDto(classeId);
        
        loadTeachers();
        loadStudents();
        loadModules();
    }

    // ==========================================
    // STUDENTS
    // ==========================================

    private void loadStudents() {
        List<StudentEnrollmentRowDto> enrolled = enrollSvc.getStudentsForClasseDto(classeId);
        studentCountBadge.setText(String.valueOf(enrolled.size()));
        
        enrolledStudentsContainer.getChildren().clear();
        if (enrolled.isEmpty()) {
            noStudentsLabel.setVisible(true); noStudentsLabel.setManaged(true);
        } else {
            noStudentsLabel.setVisible(false); noStudentsLabel.setManaged(false);
            for (StudentEnrollmentRowDto s : enrolled) {
                enrolledStudentsContainer.getChildren().add(buildStudentRow(s));
            }
        }

        // Available to enroll
        Set<Integer> enrolledIds = enrolled.stream().map(StudentEnrollmentRowDto::getStudentId).collect(Collectors.toSet());
        List<UserOptionDto> available = userSvc.getAllUsers(1, 1000).stream()
            .filter(u -> {
                String r = u.getRole();
                if (r == null) return false;
                r = r.toUpperCase();
                if (r.startsWith("ROLE_")) r = r.substring(5);
                return "STUDENT".equals(r) && !enrolledIds.contains(u.getId());
            })
            .map(u -> new UserOptionDto(u.getId(), u.getEmail()))
            .collect(Collectors.toList());

        availableStudentsContainer.getChildren().clear();
        if (available.isEmpty()) {
            noAvailableStudentsLabel.setVisible(true); noAvailableStudentsLabel.setManaged(true);
        } else {
            noAvailableStudentsLabel.setVisible(false); noAvailableStudentsLabel.setManaged(false);
            for (UserOptionDto s : available) {
                availableStudentsContainer.getChildren().add(buildAvailableStudentCard(s));
            }
        }
    }

    private HBox buildStudentRow(StudentEnrollmentRowDto s) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 0, 12, 0));
        row.setStyle("-fx-border-color: transparent transparent #e0e0e0 transparent; -fx-border-width: 0 0 1 0;");

        // Avatar
        Label avatar = new Label(s.getEmail().substring(0, 1).toUpperCase());
        avatar.setStyle("-fx-background-color: #1976D2; -fx-text-fill: white; -fx-font-weight: bold; -fx-alignment: center; -fx-background-radius: 50; -fx-min-width: 36; -fx-min-height: 36;");
        
        Label email = new Label(s.getEmail());
        email.getStyleClass().add("card-text");
        email.setPrefWidth(250);

        Label enrolledAt = new Label(s.getEnrolledAt());
        enrolledAt.getStyleClass().add("card-text");
        enrolledAt.setPrefWidth(180);

        Label status = new Label("Yes".equals(s.getActive()) ? "ACTIVE" : "INACTIVE");
        status.getStyleClass().addAll("badge", "Yes".equals(s.getActive()) ? "badge-active" : "badge-inactive");
        status.setPrefWidth(100);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button toggleBtn = new Button("Yes".equals(s.getActive()) ? "⏸" : "▶");
        toggleBtn.getStyleClass().add("ghost-button");
        toggleBtn.setTooltip(new Tooltip("Toggle active status"));
        toggleBtn.setOnAction(e -> toggleStudent(s));

        Button removeBtn = new Button("👤x");
        removeBtn.getStyleClass().add("ghost-button");
        removeBtn.setStyle("-fx-text-fill: #d32f2f; -fx-border-color: #ef9a9a; -fx-border-radius: 4;");
        removeBtn.setTooltip(new Tooltip("Remove from class"));
        removeBtn.setOnAction(e -> removeStudent(s));

        HBox actions = new HBox(8, toggleBtn, removeBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        row.getChildren().addAll(avatar, email, enrolledAt, status, spacer, actions);
        return row;
    }

    private HBox buildAvailableStudentCard(UserOptionDto u) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-padding: 8 16 8 8; -fx-background-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 4, 0, 0, 2);");

        Label avatar = new Label(u.getEmail().substring(0, 1).toUpperCase());
        avatar.setStyle("-fx-background-color: #607d8b; -fx-text-fill: white; -fx-font-weight: bold; -fx-alignment: center; -fx-background-radius: 50; -fx-min-width: 32; -fx-min-height: 32;");

        VBox text = new VBox(2);
        Label name = new Label(u.getEmail().split("@")[0]);
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label email = new Label(u.getEmail());
        email.setStyle("-fx-text-fill: #757575; -fx-font-size: 11px;");
        text.getChildren().addAll(name, email);

        Button addBtn = new Button("+");
        addBtn.setStyle("-fx-background-color: #00bfa5; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; -fx-padding: 4 12; -fx-cursor: hand;");
        addBtn.setOnAction(e -> enrollStudent(u));

        card.getChildren().addAll(avatar, text, addBtn);
        return card;
    }

    // ==========================================
    // TEACHERS
    // ==========================================

    private void loadTeachers() {
        teacherCountBadge.setText(String.valueOf(currentAssignedTeachers.size()));
        
        assignedTeachersContainer.getChildren().clear();
        if (currentAssignedTeachers.isEmpty()) {
            noTeachersLabel.setVisible(true); noTeachersLabel.setManaged(true);
        } else {
            noTeachersLabel.setVisible(false); noTeachersLabel.setManaged(false);
            for (TeacherAssignmentRowDto t : currentAssignedTeachers) {
                assignedTeachersContainer.getChildren().add(buildTeacherRow(t));
            }
        }

        Set<Integer> assignedIds = currentAssignedTeachers.stream().map(TeacherAssignmentRowDto::getTeacherId).collect(Collectors.toSet());
        List<UserOptionDto> available = userSvc.getAllUsers(1, 1000).stream()
            .filter(u -> {
                String r = u.getRole();
                if (r == null) return false;
                r = r.toUpperCase();
                if (r.startsWith("ROLE_")) r = r.substring(5);
                return "TEACHER".equals(r) && !assignedIds.contains(u.getId());
            })
            .map(u -> new UserOptionDto(u.getId(), u.getEmail()))
            .collect(Collectors.toList());

        availableTeachersContainer.getChildren().clear();
        if (available.isEmpty()) {
            noAvailableTeachersLabel.setVisible(true); noAvailableTeachersLabel.setManaged(true);
        } else {
            noAvailableTeachersLabel.setVisible(false); noAvailableTeachersLabel.setManaged(false);
            for (UserOptionDto t : available) {
                availableTeachersContainer.getChildren().add(buildAvailableTeacherCard(t));
            }
        }
    }

    private HBox buildTeacherRow(TeacherAssignmentRowDto t) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 0, 12, 0));
        row.setStyle("-fx-border-color: transparent transparent #e0e0e0 transparent; -fx-border-width: 0 0 1 0;");

        Label avatar = new Label(t.getEmail().substring(0, 1).toUpperCase());
        avatar.setStyle("-fx-background-color: #FF5722; -fx-text-fill: white; -fx-font-weight: bold; -fx-alignment: center; -fx-background-radius: 50; -fx-min-width: 36; -fx-min-height: 36;");
        
        Label email = new Label(t.getEmail());
        email.getStyleClass().add("card-text");
        email.setPrefWidth(200);

        Label module = new Label(t.getModuleName() != null ? t.getModuleName() : "No Module Yet (" + t.getHasCreatedModule() + ")");
        module.getStyleClass().add("card-text");
        module.setPrefWidth(180);

        Label status = new Label("Yes".equals(t.getActive()) ? "ACTIVE" : "INACTIVE");
        status.getStyleClass().addAll("badge", "Yes".equals(t.getActive()) ? "badge-active" : "badge-inactive");
        status.setPrefWidth(100);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button toggleBtn = new Button("Yes".equals(t.getActive()) ? "⏸" : "▶");
        toggleBtn.getStyleClass().add("ghost-button");
        toggleBtn.setTooltip(new Tooltip("Toggle active status"));
        toggleBtn.setOnAction(e -> toggleTeacher(t));

        Button removeBtn = new Button("👨‍🏫x");
        removeBtn.getStyleClass().add("ghost-button");
        removeBtn.setStyle("-fx-text-fill: #d32f2f; -fx-border-color: #ef9a9a; -fx-border-radius: 4;");
        removeBtn.setTooltip(new Tooltip("Remove from class"));
        removeBtn.setOnAction(e -> removeTeacher(t));

        HBox actions = new HBox(8, toggleBtn, removeBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        row.getChildren().addAll(avatar, email, module, status, spacer, actions);
        return row;
    }

    private HBox buildAvailableTeacherCard(UserOptionDto u) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-padding: 8 16 8 8; -fx-background-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 4, 0, 0, 2);");

        Label avatar = new Label(u.getEmail().substring(0, 1).toUpperCase());
        avatar.setStyle("-fx-background-color: #795548; -fx-text-fill: white; -fx-font-weight: bold; -fx-alignment: center; -fx-background-radius: 50; -fx-min-width: 32; -fx-min-height: 32;");

        VBox text = new VBox(2);
        Label name = new Label(u.getEmail().split("@")[0]);
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label email = new Label(u.getEmail());
        email.setStyle("-fx-text-fill: #757575; -fx-font-size: 11px;");
        text.getChildren().addAll(name, email);

        Button addBtn = new Button("+");
        addBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; -fx-padding: 4 12; -fx-cursor: hand;");
        addBtn.setOnAction(e -> assignTeacher(u));

        card.getChildren().addAll(avatar, text, addBtn);
        return card;
    }

    // ==========================================
    // MODULES
    // ==========================================

    private void loadModules() {
        List<ModuleRowDto> modules = cdSvc.getModulesForClasseDto(classeId);
        moduleCountBadge.setText(String.valueOf(modules.size()));

        modulesContainer.getChildren().clear();
        if (modules.isEmpty()) {
            noModulesLabel.setVisible(true); noModulesLabel.setManaged(true);
        } else {
            noModulesLabel.setVisible(false); noModulesLabel.setManaged(false);
            for (ModuleRowDto m : modules) {
                modulesContainer.getChildren().add(buildModuleRow(m));
            }
        }
    }

    private HBox buildModuleRow(ModuleRowDto m) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 0, 12, 0));
        row.setStyle("-fx-border-color: transparent transparent #e0e0e0 transparent; -fx-border-width: 0 0 1 0;");

        Label name = new Label(m.getModuleName());
        name.getStyleClass().add("card-text");
        name.setStyle("-fx-font-weight: bold;");
        name.setPrefWidth(300);

        String teacherEmail = currentAssignedTeachers.stream()
                .filter(tc -> m.getModuleId().equals(tc.getModuleId()))
                .map(TeacherAssignmentRowDto::getEmail)
                .findFirst().orElse("—");
        
        Label createdBy = new Label(teacherEmail);
        createdBy.getStyleClass().add("card-text");

        row.getChildren().addAll(name, createdBy);
        return row;
    }

    // ==========================================
    // ACTIONS
    // ==========================================

    private void enrollStudent(UserOptionDto s) {
        try {
            enrollSvc.enrollStudent(s.getUserId(), classeId);
            refreshAll();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }

    private void assignTeacher(UserOptionDto t) {
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
                try {
                    enrollSvc.unenrollStudent(sc.getId());
                    refreshAll();
                } catch (Exception e) {
                    new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
                }
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
        new Alert(Alert.AlertType.CONFIRMATION, "Remove this teacher?", ButtonType.YES, ButtonType.NO).showAndWait().ifPresent(b -> {
            if(b == ButtonType.YES) {
                try {
                    taSvc.removeTeacher(tc.getId());
                    refreshAll();
                } catch (Exception e) {
                    new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
                }
            }
        });
    }

    private void refreshAll() {
        Classe c = classeSvc.findById(classeId).orElse(null);
        if (c != null) { setClasse(c); }
    }

    @FXML private void onBackToList() {
        AppNavigator.showClasses();
    }
}
