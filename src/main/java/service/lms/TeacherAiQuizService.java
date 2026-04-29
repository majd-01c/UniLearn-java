package service.lms;

import entities.Choice;
import entities.Contenu;
import entities.Question;
import entities.Quiz;
import service.evaluation.AiQuizGenerationService;
import services.ServiceChoice;
import services.ServiceQuestion;
import services.ServiceQuiz;
import util.RoleGuard;

import java.io.File;

public class TeacherAiQuizService {

    private final ContenuService contenuService = new ContenuService();
    private final FileUploadService fileUploadService = new FileUploadService();
    private final AiQuizGenerationService aiQuizGenerationService = new AiQuizGenerationService();
    private final ServiceQuiz quizService = new ServiceQuiz();
    private final ServiceQuestion questionService = new ServiceQuestion();
    private final ServiceChoice choiceService = new ServiceChoice();

    public Contenu createQuizContenuFromFile(String requestedTitle,
                                             File sourceFile,
                                             int questionCount,
                                             Integer passingScore,
                                             Integer timeLimitMinutes) {
        RoleGuard.requireCurrentTeacher();

        if (sourceFile == null || !sourceFile.exists()) {
            throw new IllegalArgumentException("Please choose a valid source file.");
        }

        int normalizedQuestionCount = Math.max(3, Math.min(questionCount, 25));
        int normalizedPassingScore = passingScore == null ? 60 : Math.max(1, Math.min(passingScore, 100));
        int normalizedTimeLimit = timeLimitMinutes == null ? 20 : Math.max(1, Math.min(timeLimitMinutes, 180));

        AiQuizGenerationService.QuizDraft draft = aiQuizGenerationService.generateQuizFromFile(sourceFile, normalizedQuestionCount);

        String storedFileName;
        try {
            storedFileName = fileUploadService.saveFile(sourceFile, sourceFile.getName());
        } catch (Exception e) {
            throw new IllegalStateException("File upload failed: " + e.getMessage(), e);
        }

        String title = requestedTitle == null || requestedTitle.isBlank() ? draft.getTitle() : requestedTitle.trim();
        String description = draft.getDescription();

        Contenu contenu = contenuService.createContenu(
                title,
                "QUIZ",
                true,
                storedFileName,
                guessMimeType(sourceFile),
                (int) sourceFile.length(),
                null
        );

        createQuizAndQuestions(
                contenu.getId(),
                title,
                description,
                draft,
                normalizedPassingScore,
                normalizedTimeLimit
        );

        return contenu;
    }

    private void createQuizAndQuestions(Integer contenuId,
                                        String title,
                                        String description,
                                        AiQuizGenerationService.QuizDraft draft,
                                        int passingScore,
                                        int timeLimitMinutes) {
        Quiz quiz = new Quiz();
        quiz.setContenu(contenuRef(contenuId));
        quiz.setTitle(title);
        quiz.setDescription(description);
        quiz.setPassingScore(passingScore);
        quiz.setTimeLimit(timeLimitMinutes);
        quiz.setShuffleQuestions((byte) 1);
        quiz.setShuffleChoices((byte) 1);
        quiz.setShowCorrectAnswers((byte) 1);
        quizService.add(quiz);

        Integer quizId = quizService.getALL().stream()
                .filter(q -> q.getContenu() != null && q.getContenu().getId() == contenuId)
                .map(Quiz::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Quiz was not persisted."));

        int position = 1;
        for (AiQuizGenerationService.QuestionDraft questionDraft : draft.getQuestions()) {
            final int currentPosition = position;
            Question question = new Question();
            question.setQuiz(quizRef(quizId));
            question.setType("MCQ");
            question.setQuestionText(questionDraft.getQuestionText());
            question.setPoints(1);
            question.setPosition(currentPosition);
            question.setExplanation(questionDraft.getExplanation());
            questionService.add(question);

            Integer questionId = questionService.getALL().stream()
                    .filter(q -> q.getQuiz() != null && q.getQuiz().getId() == quizId)
                    .filter(q -> q.getPosition() == currentPosition)
                    .map(Question::getId)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Question was not persisted."));

            int choicePosition = 1;
            for (AiQuizGenerationService.ChoiceDraft choiceDraft : questionDraft.getChoices()) {
                Choice choice = new Choice();
                choice.setQuestion(questionRef(questionId));
                choice.setChoiceText(choiceDraft.getText());
                choice.setIsCorrect(choiceDraft.isCorrect() ? (byte) 1 : (byte) 0);
                choice.setPosition(choicePosition++);
                choiceService.add(choice);
            }
            position++;
        }
    }

    private Contenu contenuRef(int id) {
        Contenu contenu = new Contenu();
        contenu.setId(id);
        return contenu;
    }

    private Quiz quizRef(int id) {
        Quiz quiz = new Quiz();
        quiz.setId(id);
        return quiz;
    }

    private Question questionRef(int id) {
        Question question = new Question();
        question.setId(id);
        return question;
    }

    private String guessMimeType(File file) {
        try {
            String mime = java.nio.file.Files.probeContentType(file.toPath());
            if (mime != null && !mime.isBlank()) {
                return mime;
            }
        } catch (Exception ignored) {
            // Fallback for portability.
        }
        return "application/octet-stream";
    }
}
