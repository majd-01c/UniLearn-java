package controller.job_offer;

import entities.User;
import entities.job_offer.JobApplication;
import entities.job_offer.JobApplicationStatus;
import entities.job_offer.JobOffer;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import service.job_offer.GeminiApplicationFeedbackService;
import service.job_offer.JobOfferMeetingService;
import services.job_offer.ServiceJobApplication;
import services.job_offer.ServiceJobOffer;
import util.AppNavigator;
import util.RoleGuard;

import java.net.URL;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.IntStream;

public class ApplicationReviewController implements Initializable {

    private static final int DEFAULT_MEETING_DURATION_MINUTES = 30;
    private static final List<String> MEETING_HOURS = IntStream.rangeClosed(1, 12)
            .mapToObj(value -> String.format("%02d", value))
            .toList();
    private static final List<String> MEETING_MINUTES = IntStream.range(0, 60)
            .mapToObj(value -> String.format("%02d", value))
            .toList();
    private static final List<String> MEETING_MERIDIEMS = List.of("AM", "PM");

    private record MeetingSchedule(Timestamp startsAt, Timestamp endsAt) {
    }

    @FXML
    private VBox rootContainer;

    @FXML
    private Label jobTitleLabel;

    @FXML
    private Label candidateLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label appliedDateLabel;

    @FXML
    private TextArea messageArea;

    @FXML
    private Label scoreLabel;

    @FXML
    private TextArea scoreBreakdownArea;

    @FXML
    private Button approveButton;

    @FXML
    private Button rejectButton;

    @FXML
    private Button backButton;

    private JobApplication application;
    private User currentUser;
    private ServiceJobApplication serviceJobApplication;
    private ServiceJobOffer serviceJobOffer;
    private GeminiApplicationFeedbackService aiFeedbackService;
    private JobOfferMeetingService jobOfferMeetingService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        serviceJobApplication = new ServiceJobApplication();
        serviceJobOffer = new ServiceJobOffer();
        aiFeedbackService = new GeminiApplicationFeedbackService();
        jobOfferMeetingService = new JobOfferMeetingService();

        // Ensure reject button keeps themed style even if FXML class parsing is inconsistent.
        if (rejectButton != null) {
            if (!rejectButton.getStyleClass().contains("ghost-button")) {
                rejectButton.getStyleClass().add("ghost-button");
            }
            if (!rejectButton.getStyleClass().contains("job-offer-danger-button")) {
                rejectButton.getStyleClass().add("job-offer-danger-button");
            }
        }
    }

    public void setApplication(JobApplication app) {
        this.application = app;
        if (app != null) {
            displayApplicationDetails();
        }
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        setupUI();
    }

    private void displayApplicationDetails() {
        JobOffer offer = application.getJobOffer();
        jobTitleLabel.setText(offer != null && offer.getTitle() != null ? offer.getTitle() : "Unknown");

        User candidate = application.getUser();
        candidateLabel.setText(candidate != null && candidate.getEmail() != null ? candidate.getEmail() : "Unknown");

        statusLabel.setText(application.getStatus() != null ? application.getStatus() : "Unknown");
        appliedDateLabel.setText(application.getCreatedAt() != null ? application.getCreatedAt().toString() : "Unknown");

        messageArea.setText(application.getMessage() != null ? application.getMessage() : "No message provided");
        messageArea.setEditable(false);
        messageArea.setWrapText(true);

        if (application.getScore() != null) {
            scoreLabel.setText("Score: " + application.getScore() + "/100");
            if (!scoreLabel.getStyleClass().contains("job-offer-score-highlight")) {
                scoreLabel.getStyleClass().add("job-offer-score-highlight");
            }
        } else {
            scoreLabel.setText("Score: Not yet evaluated");
            scoreLabel.getStyleClass().remove("job-offer-score-highlight");
        }

        scoreBreakdownArea.setText(application.getScoreBreakdown() != null ? application.getScoreBreakdown() : "No breakdown available");
        scoreBreakdownArea.setEditable(false);
        scoreBreakdownArea.setWrapText(true);
    }

    private void setupUI() {
        if (currentUser == null || application == null) {
            approveButton.setDisable(true);
            rejectButton.setDisable(true);
            return;
        }

        JobOffer offer = application.getJobOffer();
        if (offer == null) {
            approveButton.setDisable(true);
            rejectButton.setDisable(true);
            return;
        }

        boolean isAdmin = RoleGuard.isAdmin(currentUser);
        boolean canReview = isAdmin || isCurrentUserOfferOwner(offer);

        approveButton.setDisable(!canReview);
        rejectButton.setDisable(!canReview);
    }

    @FXML
    private void onApprove() {
        if (!canReview()) {
            showError("Error", "You don't have permission to review this application");
            return;
        }

        TextInputDialog scoreDialog = new TextInputDialog("85");
        scoreDialog.setTitle("Evaluate Application");
        scoreDialog.setHeaderText("Enter applicant score (0-100)");
        scoreDialog.setContentText("Score:");

        scoreDialog.showAndWait().ifPresent(score -> {
            try {
                int scoreValue = Integer.parseInt(score);
                if (scoreValue < 0 || scoreValue > 100) {
                    showError("Validation", "Score must be between 0 and 100");
                    return;
                }

                application.setStatus("ACCEPTED");
                application.setScore(scoreValue);
                application.setUpdatedAt(new Timestamp(Instant.now().toEpochMilli()));
                application.setStatusNotified((byte) 0);
                application.setStatusNotifiedAt(null);
                String feedback = promptForDecisionFeedback(JobApplicationStatus.ACCEPTED, application.getStatusMessage());
                if (feedback == null) {
                    return;
                }
                MeetingSchedule meetingSchedule = promptForMeetingSchedule();
                if (meetingSchedule == null) {
                    return;
                }
                application.setStatusMessage(feedback);

                serviceJobApplication.update(application);
                jobOfferMeetingService.scheduleMeetingForPartner(
                        application.getId(),
                        "Interview - " + (application.getJobOffer() != null ? application.getJobOffer().getTitle() : "Job offer"),
                        "Interview meeting for " + (application.getUser() != null ? application.getUser().getEmail() : "candidate") + ".",
                        meetingSchedule.startsAt(),
                        meetingSchedule.endsAt()
                );
                showInfo("Success", "Application approved successfully");
                AppNavigator.showPartnerApplications();
            } catch (NumberFormatException e) {
                showError("Validation", "Please enter a valid number");
            } catch (Exception e) {
                showError("Error", "Failed to approve application: " + e.getMessage());
            }
        });
    }

    @FXML
    private void onReject() {
        if (!canReview()) {
            showError("Error", "You don't have permission to review this application");
            return;
        }

        try {
            application.setStatus("REJECTED");
            application.setUpdatedAt(new Timestamp(Instant.now().toEpochMilli()));
            application.setStatusNotified((byte) 0);
            application.setStatusNotifiedAt(null);
            String feedback = promptForDecisionFeedback(JobApplicationStatus.REJECTED, application.getStatusMessage());
            if (feedback == null) {
                return;
            }
            application.setStatusMessage(feedback);

            serviceJobApplication.update(application);
            showInfo("Success", "Application rejected");
            AppNavigator.showPartnerApplications();
        } catch (Exception e) {
            showError("Error", "Failed to reject application: " + e.getMessage());
        }
    }

    @FXML
    private void onBack() {
        AppNavigator.showPartnerApplications();
    }

    private boolean canReview() {
        JobOffer offer = application.getJobOffer();
        if (offer == null || currentUser == null) {
            return false;
        }

        return RoleGuard.isAdmin(currentUser) || isCurrentUserOfferOwner(offer);
    }

    private boolean isCurrentUserOfferOwner(JobOffer offerReference) {
        if (offerReference == null || currentUser == null) {
            return false;
        }

        try {
            return serviceJobOffer.getALL().stream()
                    .anyMatch(offer -> offer != null
                            && offer.getId() == offerReference.getId()
                            && offer.getUser() != null
                            && offer.getUser().getId().equals(currentUser.getId()));
        } catch (Exception exception) {
            return false;
        }
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String promptForDecisionFeedback(JobApplicationStatus decision, String existingMessage) {
        String draftMessage = resolveDecisionFeedback(decision, existingMessage);

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(decision == JobApplicationStatus.ACCEPTED ? "Approval Feedback" : "Rejection Feedback");
        dialog.setHeaderText("Review the message that will be sent to the student.");

        ButtonType saveButtonType = new ButtonType("Save Message", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextArea feedbackArea = new TextArea(draftMessage);
        feedbackArea.setWrapText(true);
        feedbackArea.setPrefRowCount(10);
        feedbackArea.setPrefColumnCount(60);
        dialog.getDialogPane().setContent(feedbackArea);

        dialog.setResultConverter(buttonType -> buttonType == saveButtonType ? feedbackArea.getText() : null);

        return dialog.showAndWait()
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .orElse(null);
    }

    private String resolveDecisionFeedback(JobApplicationStatus decision, String existingMessage) {
        String trimmedExisting = existingMessage == null ? "" : existingMessage.trim();
        if (!trimmedExisting.isEmpty()) {
            return trimmedExisting;
        }

        return aiFeedbackService.generateFeedback(application, decision);
    }

    private MeetingSchedule promptForMeetingSchedule() {
        Dialog<MeetingSchedule> dialog = new Dialog<>();
        dialog.setTitle("Interview Meeting");
        dialog.setHeaderText("Schedule the meeting for this accepted application.");

        ButtonType saveButtonType = new ButtonType("Save Schedule", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        DatePicker datePicker = new DatePicker();
        datePicker.setPromptText("24/05/2026");
        datePicker.setPrefWidth(170);

        ComboBox<String> startHourCombo = createMeetingTimeCombo(MEETING_HOURS, 68);
        ComboBox<String> startMinuteCombo = createMeetingTimeCombo(MEETING_MINUTES, 68);
        ComboBox<String> startMeridiemCombo = createMeetingTimeCombo(MEETING_MERIDIEMS, 76);
        ComboBox<String> endHourCombo = createMeetingTimeCombo(MEETING_HOURS, 68);
        ComboBox<String> endMinuteCombo = createMeetingTimeCombo(MEETING_MINUTES, 68);
        ComboBox<String> endMeridiemCombo = createMeetingTimeCombo(MEETING_MERIDIEMS, 76);
        setDefaultMeetingPickerWindow(datePicker,
                startHourCombo, startMinuteCombo, startMeridiemCombo,
                endHourCombo, endMinuteCombo, endMeridiemCombo);

        HBox scheduleRow = new HBox(16,
                datePicker,
                buildTimePickerRow("Start", startHourCombo, startMinuteCombo, startMeridiemCombo),
                buildTimePickerRow("End", endHourCombo, endMinuteCombo, endMeridiemCombo));
        scheduleRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(8,
                new Label("Meeting date, start time, and end time"),
                scheduleRow,
                new Label("Students can join only between the selected start and end time."));
        content.setPadding(new Insets(12));
        content.setPrefWidth(760);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(buttonType -> {
            if (buttonType != saveButtonType) {
                return null;
            }
            try {
                LocalTime startTime = readTimePickerValue("start", startHourCombo, startMinuteCombo, startMeridiemCombo);
                LocalTime endTime = readTimePickerValue("end", endHourCombo, endMinuteCombo, endMeridiemCombo);
                return parseMeetingSchedule(datePicker.getValue(), startTime, endTime);
            } catch (IllegalArgumentException exception) {
                showError("Validation", exception.getMessage());
                return null;
            }
        });

        return dialog.showAndWait().orElse(null);
    }

    private ComboBox<String> createMeetingTimeCombo(List<String> values, double width) {
        ComboBox<String> comboBox = new ComboBox<>(FXCollections.observableArrayList(values));
        comboBox.setPrefWidth(width);
        comboBox.setVisibleRowCount(Math.min(6, values.size()));
        comboBox.getStyleClass().addAll("job-offer-filter-combo", "job-offer-time-picker-combo");
        return comboBox;
    }

    private VBox buildTimePickerRow(String labelText,
                                    ComboBox<String> hourCombo,
                                    ComboBox<String> minuteCombo,
                                    ComboBox<String> meridiemCombo) {
        Label label = new Label(labelText);
        label.getStyleClass().add("job-offer-admin-label");

        Label separator = new Label(":");
        separator.getStyleClass().add("job-offer-card-meta");

        HBox pickerRow = new HBox(5, hourCombo, separator, minuteCombo, meridiemCombo);
        pickerRow.setAlignment(Pos.CENTER_LEFT);

        return new VBox(4, label, pickerRow);
    }

    private void setDefaultMeetingPickerWindow(DatePicker datePicker,
                                               ComboBox<String> startHourCombo,
                                               ComboBox<String> startMinuteCombo,
                                               ComboBox<String> startMeridiemCombo,
                                               ComboBox<String> endHourCombo,
                                               ComboBox<String> endMinuteCombo,
                                               ComboBox<String> endMeridiemCombo) {
        LocalDateTime startsAt = resolveDefaultMeetingStart();
        LocalDateTime endsAt = startsAt.plusMinutes(DEFAULT_MEETING_DURATION_MINUTES);
        datePicker.setValue(startsAt.toLocalDate());
        setTimePickerValue(startsAt.toLocalTime(), startHourCombo, startMinuteCombo, startMeridiemCombo);
        setTimePickerValue(endsAt.toLocalTime(), endHourCombo, endMinuteCombo, endMeridiemCombo);
    }

    private LocalDateTime resolveDefaultMeetingStart() {
        LocalDateTime startsAt = roundToNextFiveMinutes(LocalDateTime.now().plusMinutes(5));
        LocalDateTime endsAt = startsAt.plusMinutes(DEFAULT_MEETING_DURATION_MINUTES);
        if (!startsAt.toLocalDate().equals(endsAt.toLocalDate())) {
            return LocalDate.now().plusDays(1).atTime(9, 0);
        }
        return startsAt;
    }

    private LocalDateTime roundToNextFiveMinutes(LocalDateTime dateTime) {
        LocalDateTime cleanedDateTime = dateTime.withSecond(0).withNano(0);
        int roundedMinute = ((cleanedDateTime.getMinute() + 4) / 5) * 5;
        if (roundedMinute >= 60) {
            return cleanedDateTime.plusHours(1).withMinute(0);
        }
        return cleanedDateTime.withMinute(roundedMinute);
    }

    private void setTimePickerValue(LocalTime time,
                                    ComboBox<String> hourCombo,
                                    ComboBox<String> minuteCombo,
                                    ComboBox<String> meridiemCombo) {
        int hour = time.getHour();
        String meridiem = hour >= 12 ? "PM" : "AM";
        int displayHour = hour % 12;
        if (displayHour == 0) {
            displayHour = 12;
        }

        hourCombo.setValue(String.format("%02d", displayHour));
        minuteCombo.setValue(String.format("%02d", time.getMinute()));
        meridiemCombo.setValue(meridiem);
    }

    private LocalTime readTimePickerValue(String fieldName,
                                          ComboBox<String> hourCombo,
                                          ComboBox<String> minuteCombo,
                                          ComboBox<String> meridiemCombo) {
        String hourText = hourCombo.getValue();
        String minuteText = minuteCombo.getValue();
        String meridiem = meridiemCombo.getValue();
        if (hourText == null || minuteText == null || meridiem == null) {
            throw new IllegalArgumentException("Choose the meeting " + fieldName + " time.");
        }

        int hour = Integer.parseInt(hourText);
        int minute = Integer.parseInt(minuteText);
        if ("PM".equals(meridiem) && hour < 12) {
            hour += 12;
        } else if ("AM".equals(meridiem) && hour == 12) {
            hour = 0;
        }

        return LocalTime.of(hour, minute);
    }

    private MeetingSchedule parseMeetingSchedule(LocalDate date, LocalTime startTime, LocalTime endTime) {
        if (date == null) {
            throw new IllegalArgumentException("Choose a meeting date.");
        }

        LocalDateTime startsAt = LocalDateTime.of(date, startTime);
        LocalDateTime endsAt = LocalDateTime.of(date, endTime);

        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Meeting end time must be after the start time.");
        }
        if (!endsAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Meeting end time must be in the future. Check the date and AM/PM selection.");
        }

        return new MeetingSchedule(Timestamp.valueOf(startsAt), Timestamp.valueOf(endsAt));
    }
}
