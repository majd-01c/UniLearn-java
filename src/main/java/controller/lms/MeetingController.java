package controller.lms;

import dto.lms.StudentClasseRowDto;
import dto.lms.TeacherAssignmentRowDto;
import entities.ClassMeeting;
import entities.User;
import entities.job_offer.JobOfferMeeting;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import service.lms.MeetingService;
import service.job_offer.JobOfferMeetingService;
import util.AppNavigator;
import util.JcefMeetingWindow;

import java.awt.Desktop;
import java.net.URI;
import java.net.URL;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class MeetingController implements Initializable {

    @FXML private Label studentClasseNameLabel;
    @FXML private VBox studentLiveContainer;
    @FXML private VBox studentScheduledContainer;
    @FXML private Label studentEmptyLabel;

    @FXML private Label teacherClasseNameLabel;
    @FXML private VBox teacherMeetingsContainer;
    @FXML private Label teacherEmptyLabel;

    @FXML private Label roomTitleLabel;
    @FXML private Label roomClasseLabel;
    @FXML private Label roomUserLabel;
    @FXML private Button roomEndButton;
    @FXML private WebView meetingWebView;

    private final MeetingService meetingService = new MeetingService();
    private final JobOfferMeetingService jobOfferMeetingService = new JobOfferMeetingService();
    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy");

    private StudentClasseRowDto studentClasse;
    private TeacherAssignmentRowDto teacherClasse;
    private User currentUser;
    private ClassMeeting currentMeeting;
    private JobOfferMeeting currentJobOfferMeeting;
    private boolean teacherRoom;
    private boolean jobOfferRoom;
    private boolean jobOfferPartnerRoom;
    private boolean browserOpenedForCurrentRoom;
    private boolean jcefOpenedForCurrentRoom;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    public void setStudentClasse(StudentClasseRowDto classe, User student) {
        this.studentClasse = classe;
        this.currentUser = student;
        if (studentClasseNameLabel != null && classe != null) {
            studentClasseNameLabel.setText(classe.getClasseName());
        }
        loadStudentMeetings();
    }

    public void setTeacherClasse(TeacherAssignmentRowDto teacherClasse, User teacher) {
        this.teacherClasse = teacherClasse;
        this.currentUser = teacher;
        if (teacherClasseNameLabel != null && teacherClasse != null) {
            teacherClasseNameLabel.setText(teacherClasse.getClasseName());
        }
        loadTeacherMeetings();
    }

    public void setRoom(ClassMeeting meeting,
                        TeacherAssignmentRowDto teacherContext,
                        StudentClasseRowDto studentContext,
                        User user,
                        boolean isTeacher) {
        this.currentMeeting = meeting;
        this.currentJobOfferMeeting = null;
        this.teacherClasse = teacherContext;
        this.studentClasse = studentContext;
        this.currentUser = user;
        this.teacherRoom = isTeacher;
        this.jobOfferRoom = false;
        this.jobOfferPartnerRoom = false;
        this.browserOpenedForCurrentRoom = false;
        this.jcefOpenedForCurrentRoom = false;

        if (roomTitleLabel != null) {
            roomTitleLabel.setText(meeting != null ? meeting.getTitle() : "Meeting");
        }
        if (roomClasseLabel != null) {
            roomClasseLabel.setText(resolveClasseName(meeting));
        }
        if (roomUserLabel != null) {
            roomUserLabel.setText(resolveUserName(user, isTeacher));
        }
        if (roomEndButton != null) {
            roomEndButton.setVisible(isTeacher);
            roomEndButton.setManaged(isTeacher);
        }
        loadMeetingWebView();
    }

    public void setJobOfferRoom(JobOfferMeeting meeting, User user, boolean isPartner) {
        this.currentMeeting = null;
        this.currentJobOfferMeeting = meeting;
        this.teacherClasse = null;
        this.studentClasse = null;
        this.currentUser = user;
        this.teacherRoom = false;
        this.jobOfferRoom = true;
        this.jobOfferPartnerRoom = isPartner;
        this.browserOpenedForCurrentRoom = false;
        this.jcefOpenedForCurrentRoom = false;

        if (roomTitleLabel != null) {
            roomTitleLabel.setText(meeting != null ? meeting.getTitle() : "Interview Meeting");
        }
        if (roomClasseLabel != null) {
            roomClasseLabel.setText(resolveJobOfferMeetingContext(meeting));
        }
        if (roomUserLabel != null) {
            roomUserLabel.setText(resolveJobOfferUserName(user, isPartner));
        }
        if (roomEndButton != null) {
            roomEndButton.setVisible(isPartner);
            roomEndButton.setManaged(isPartner);
        }
        loadMeetingWebView();
    }

    private void loadStudentMeetings() {
        if (studentClasse == null || studentLiveContainer == null || studentScheduledContainer == null) {
            return;
        }
        studentLiveContainer.getChildren().clear();
        studentScheduledContainer.getChildren().clear();

        try {
            List<ClassMeeting> liveMeetings = meetingService.getLiveMeetingsForStudentClasse(studentClasse.getClasseId());
            List<ClassMeeting> upcomingMeetings = meetingService.getUpcomingMeetingsForStudentClasse(studentClasse.getClasseId());
            List<ClassMeeting> scheduledMeetings = upcomingMeetings.stream()
                    .filter(ClassMeeting::isScheduled)
                    .toList();

            for (ClassMeeting meeting : liveMeetings) {
                studentLiveContainer.getChildren().add(buildStudentLiveCard(meeting));
            }
            for (ClassMeeting meeting : scheduledMeetings) {
                studentScheduledContainer.getChildren().add(buildStudentScheduledRow(meeting));
            }

            boolean empty = liveMeetings.isEmpty() && scheduledMeetings.isEmpty();
            if (studentEmptyLabel != null) {
                studentEmptyLabel.setVisible(empty);
                studentEmptyLabel.setManaged(empty);
            }
        } catch (Exception e) {
            showError("Meetings unavailable", e.getMessage());
            AppNavigator.showStudentClasseView(studentClasse);
        }
    }

    private void loadTeacherMeetings() {
        if (teacherClasse == null || teacherMeetingsContainer == null) {
            return;
        }
        teacherMeetingsContainer.getChildren().clear();

        try {
            List<ClassMeeting> meetings = meetingService.getMeetingsForTeacher(teacherClasse.getId());
            if (teacherEmptyLabel != null) {
                teacherEmptyLabel.setVisible(meetings.isEmpty());
                teacherEmptyLabel.setManaged(meetings.isEmpty());
            }
            for (ClassMeeting meeting : meetings) {
                teacherMeetingsContainer.getChildren().add(buildTeacherMeetingRow(meeting));
            }
        } catch (Exception e) {
            showError("Meetings unavailable", e.getMessage());
            AppNavigator.showTeacherWorkspace(teacherClasse);
        }
    }

    private VBox buildStudentLiveCard(ClassMeeting meeting) {
        VBox card = new VBox(10);
        card.getStyleClass().add("lms-card");
        card.setPadding(new Insets(16));

        Label liveBadge = new Label("LIVE NOW");
        liveBadge.getStyleClass().addAll("badge", "badge-active");

        Label title = new Label(meeting.getTitle());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        Label teacher = new Label("Teacher: " + resolveTeacherName(meeting));
        teacher.getStyleClass().add("card-text");

        Button joinButton = new Button("Join Meeting");
        joinButton.getStyleClass().add("primary-button");
        joinButton.setMaxWidth(Double.MAX_VALUE);
        joinButton.setOnAction(event -> joinStudentMeeting(meeting));

        card.getChildren().addAll(liveBadge, title, teacher);
        if (meeting.getDescription() != null && !meeting.getDescription().isBlank()) {
            Label description = new Label(meeting.getDescription());
            description.getStyleClass().add("card-text");
            description.setWrapText(true);
            card.getChildren().add(description);
        }
        card.getChildren().add(joinButton);
        return card;
    }

    private VBox buildStudentScheduledRow(ClassMeeting meeting) {
        VBox row = new VBox(6);
        row.setPadding(new Insets(12));
        row.getStyleClass().add("lms-card");

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(meeting.getTitle());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label status = statusBadge(meeting);
        top.getChildren().addAll(title, spacer, status);

        Label meta = new Label("Teacher: " + resolveTeacherName(meeting) + " | " + scheduledText(meeting));
        meta.getStyleClass().add("card-text");
        meta.setWrapText(true);

        row.getChildren().addAll(top, meta);
        if (meeting.getDescription() != null && !meeting.getDescription().isBlank()) {
            Label description = new Label(meeting.getDescription());
            description.getStyleClass().add("card-text");
            description.setWrapText(true);
            row.getChildren().add(description);
        }
        return row;
    }

    private VBox buildTeacherMeetingRow(ClassMeeting meeting) {
        VBox row = new VBox(8);
        row.setPadding(new Insets(12));
        row.getStyleClass().add("lms-card");

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label title = new Label(meeting.getTitle());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);
        Label meta = new Label("Scheduled: " + scheduledText(meeting) + " | Created: " + createdText(meeting));
        meta.getStyleClass().add("card-text");
        meta.setWrapText(true);
        titleBox.getChildren().addAll(title, meta);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        top.getChildren().addAll(titleBox, spacer, statusBadge(meeting));

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);

        if (meeting.isLive()) {
            Button joinButton = actionButton("Join", "primary-button");
            joinButton.setOnAction(event -> joinTeacherMeeting(meeting));
            Button endButton = actionButton("End", "danger-button");
            endButton.setOnAction(event -> endMeeting(meeting));
            actions.getChildren().addAll(joinButton, endButton);
        } else if (meeting.isScheduled()) {
            Button startButton = actionButton("Start", "primary-button");
            startButton.setOnAction(event -> startTeacherMeeting(meeting));
            Button deleteButton = actionButton("Delete", "danger-button");
            deleteButton.setOnAction(event -> deleteMeeting(meeting));
            actions.getChildren().addAll(startButton, deleteButton);
        } else {
            Button deleteButton = actionButton("Delete", "danger-button");
            deleteButton.setOnAction(event -> deleteMeeting(meeting));
            actions.getChildren().add(deleteButton);
        }

        row.getChildren().addAll(top);
        if (meeting.getDescription() != null && !meeting.getDescription().isBlank()) {
            Label description = new Label(meeting.getDescription());
            description.getStyleClass().add("card-text");
            description.setWrapText(true);
            row.getChildren().add(description);
        }
        row.getChildren().addAll(new Separator(), actions);
        return row;
    }

    private Button actionButton(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().add(styleClass);
        return button;
    }

    private void joinStudentMeeting(ClassMeeting meeting) {
        try {
            ClassMeeting liveMeeting = meetingService.joinStudentMeeting(meeting.getId());
            AppNavigator.showMeetingRoom(liveMeeting, null, studentClasse, false);
        } catch (Exception e) {
            showWarning("Cannot join meeting", e.getMessage());
            loadStudentMeetings();
        }
    }

    private void startTeacherMeeting(ClassMeeting meeting) {
        try {
            ClassMeeting liveMeeting = meetingService.startMeeting(teacherClasse.getId(), meeting.getId());
            AppNavigator.showMeetingRoom(liveMeeting, teacherClasse, null, true);
        } catch (Exception e) {
            showError("Cannot start meeting", e.getMessage());
            loadTeacherMeetings();
        }
    }

    private void joinTeacherMeeting(ClassMeeting meeting) {
        try {
            ClassMeeting liveMeeting = meetingService.joinTeacherMeeting(teacherClasse.getId(), meeting.getId());
            AppNavigator.showMeetingRoom(liveMeeting, teacherClasse, null, true);
        } catch (Exception e) {
            showError("Cannot join meeting", e.getMessage());
            loadTeacherMeetings();
        }
    }

    private void endMeeting(ClassMeeting meeting) {
        if (!confirm("End meeting", "End this meeting for all participants?")) {
            return;
        }
        try {
            meetingService.endMeeting(teacherClasse.getId(), meeting.getId());
            loadTeacherMeetings();
        } catch (Exception e) {
            showError("Cannot end meeting", e.getMessage());
        }
    }

    private void deleteMeeting(ClassMeeting meeting) {
        if (!confirm("Delete meeting", "Delete this meeting?")) {
            return;
        }
        try {
            meetingService.deleteMeeting(teacherClasse.getId(), meeting.getId());
            loadTeacherMeetings();
        } catch (Exception e) {
            showError("Cannot delete meeting", e.getMessage());
        }
    }

    @FXML
    private void onNewMeeting() {
        showCreateMeetingDialog();
    }

    private void showCreateMeetingDialog() {
        if (teacherClasse == null) {
            return;
        }

        ButtonType startNowType = new ButtonType("Start Meeting Now", ButtonBar.ButtonData.OK_DONE);
        ButtonType scheduleType = new ButtonType("Schedule Meeting", ButtonBar.ButtonData.APPLY);

        javafx.scene.control.Dialog<ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Create Meeting");
        dialog.setHeaderText("Create a meeting for " + teacherClasse.getClasseName());
        dialog.getDialogPane().getButtonTypes().addAll(startNowType, scheduleType, ButtonType.CANCEL);

        TextField titleField = new TextField();
        titleField.setPromptText("Weekly lecture, Q&A session...");
        titleField.getStyleClass().add("lms-input");

        TextArea descriptionArea = new TextArea();
        descriptionArea.setPromptText("Optional description");
        descriptionArea.setPrefRowCount(3);
        descriptionArea.getStyleClass().add("lms-input");

        DatePicker scheduledDatePicker = new DatePicker();
        scheduledDatePicker.setPromptText("Optional date");
        TextField timeField = new TextField();
        timeField.setPromptText("HH:mm");
        timeField.setPrefWidth(120);
        timeField.getStyleClass().add("lms-input");

        HBox scheduleRow = new HBox(8, scheduledDatePicker, timeField);
        scheduleRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(8,
                new Label("Meeting Title"),
                titleField,
                new Label("Description"),
                descriptionArea,
                new Label("Schedule For"),
                scheduleRow);
        content.setPadding(new Insets(12));
        content.setPrefWidth(520);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(buttonType -> buttonType);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
            return;
        }

        boolean startNow = result.get() == startNowType;
        try {
            Timestamp scheduledAt = parseScheduledAt(scheduledDatePicker.getValue(), timeField.getText());
            ClassMeeting meeting = meetingService.createMeetingForTeacher(
                    teacherClasse.getId(),
                    titleField.getText(),
                    descriptionArea.getText(),
                    scheduledAt,
                    startNow
            );
            if (startNow) {
                AppNavigator.showMeetingRoom(meeting, teacherClasse, null, true);
            } else {
                loadTeacherMeetings();
            }
        } catch (Exception e) {
            showError("Cannot create meeting", e.getMessage());
        }
    }

    @FXML
    private void onBackFromStudentMeetings() {
        if (studentClasse != null) {
            AppNavigator.showStudentClasseView(studentClasse);
        } else {
            AppNavigator.showStudentLearning();
        }
    }

    @FXML
    private void onBackFromTeacherMeetings() {
        if (teacherClasse != null) {
            AppNavigator.showTeacherWorkspace(teacherClasse);
        } else {
            AppNavigator.showTeacherClasses();
        }
    }

    @FXML
    private void onLeaveRoom() {
        if (jobOfferRoom) {
            if (jobOfferPartnerRoom) {
                AppNavigator.showPartnerApplications();
            } else {
                AppNavigator.showMyJobApplications();
            }
            return;
        }
        if (teacherRoom) {
            onBackFromTeacherMeetings();
        } else {
            onBackFromStudentMeetings();
        }
    }

    @FXML
    private void onOpenInBrowser() {
        openCurrentMeetingInBrowser(true);
    }

    private boolean openCurrentMeetingInBrowser(boolean showFailureDialog) {
        if (!hasCurrentRoom()) {
            return false;
        }
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                if (showFailureDialog) {
                    showWarning("Browser unavailable", "Desktop browser integration is not available on this system.");
                }
                return false;
            }
            Desktop.getDesktop().browse(new URI(buildCurrentDirectMeetingUrl()));
            browserOpenedForCurrentRoom = true;
            return true;
        } catch (Exception e) {
            if (showFailureDialog) {
                showError("Cannot open browser", e.getMessage());
            }
            return false;
        }
    }

    private void openCurrentMeetingInJcef() {
        if (!hasCurrentRoom() || jcefOpenedForCurrentRoom) {
            return;
        }

        jcefOpenedForCurrentRoom = true;
        String url = buildCurrentDirectMeetingUrl();
        String title = "UniLearn Meeting - " + resolveCurrentRoomTitle();
        updateJcefStatus("Preparing embedded Chromium...");
        JcefMeetingWindow.openAsync(url, title, this::updateJcefStatus, throwable -> {
            String message = throwable.getMessage();
            if (message == null || message.isBlank()) {
                message = throwable.getClass().getSimpleName();
            }
            updateJcefStatus("Embedded Chromium failed. Opening your browser instead.");
            showWarning("JCEF unavailable", "Could not start the embedded Chromium meeting window. Opening your browser instead.\n\n" + message);
            openCurrentMeetingInBrowser(false);
        });
    }

    private void updateJcefStatus(String message) {
        if (meetingWebView == null || message == null) {
            return;
        }
        try {
            meetingWebView.getEngine().executeScript(
                    "const el = document.getElementById('jcef-status'); if (el) el.textContent = '"
                            + jsString(message)
                            + "';"
            );
        } catch (Exception ignored) {
            // The status page may not be loaded yet.
        }
    }

    @FXML
    private void onEndCurrentMeeting() {
        if (jobOfferRoom) {
            if (currentJobOfferMeeting == null) {
                return;
            }
            if (!confirm("End meeting", "End meeting for all participants?")) {
                return;
            }
            try {
                jobOfferMeetingService.endMeeting(currentJobOfferMeeting.getId());
                AppNavigator.showPartnerApplications();
            } catch (Exception e) {
                showError("Cannot end meeting", e.getMessage());
            }
            return;
        }
        if (currentMeeting == null || teacherClasse == null) {
            return;
        }
        if (!confirm("End meeting", "End meeting for all participants?")) {
            return;
        }
        try {
            meetingService.endMeeting(teacherClasse.getId(), currentMeeting.getId());
            AppNavigator.showTeacherMeetings(teacherClasse);
        } catch (Exception e) {
            showError("Cannot end meeting", e.getMessage());
        }
    }

    private void loadMeetingWebView() {
        if (meetingWebView == null || !hasCurrentRoom()) {
            return;
        }
        meetingWebView.setContextMenuEnabled(false);
        meetingWebView.getEngine().setJavaScriptEnabled(true);
        if (meetingService.isJcefMeetingsEnabled()) {
            meetingWebView.getEngine().loadContent(buildJcefMeetingHtml());
            openCurrentMeetingInJcef();
            return;
        }
        if (!meetingService.isEmbeddedMeetingsEnabled()) {
            meetingWebView.getEngine().loadContent(buildBrowserMeetingHtml());
            if (meetingService.isAutoOpenBrowserEnabled() && !browserOpenedForCurrentRoom) {
                openCurrentMeetingInBrowser(false);
            }
            return;
        }
        meetingWebView.getEngine().loadContent(buildJitsiHtml());
    }

    private String buildBrowserMeetingHtml() {
        String directUrl = html(buildCurrentDirectMeetingUrl());
        return """
                <!doctype html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        html, body { width: 100%%; height: 100%%; margin: 0; background: #202124; font-family: Arial, sans-serif; color: white; }
                        body { display: flex; align-items: center; justify-content: center; text-align: center; padding: 24px; box-sizing: border-box; }
                        h3 { color: #bfdbfe; margin: 0 0 12px; }
                        p { max-width: 720px; margin: 8px auto; line-height: 1.5; }
                        .details { color: #bfdbfe; font-family: Consolas, monospace; overflow-wrap: anywhere; }
                    </style>
                </head>
                <body>
                    <main>
                        <h3>Meeting opens in your browser</h3>
                        <p>Jitsi video calls need full browser support for camera, microphone, and screen sharing.</p>
                        <p>If the browser did not open automatically, use the Open in Browser button above.</p>
                        <p class="details">%s</p>
                    </main>
                </body>
                </html>
                """.formatted(directUrl);
    }

    private String buildJcefMeetingHtml() {
        String directUrl = html(buildCurrentDirectMeetingUrl());
        return """
                <!doctype html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        html, body { width: 100%%; height: 100%%; margin: 0; background: #202124; font-family: Arial, sans-serif; color: white; }
                        body { display: flex; align-items: center; justify-content: center; text-align: center; padding: 24px; box-sizing: border-box; }
                        h3 { color: #bfdbfe; margin: 0 0 12px; }
                        p { max-width: 720px; margin: 8px auto; line-height: 1.5; }
                        .details { color: #bfdbfe; font-family: Consolas, monospace; overflow-wrap: anywhere; }
                    </style>
                </head>
                <body>
                    <main>
                        <h3>Meeting opens in embedded Chromium</h3>
                        <p id="jcef-status">UniLearn is starting a JCEF window for this Jitsi room.</p>
                        <p>The first launch downloads and extracts Chromium, so it can take a few minutes.</p>
                        <p class="details">%s</p>
                    </main>
                </body>
                </html>
                """.formatted(directUrl);
    }

    private String buildJitsiHtml() {
        String host = jsString(meetingService.getJitsiHost());
        String externalApiUrl = jsString(meetingService.buildJitsiExternalApiUrl());
        String directMeetingUrl = html(buildCurrentDirectMeetingUrl());
        String room = jsString(resolveCurrentRoomCode());
        String username = jsString(resolveCurrentUserName());
        boolean hostRoom = teacherRoom || jobOfferPartnerRoom;
        String loadingText = hostRoom ? "Connecting to meeting..." : "Joining meeting...";
        String audioMuted = hostRoom ? "false" : "true";
        String toolbarButtons = hostRoom
                ? "'microphone','camera','closedcaptions','desktop','fullscreen','fodeviceselection','hangup','chat','recording','livestreaming','etherpad','sharedvideo','settings','raisehand','videoquality','filmstrip','participants-pane','tileview','select-background','mute-everyone','security'"
                : "'microphone','camera','closedcaptions','desktop','fullscreen','fodeviceselection','hangup','chat','settings','raisehand','videoquality','filmstrip','participants-pane','tileview','select-background'";
        String connectionErrorHtml = jsString("""
                <div class="error">
                    <h3>Cannot connect to the video server</h3>
                    <p>The app could not load the Jitsi integration script from the configured server.</p>
                    <p class="details">%s</p>
                    <p>Check that the Jitsi server is running, reachable, and using a trusted HTTPS certificate, or use Open in Browser.</p>
                </div>
                """.formatted(html(meetingService.buildJitsiExternalApiUrl())));
        String stuckErrorHtml = jsString("""
                <div class="error">
                    <h3>Embedded meeting could not finish loading</h3>
                    <p>Jitsi loaded, but JavaFX WebView could not start the video conference.</p>
                    <p>Use the Open in Browser button above for camera, microphone, and screen sharing.</p>
                    <p class="details">%s</p>
                </div>
                """.formatted(directMeetingUrl));

        return """
                <!doctype html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        html, body, #meet { width: 100%%; height: 100%%; margin: 0; overflow: hidden; background: #202124; font-family: Arial, sans-serif; }
                        .loading, .error { height: 100%%; display: flex; flex-direction: column; align-items: center; justify-content: center; color: white; text-align: center; padding: 24px; box-sizing: border-box; }
                        .spinner { width: 42px; height: 42px; border-radius: 50%%; border: 4px solid rgba(255,255,255,.18); border-top-color: #3b82f6; animation: spin 1s linear infinite; margin-bottom: 16px; }
                        @keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }
                        .error { color: #fecaca; background: #1f2937; }
                        .error p { max-width: 720px; margin: 6px 0; }
                        .details { color: #bfdbfe; font-family: Consolas, monospace; overflow-wrap: anywhere; }
                    </style>
                </head>
                <body>
                    <div id="meet"><div class="loading"><div class="spinner"></div><p>%s</p></div></div>
                    <script>
                        function showConnectionError() {
                            document.getElementById('meet').innerHTML = '%s';
                        }
                        let joined = false;
                        let startupTimer = setTimeout(function() {
                            if (!joined) {
                                document.getElementById('meet').innerHTML = '%s';
                            }
                        }, 20000);
                        const script = document.createElement('script');
                        script.src = '%s';
                        script.async = true;
                        script.onerror = function() {
                            clearTimeout(startupTimer);
                            showConnectionError();
                        };
                        script.onload = function() {
                            try {
                                const api = new JitsiMeetExternalAPI('%s', {
                                    roomName: '%s',
                                    width: '100%%',
                                    height: '100%%',
                                    parentNode: document.querySelector('#meet'),
                                    configOverwrite: {
                                        startWithAudioMuted: %s,
                                        startWithVideoMuted: false,
                                        enableWelcomePage: false,
                                        prejoinPageEnabled: false
                                    },
                                    interfaceConfigOverwrite: {
                                        SHOW_JITSI_WATERMARK: false,
                                        SHOW_WATERMARK_FOR_GUESTS: false,
                                        DEFAULT_BACKGROUND: '#474747',
                                        FILM_STRIP_MAX_HEIGHT: 120,
                                        TOOLBAR_BUTTONS: [%s]
                                    },
                                    userInfo: {
                                        displayName: '%s'
                                    }
                                });
                                api.addListener('videoConferenceJoined', function() {
                                    joined = true;
                                    clearTimeout(startupTimer);
                                });
                                api.addListener('readyToClose', function() {
                                    joined = true;
                                    clearTimeout(startupTimer);
                                });
                                api.addListener('browserSupport', function(event) {
                                    if (event && event.supported === false) {
                                        clearTimeout(startupTimer);
                                        document.getElementById('meet').innerHTML = '%s';
                                    }
                                });
                                api.addListener('cameraError', function() {
                                    clearTimeout(startupTimer);
                                    document.getElementById('meet').innerHTML = '%s';
                                });
                                api.addListener('micError', function() {
                                    clearTimeout(startupTimer);
                                    document.getElementById('meet').innerHTML = '%s';
                                });
                            } catch (e) {
                                clearTimeout(startupTimer);
                                document.getElementById('meet').innerHTML = '<div class="error"><h3>Error starting meeting</h3><p>' + e.message + '</p></div>';
                            }
                        };
                        document.head.appendChild(script);
                    </script>
                </body>
                </html>
                """.formatted(
                        html(loadingText),
                        connectionErrorHtml,
                        stuckErrorHtml,
                        externalApiUrl,
                        host,
                        room,
                        audioMuted,
                        toolbarButtons,
                        username,
                        stuckErrorHtml,
                        stuckErrorHtml,
                        stuckErrorHtml
                );
    }

    private boolean hasCurrentRoom() {
        return currentMeeting != null || currentJobOfferMeeting != null;
    }

    private String buildCurrentDirectMeetingUrl() {
        if (currentJobOfferMeeting != null) {
            return jobOfferMeetingService.buildDirectMeetingUrl(currentJobOfferMeeting);
        }
        return meetingService.buildDirectMeetingUrl(currentMeeting);
    }

    private String resolveCurrentRoomCode() {
        if (currentJobOfferMeeting != null) {
            return currentJobOfferMeeting.getRoomCode();
        }
        return currentMeeting != null ? currentMeeting.getRoomCode() : "";
    }

    private String resolveCurrentRoomTitle() {
        if (currentJobOfferMeeting != null) {
            return firstNonBlank(currentJobOfferMeeting.getTitle(), resolveJobOfferMeetingContext(currentJobOfferMeeting), "Meeting");
        }
        return firstNonBlank(currentMeeting != null ? currentMeeting.getTitle() : null,
                resolveClasseName(currentMeeting),
                "Meeting");
    }

    private String resolveCurrentUserName() {
        if (jobOfferRoom) {
            return resolveJobOfferUserName(currentUser, jobOfferPartnerRoom);
        }
        return resolveUserName(currentUser, teacherRoom);
    }

    private Timestamp parseScheduledAt(LocalDate date, String rawTime) {
        if (date == null) {
            return null;
        }
        LocalTime time = LocalTime.MIDNIGHT;
        String cleanTime = rawTime == null ? "" : rawTime.trim();
        if (!cleanTime.isEmpty()) {
            try {
                time = LocalTime.parse(cleanTime, DateTimeFormatter.ofPattern("H:mm"));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Time must use HH:mm format.");
            }
        }
        return Timestamp.valueOf(LocalDateTime.of(date, time));
    }

    private Label statusBadge(ClassMeeting meeting) {
        String statusValue = meeting != null && meeting.getStatus() != null ? meeting.getStatus() : "";
        Label label = new Label(statusValue.toUpperCase());
        label.getStyleClass().add("badge");
        if (ClassMeeting.STATUS_LIVE.equals(statusValue)) {
            label.getStyleClass().add("badge-active");
        } else if (ClassMeeting.STATUS_SCHEDULED.equals(statusValue)) {
            label.getStyleClass().add("badge-info");
        } else {
            label.getStyleClass().add("badge-inactive");
        }
        return label;
    }

    private String scheduledText(ClassMeeting meeting) {
        return meeting != null && meeting.getScheduledAt() != null
                ? dateTimeFormat.format(meeting.getScheduledAt())
                : "-";
    }

    private String createdText(ClassMeeting meeting) {
        return meeting != null && meeting.getCreatedAt() != null
                ? dateFormat.format(meeting.getCreatedAt())
                : "-";
    }

    private String resolveTeacherName(ClassMeeting meeting) {
        if (meeting == null || meeting.getTeacherClasse() == null || meeting.getTeacherClasse().getUser() == null) {
            return "?";
        }
        User teacher = meeting.getTeacherClasse().getUser();
        return firstNonBlank(teacher.getName(), teacher.getEmail(), "Teacher");
    }

    private String resolveClasseName(ClassMeeting meeting) {
        if (teacherClasse != null) {
            return teacherClasse.getClasseName();
        }
        if (studentClasse != null) {
            return studentClasse.getClasseName();
        }
        if (meeting != null && meeting.getTeacherClasse() != null && meeting.getTeacherClasse().getClasse() != null) {
            return meeting.getTeacherClasse().getClasse().getName();
        }
        return "Class Meeting";
    }

    private String resolveJobOfferMeetingContext(JobOfferMeeting meeting) {
        if (meeting == null || meeting.getJobOffer() == null) {
            return "Job Interview Meeting";
        }
        String offerTitle = firstNonBlank(meeting.getJobOffer().getTitle(), meeting.getJobOffer().getType(), "Job Offer");
        String studentName = meeting.getStudent() == null
                ? "Candidate"
                : firstNonBlank(meeting.getStudent().getName(), meeting.getStudent().getEmail(), "Candidate");
        return offerTitle + " | " + studentName;
    }

    private String resolveUserName(User user, boolean teacher) {
        String name = user == null ? null : firstNonBlank(user.getName(), user.getEmail(), null);
        if (name == null) {
            name = teacher ? "Teacher" : "Student";
        }
        return teacher ? name + " (Teacher)" : name;
    }

    private String resolveJobOfferUserName(User user, boolean partner) {
        String name = user == null ? null : firstNonBlank(user.getName(), user.getEmail(), null);
        if (name == null) {
            name = partner ? "Partner" : "Student";
        }
        return partner ? name + " (Partner)" : name;
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return fallback;
    }

    private String jsString(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

    private String html(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message == null ? "" : message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message == null ? "" : message);
        alert.showAndWait();
    }
}
