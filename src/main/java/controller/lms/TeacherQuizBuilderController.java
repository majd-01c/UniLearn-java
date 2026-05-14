package controller.lms;

import dto.lms.CourseRowDto;
import entities.Choice;
import entities.Contenu;
import entities.Question;
import entities.Quiz;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import service.lms.ClassDeliveryService;
import service.lms.ContenuService;
import services.ServiceChoice;
import services.ServiceQuestion;
import services.ServiceQuiz;
import util.AppNavigator;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class TeacherQuizBuilderController implements Initializable {

    @FXML private Label courseContextLabel;
    @FXML private TextField quizTitleField;
    @FXML private TextArea quizDescriptionField;
    @FXML private Spinner<Integer> timeLimitSpinner;
    @FXML private Spinner<Integer> passingScoreSpinner;
    @FXML private Label errorLabel;
    @FXML private VBox questionsContainer;
    @FXML private Label noQuestionsLabel;

    private CourseRowDto targetCourse;
    private dto.lms.TeacherAssignmentRowDto targetAssignment;
    private final ClassDeliveryService cdSvc = new ClassDeliveryService();
    private final ContenuService contenuSvc = new ContenuService();
    private final ServiceQuiz quizSvc = new ServiceQuiz();
    private final ServiceQuestion questionSvc = new ServiceQuestion();
    private final ServiceChoice choiceSvc = new ServiceChoice();

    private int questionCounter = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        timeLimitSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 180, 20));
        passingScoreSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 60));
    }

    public void setContext(CourseRowDto course, dto.lms.TeacherAssignmentRowDto assignment) {
        this.targetCourse = course;
        this.targetAssignment = assignment;
        courseContextLabel.setText("Course: " + course.getTitle());
    }

    @FXML
    private void onAddQuestion() {
        noQuestionsLabel.getParent().setVisible(false);
        noQuestionsLabel.getParent().setManaged(false);

        questionCounter++;
        VBox questionBox = createQuestionEditor(questionCounter);
        questionsContainer.getChildren().add(questionBox);
    }

    private VBox createQuestionEditor(int index) {
        VBox qBox = new VBox(12);
        qBox.getStyleClass().add("lms-card");
        qBox.setPadding(new Insets(16));

        HBox header = new HBox(12);
        Label qLabel = new Label("Question " + index);
        qLabel.setStyle("-fx-font-weight: bold;");
        
        ComboBox<String> typeBox = new ComboBox<>(FXCollections.observableArrayList("MCQ", "TEXT"));
        typeBox.setValue("MCQ");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button removeBtn = new Button("Delete");
        removeBtn.getStyleClass().add("danger-button");
        removeBtn.setOnAction(e -> questionsContainer.getChildren().remove(qBox));
        
        header.getChildren().addAll(qLabel, new Label("Type:"), typeBox, spacer, removeBtn);

        TextField questionTextField = new TextField();
        questionTextField.setPromptText("Enter your question here");
        questionTextField.getStyleClass().add("lms-input");
        questionTextField.setId("qText"); // For data extraction

        TextField pointsField = new TextField("1");
        pointsField.setPromptText("Points");
        pointsField.setPrefWidth(80);
        pointsField.setId("qPoints");
        
        TextField explanationField = new TextField();
        explanationField.setPromptText("Explanation (Optional)");
        explanationField.getStyleClass().add("lms-input");
        explanationField.setId("qExplanation");

        VBox choicesContainer = new VBox(8);
        choicesContainer.setId("choicesContainer");
        // Create a shared ToggleGroup for correct-choice RadioButtons
        ToggleGroup correctGroup = new ToggleGroup();
        choicesContainer.setUserData(correctGroup);
        
        Button addChoiceBtn = new Button("+ Add Choice");
        addChoiceBtn.getStyleClass().add("ghost-button");
        addChoiceBtn.setOnAction(e -> addChoiceToContainer(choicesContainer));

        Runnable updateTypeUI = () -> {
            if ("MCQ".equals(typeBox.getValue())) {
                choicesContainer.setVisible(true);
                choicesContainer.setManaged(true);
                addChoiceBtn.setVisible(true);
                addChoiceBtn.setManaged(true);
                if (choicesContainer.getChildren().isEmpty()) {
                    addChoiceToContainer(choicesContainer);
                    addChoiceToContainer(choicesContainer);
                }
            } else {
                choicesContainer.setVisible(false);
                choicesContainer.setManaged(false);
                addChoiceBtn.setVisible(false);
                addChoiceBtn.setManaged(false);
            }
        };

        typeBox.valueProperty().addListener((obs, old, newVal) -> updateTypeUI.run());
        updateTypeUI.run();

        qBox.getChildren().addAll(
            header,
            new Label("Question Text:"), questionTextField,
            new HBox(12, new Label("Points:"), pointsField),
            new Label("Explanation:"), explanationField,
            choicesContainer,
            addChoiceBtn
        );

        return qBox;
    }

    private void addChoiceToContainer(VBox choicesContainer) {
        HBox choiceBox = new HBox(8);
        choiceBox.setStyle("-fx-alignment: center-left;");

        RadioButton correctRadio = new RadioButton();
        correctRadio.setId("cCorrect");
        // Join the shared ToggleGroup so only one choice can be correct
        if (choicesContainer.getUserData() instanceof ToggleGroup tg) {
            correctRadio.setToggleGroup(tg);
        }

        TextField choiceTextField = new TextField();
        choiceTextField.setPromptText("Choice text");
        choiceTextField.getStyleClass().add("lms-input");
        choiceTextField.setId("cText");
        HBox.setHgrow(choiceTextField, Priority.ALWAYS);

        Button removeBtn = new Button("x");
        removeBtn.getStyleClass().add("ghost-button");
        removeBtn.setOnAction(e -> choicesContainer.getChildren().remove(choiceBox));

        choiceBox.getChildren().addAll(correctRadio, choiceTextField, removeBtn);
        choicesContainer.getChildren().add(choiceBox);
    }

    @FXML
    private void onCancel() {
        AppNavigator.showTeacherWorkspace(targetAssignment);
    }

    @FXML
    private void onSaveQuiz() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        String title = quizTitleField.getText();
        if (title == null || title.isBlank()) {
            showError("Quiz Title is required.");
            return;
        }

        if (questionsContainer.getChildren().isEmpty()) {
            showError("You must add at least one question.");
            return;
        }

        try {
            // 1. Create Contenu
            Contenu contenu = contenuSvc.createContenu(
                title, "QUIZ", true, null, null, null, null
            );

            // 2. Link Contenu to ClasseCourse
            cdSvc.addContenuToClasseCourse(targetCourse.getClasseCourseId(), contenu.getId());

            // 3. Create Quiz
            Quiz quiz = new Quiz();
            quiz.setContenu(contenu);
            quiz.setTitle(title);
            quiz.setDescription(quizDescriptionField.getText());
            quiz.setTimeLimit(timeLimitSpinner.getValue());
            quiz.setPassingScore(passingScoreSpinner.getValue());
            quiz.setShuffleQuestions((byte) 0);
            quiz.setShuffleChoices((byte) 0);
            quiz.setShowCorrectAnswers((byte) 1);
            quizSvc.add(quiz);

            Quiz savedQuiz = quizSvc.getALL().stream()
                .filter(q -> q.getContenu() != null && q.getContenu().getId() == contenu.getId())
                .findFirst().orElseThrow(() -> new Exception("Failed to retrieve saved quiz."));

            // 4. Save Questions and Choices
            // NOTE: We traverse children by structure instead of using lookup()
            // because lookup("#id") searches the entire scene graph and returns
            // the wrong node when multiple question editors share the same IDs.
            int qPosition = 1;
            for (Node qNode : questionsContainer.getChildren()) {
                VBox qBox = (VBox) qNode;

                // qBox children layout (from createQuestionEditor):
                //   0: header HBox  (contains: Label, Label, ComboBox, Region, Button)
                //   1: Label "Question Text:"
                //   2: TextField (question text)       — was #qText
                //   3: HBox (contains: Label, TextField) — points field is child[1] — was #qPoints
                //   4: Label "Explanation:"
                //   5: TextField (explanation)          — was #qExplanation
                //   6: VBox (choicesContainer)          — was #choicesContainer
                //   7: Button "+ Add Choice"

                HBox header = (HBox) qBox.getChildren().get(0);
                String qType = ((ComboBox<String>) header.getChildren().get(2)).getValue();

                TextField questionTextField = (TextField) qBox.getChildren().get(2);
                String qText = questionTextField.getText();

                HBox pointsRow = (HBox) qBox.getChildren().get(3);
                TextField pointsField = (TextField) pointsRow.getChildren().get(1);
                String qPointsStr = pointsField.getText();

                TextField explanationField = (TextField) qBox.getChildren().get(5);
                String qExplanation = explanationField.getText();

                if (qText == null || qText.isBlank()) throw new Exception("Question text cannot be blank.");
                int points = 1;
                try { points = Integer.parseInt(qPointsStr); } catch (Exception ignored) {}

                Question question = new Question();
                question.setQuiz(savedQuiz);
                question.setType(qType);
                question.setQuestionText(qText);
                question.setPoints(points);
                final int currentQPosition = qPosition;
                question.setPosition(currentQPosition);
                qPosition++;
                question.setExplanation(qExplanation);
                questionSvc.add(question);

                Question savedQuestion = questionSvc.getALL().stream()
                    .filter(q -> q.getQuiz() != null && q.getQuiz().getId() == savedQuiz.getId() && q.getPosition() == currentQPosition)
                    .findFirst().orElseThrow(() -> new Exception("Failed to save question."));

                if ("MCQ".equals(qType)) {
                    VBox choicesContainer = (VBox) qBox.getChildren().get(6);
                    int cPosition = 1;
                    boolean hasCorrect = false;
                    for (Node cNode : choicesContainer.getChildren()) {
                        HBox cBox = (HBox) cNode;
                        // Choice HBox children: RadioButton, TextField, Button
                        boolean isCorrect = ((RadioButton) cBox.getChildren().get(0)).isSelected();
                        String cText = ((TextField) cBox.getChildren().get(1)).getText();
                        if (cText == null || cText.isBlank()) throw new Exception("Choice text cannot be blank.");

                        Choice choice = new Choice();
                        choice.setQuestion(savedQuestion);
                        choice.setChoiceText(cText);
                        choice.setIsCorrect(isCorrect ? (byte) 1 : (byte) 0);
                        choice.setPosition(cPosition++);
                        choiceSvc.add(choice);
                        
                        if (isCorrect) hasCorrect = true;
                    }
                    if (!hasCorrect) {
                        throw new Exception("You must select at least one correct choice for multiple choice questions.");
                    }
                }
            }

            // Success, return to workspace
            AppNavigator.showTeacherWorkspace(targetAssignment);

        } catch (Exception e) {
            showError("Error saving quiz: " + e.getMessage());
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
