package service.lms;

import entities.Answer;
import entities.Choice;
import entities.Classe;
import entities.ClasseContenu;
import entities.Contenu;
import entities.Question;
import entities.Quiz;
import entities.User;
import entities.UserAnswer;
import services.ServiceAnswer;
import services.ServiceChoice;
import services.ServiceContenu;
import services.ServiceQuestion;
import services.ServiceQuiz;
import services.ServiceUser;
import services.ServiceUserAnswer;
import util.RoleGuard;
import service.evaluation.QuizGradingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service for managing student quiz interactions:
 * - List available quizzes for student's classes
 * - Start a quiz (create UserAnswer record)
 * - Save student answers
 * - Submit quiz (finalize and grade)
 * - Get quiz results and performance
 */
public class StudentQuizService {

    private static final Logger logger = LoggerFactory.getLogger(StudentQuizService.class);

    private final ServiceQuiz quizService = new ServiceQuiz();
    private final ServiceQuestion questionService = new ServiceQuestion();
    private final ServiceChoice choiceService = new ServiceChoice();
    private final ServiceAnswer answerService = new ServiceAnswer();
    private final ServiceUser userService = new ServiceUser();
    private final ServiceUserAnswer userAnswerService = new ServiceUserAnswer();
    private final ServiceContenu contenuService = new ServiceContenu();
    private final ClasseService classeService = new ClasseService();
    private final QuizGradingService gradingService = new QuizGradingService();
    private final EnrollmentService enrollmentService = new EnrollmentService();
    private final ClassDeliveryService classDeliveryService = new ClassDeliveryService();

    /**
     * Get all available quizzes for current student's enrolled classes
     */
    public List<QuizInfo> getAvailableQuizzesForStudent(int studentId) {
        try {
            List<QuizInfo> available = new ArrayList<>();
            
            // Get student's active enrollments
            List<dto.lms.StudentClasseRowDto> enrollments = enrollmentService.getActiveEnrollmentsForStudentDto(studentId);
            if (enrollments == null || enrollments.isEmpty()) {
                return available;
            }

            // Find all visible Contenu IDs for these classes
            Set<Integer> visibleContenuIds = new HashSet<>();
            for (dto.lms.StudentClasseRowDto enrollment : enrollments) {
                List<entities.ClasseModule> cmList = classDeliveryService.getModulesForClasse(enrollment.getClasseId());
                for (entities.ClasseModule cm : cmList) {
                    List<entities.ClasseCourse> ccList = classDeliveryService.getVisibleCoursesForClasseModule(cm.getId());
                    for (entities.ClasseCourse cc : ccList) {
                        List<entities.ClasseContenu> cxList = classDeliveryService.getVisibleContenuForClasseCourse(cc.getId());
                        for (entities.ClasseContenu cx : cxList) {
                            if (cx.getContenu() != null && "QUIZ".equalsIgnoreCase(cx.getContenu().getType())) {
                                visibleContenuIds.add(cx.getContenu().getId());
                            }
                        }
                    }
                }
            }

            // Get all quizzes
            List<Quiz> allQuizzes = quizService.getALL();
            if (allQuizzes == null || allQuizzes.isEmpty()) {
                return available;
            }

            // Filter quizzes by visible Contenu IDs
            for (Quiz quiz : allQuizzes) {
                if (quiz.getContenu() != null && visibleContenuIds.contains(quiz.getContenu().getId())) {
                    QuizInfo info = createQuizInfo(quiz, studentId);
                    available.add(info);
                }
            }

            // Sort by most recent first
            available.sort(Comparator.comparing((QuizInfo q) -> q.createdAt, 
                    Comparator.nullsLast(Comparator.reverseOrder())));

            return available;

        } catch (Exception e) {
            logger.error("Error fetching available quizzes: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Get quiz details with questions and choices
     */
    public QuizDetail getQuizDetail(int quizId, int studentId) {
        try {
            Quiz quiz = quizService.getALL().stream()
                    .filter(q -> q.getId() == quizId)
                    .findFirst()
                    .orElse(null);
            
            if (quiz == null) {
                logger.warn("Quiz not found: " + quizId);
                return null;
            }

            // Get user's existing attempt or create new one
            UserAnswer userAnswer = userAnswerService.getByUserAndQuiz(studentId, quizId);
            if (userAnswer == null) {
                // Create new attempt
                userAnswer = createNewAttempt(quizId, studentId);
            }

            // Build quiz detail with all questions
            List<QuestionDetail> questions = new ArrayList<>();
            List<Question> allQuestions = questionService.getALL();
            if (allQuestions != null) {
                for (Question q : allQuestions) {
                    if (q.getQuiz() != null && q.getQuiz().getId() == quizId) {
                        questions.add(createQuestionDetail(q, userAnswer));
                    }
                }

                // Sort by position
                questions.sort(Comparator.comparingInt(qd -> qd.position));
            }

            // Get existing student answers using targeted query
            Set<Answer> studentAnswers = new HashSet<>(answerService.getByUserAnswerId(userAnswer.getId()));

            return new QuizDetail(quizId, quiz.getTitle(), quiz.getDescription(),
                    quiz.getPassingScore(), quiz.getTimeLimit(),
                    quiz.getShowCorrectAnswers() == 1,
                    questions, userAnswer.getId(), studentAnswers);

        } catch (Exception e) {
            logger.error("Error fetching quiz detail: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Save student answer (auto-save)
     */
    public boolean saveAnswer(int userAnswerId, int questionId, String answer, Integer selectedChoiceId) {
        try {
            // Use targeted query instead of filtering getALL()
            UserAnswer userAnswer = userAnswerService.getParFiltreInt(userAnswerId);
            if (userAnswer == null) {
                logger.warn("User attempt not found: " + userAnswerId);
                return false;
            }

            Question question = questionService.getALL().stream()
                    .filter(q -> q.getId() == questionId)
                    .findFirst()
                    .orElse(null);
            
            if (question == null) {
                logger.warn("Question not found: " + questionId);
                return false;
            }

            // Use targeted query to find existing answer
            Answer answerEntity = answerService.getByUserAnswerAndQuestion(userAnswerId, questionId);

            if (answerEntity == null) {
                answerEntity = new Answer();
                answerEntity.setQuestion(question);
                answerEntity.setUserAnswer(userAnswer);
            }

            // Update answer based on question type
            String questionType = question.getType();
            if ("MULTIPLE_CHOICE".equals(questionType) || "TRUE_FALSE".equals(questionType) || "MCQ".equals(questionType)) {
                if (selectedChoiceId != null) {
                    // Use targeted query to load the full choice
                    Choice choice = choiceService.getById(selectedChoiceId);
                    if (choice != null) {
                        answerEntity.setChoice(choice);
                    }
                }
            } else if ("TEXT".equals(questionType)) {
                answerEntity.setTextAnswer(answer);
            }

            // Save answer
            if (answerEntity.getId() == 0) {
                answerService.add(answerEntity);
            } else {
                answerService.update(answerEntity);
            }

            logger.debug("Answer saved: Question=" + questionId + ", UserAttempt=" + userAnswerId);
            return true;

        } catch (Exception e) {
            logger.error("Error saving answer: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Submit a completed quiz for grading
     */
    public SubmissionResult submitQuiz(int userAnswerId, int studentId, int teacherId) {
        try {
            UserAnswer userAnswer = userAnswerService.getParFiltreInt(userAnswerId);
            if (userAnswer == null) {
                logger.warn("User attempt not found: " + userAnswerId);
                return new SubmissionResult(false, "Quiz attempt not found", 0, 0, 0);
            }

            int quizId = userAnswer.getQuiz().getId();

            // Grade the quiz
            QuizGradingService.QuizGradeResult gradeResult = 
                    gradingService.gradeAndRecordQuiz(quizId, studentId, teacherId);

            if (!gradeResult.success) {
                logger.warn("Failed to grade quiz: " + gradeResult.message);
                return new SubmissionResult(false, gradeResult.message, 0, 0, 0);
            }

            // Reload to get updated values
            userAnswer = userAnswerService.getParFiltreInt(userAnswerId);

            logger.info("Quiz submitted and graded: Quiz=" + quizId + ", Student=" + studentId 
                    + ", Score=" + gradeResult.earnedPoints + "/" + gradeResult.totalPoints);

            return new SubmissionResult(true, "Quiz submitted successfully", 
                    gradeResult.earnedPoints, gradeResult.totalPoints, 
                    (int) gradeResult.percentage);

        } catch (Exception e) {
            logger.error("Error submitting quiz: " + e.getMessage(), e);
            return new SubmissionResult(false, "Error submitting quiz", 0, 0, 0);
        }
    }

    /**
     * Get quiz results for a student
     */
    public QuizResultDetail getQuizResultDetail(int userAnswerId) {
        try {
            UserAnswer userAnswer = userAnswerService.getParFiltreInt(userAnswerId);
            if (userAnswer == null) {
                return null;
            }

            // Reload the full quiz to get title/description
            Quiz quiz = userAnswer.getQuiz();
            if (quiz != null && quiz.getTitle() == null) {
                final int qid = quiz.getId();
                quiz = quizService.getALL().stream()
                        .filter(q -> q.getId() == qid)
                        .findFirst().orElse(quiz);
            }
            int earnedPoints = userAnswer.getScore() != null ? userAnswer.getScore() : 0;
            int totalPoints = userAnswer.getTotalPoints() != null ? userAnswer.getTotalPoints() : 0;
            int percentage = (totalPoints == 0) ? 0 : (earnedPoints * 100) / totalPoints;
            boolean passed = userAnswer.getIsPassed() == 1;

            // Use targeted query instead of filtering getALL()
            List<QuestionResultDetail> questionResults = new ArrayList<>();
            List<Answer> userAnswers = answerService.getByUserAnswerId(userAnswer.getId());

            for (Answer answer : userAnswers) {
                Question question = answer.getQuestion();
                if (question != null) {
                    // Reload full question (answer's question is a shallow reference)
                    final int questionId = question.getId();
                    Question fullQuestion = questionService.getALL().stream()
                            .filter(q -> q.getId() == questionId)
                            .findFirst().orElse(question);
                    questionResults.add(createQuestionResultDetail(answer, fullQuestion));
                }
            }

            // Sort by question position
            questionResults.sort(Comparator.comparingInt(qr -> qr.questionId));

            return new QuizResultDetail(quiz.getId(), quiz.getTitle(), quiz.getDescription(),
                    earnedPoints, totalPoints, percentage, passed,
                    userAnswer.getStartedAt(), userAnswer.getCompletedAt(), 
                    userAnswer.getCheatFlag() == 1, questionResults);

        } catch (Exception e) {
            logger.error("Error fetching quiz results: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Flag an attempt as cheated
     */
    public void flagCheat(int userAnswerId) {
        try {
            UserAnswer ua = userAnswerService.getParFiltreInt(userAnswerId);
            if (ua != null && ua.getCheatFlag() == 0) {
                ua.setCheatFlag((byte) 1);
                userAnswerService.update(ua);
                logger.warn("User attempt " + userAnswerId + " flagged as cheated.");
            }
        } catch (Exception e) {
            logger.error("Error flagging cheat: " + e.getMessage(), e);
        }
    }

    // ========== Helper Methods ==========

    private UserAnswer createNewAttempt(int quizId, int studentId) {
        try {
            User student = userService.getALL().stream()
                    .filter(u -> u.getId() == studentId)
                    .findFirst()
                    .orElse(null);
            
            Quiz quiz = quizService.getALL().stream()
                    .filter(q -> q.getId() == quizId)
                    .findFirst()
                    .orElse(null);

            if (student == null || quiz == null) {
                logger.warn("Failed to create attempt: student or quiz not found");
                return null;
            }

            UserAnswer attempt = new UserAnswer();
            attempt.setUser(student);
            attempt.setQuiz(quiz);
            attempt.setStartedAt(new Timestamp(System.currentTimeMillis()));
            attempt.setIsPassed((byte) 0);
            attempt.setAnswers(new HashSet<>());

            userAnswerService.add(attempt);

            // Reload to get the auto-generated ID
            UserAnswer persisted = userAnswerService.getByUserAndQuiz(studentId, quizId);
            return persisted != null ? persisted : attempt;
        } catch (Exception e) {
            logger.error("Error creating attempt: " + e.getMessage(), e);
            return null;
        }
    }

    private QuizInfo createQuizInfo(Quiz quiz, int studentId) {
        List<Question> allQuestions = questionService.getALL();
        int totalQuestions = 0;
        if (allQuestions != null) {
            for (Question q : allQuestions) {
                if (q.getQuiz() != null && q.getQuiz().getId() == quiz.getId()) {
                    totalQuestions++;
                }
            }
        }
        Integer passingScore = quiz.getPassingScore() != null ? quiz.getPassingScore() : 60;

        // Check if student already took this quiz
        UserAnswer attempt = userAnswerService.getByUserAndQuiz(studentId, quiz.getId());
        boolean alreadyTaken = (attempt != null && attempt.getCompletedAt() != null);
        Integer lastScore = null;
        if (alreadyTaken && attempt.getScore() != null) {
            int totalPoints = attempt.getTotalPoints() != null ? attempt.getTotalPoints() : 100;
            lastScore = (attempt.getScore() * 100) / Math.max(totalPoints, 1);
        }

        return new QuizInfo(quiz.getId(), quiz.getTitle(), quiz.getDescription(),
                totalQuestions, passingScore, quiz.getTimeLimit(),
                alreadyTaken, lastScore, null);
    }

    private QuestionDetail createQuestionDetail(Question question, UserAnswer userAnswer) {
        List<ChoiceDetail> choices = new ArrayList<>();

        if ("MULTIPLE_CHOICE".equals(question.getType()) || "TRUE_FALSE".equals(question.getType()) || "MCQ".equals(question.getType())) {
            // Use targeted query instead of filtering all choices
            List<Choice> questionChoices = choiceService.getByQuestionId(question.getId());
            for (Choice choice : questionChoices) {
                choices.add(new ChoiceDetail(choice.getId(), choice.getChoiceText(),
                        choice.getPosition()));
            }
        }

        return new QuestionDetail(question.getId(), question.getQuestionText(),
                question.getType(), question.getPoints(), question.getPosition(),
                question.getExplanation(), choices);
    }

    private QuestionResultDetail createQuestionResultDetail(Answer answer, Question question) {
        String studentAnswer = "";
        String selectedChoiceText = "";

        if (answer.getChoice() != null) {
            // Reload the full choice from DB to get choiceText
            Choice fullChoice = choiceService.getById(answer.getChoice().getId());
            if (fullChoice != null) {
                selectedChoiceText = fullChoice.getChoiceText();
            }
            studentAnswer = selectedChoiceText;
        } else if (answer.getTextAnswer() != null) {
            studentAnswer = answer.getTextAnswer();
        }

        // Find correct answer using targeted query
        // DO NOT use question.getChoices() — it's a lazy-loaded Hibernate proxy
        // that fails outside an active session. Use ServiceChoice instead.
        String correctAnswer = "";
        boolean isCorrect = false;
        int pointsEarned = answer.getPointsEarned() != null ? answer.getPointsEarned() : 0;

        List<Choice> questionChoices = choiceService.getByQuestionId(question.getId());
        for (Choice choice : questionChoices) {
            if (choice.getIsCorrect() == 1) {
                correctAnswer = choice.getChoiceText();
                break;
            }
        }

        // Use the correctness determined during grading
        isCorrect = answer.getIsCorrect() == 1;

        return new QuestionResultDetail(question.getId(), question.getQuestionText(),
                question.getType(), studentAnswer, correctAnswer, question.getExplanation(),
                isCorrect, pointsEarned, question.getPoints());
    }

    // ========== Data Transfer Objects ==========

    public static class QuizInfo {
        public final int id;
        public final String title;
        public final String description;
        public final int questionCount;
        public final int passingScore;
        public final Integer timeLimit;
        public final boolean alreadyTaken;
        public final Integer lastScore;
        public final Timestamp createdAt;

        public QuizInfo(int id, String title, String description, int questionCount,
                       int passingScore, Integer timeLimit, boolean alreadyTaken,
                       Integer lastScore, Timestamp createdAt) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.questionCount = questionCount;
            this.passingScore = passingScore;
            this.timeLimit = timeLimit;
            this.alreadyTaken = alreadyTaken;
            this.lastScore = lastScore;
            this.createdAt = createdAt;
        }
    }

    public static class QuizDetail {
        public final int id;
        public final String title;
        public final String description;
        public final Integer passingScore;
        public final Integer timeLimit;
        public final boolean showCorrectAnswers;
        public final List<QuestionDetail> questions;
        public final int userAnswerId;
        public final Set<Answer> existingAnswers;

        public QuizDetail(int id, String title, String description, Integer passingScore,
                         Integer timeLimit, boolean showCorrectAnswers,
                         List<QuestionDetail> questions, int userAnswerId,
                         Set<Answer> existingAnswers) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.passingScore = passingScore;
            this.timeLimit = timeLimit;
            this.showCorrectAnswers = showCorrectAnswers;
            this.questions = questions;
            this.userAnswerId = userAnswerId;
            this.existingAnswers = existingAnswers;
        }
    }

    public static class QuestionDetail {
        public final int id;
        public final String text;
        public final String type;
        public final int points;
        public final int position;
        public final String explanation;
        public final List<ChoiceDetail> choices;

        public QuestionDetail(int id, String text, String type, int points, int position,
                             String explanation, List<ChoiceDetail> choices) {
            this.id = id;
            this.text = text;
            this.type = type;
            this.points = points;
            this.position = position;
            this.explanation = explanation;
            this.choices = choices;
        }
    }

    public static class ChoiceDetail {
        public final int id;
        public final String text;
        public final int position;

        public ChoiceDetail(int id, String text, int position) {
            this.id = id;
            this.text = text;
            this.position = position;
        }
    }

    public static class SubmissionResult {
        public final boolean success;
        public final String message;
        public final int earnedPoints;
        public final int totalPoints;
        public final int percentage;

        public SubmissionResult(boolean success, String message, int earnedPoints,
                               int totalPoints, int percentage) {
            this.success = success;
            this.message = message;
            this.earnedPoints = earnedPoints;
            this.totalPoints = totalPoints;
            this.percentage = percentage;
        }
    }

    public static class QuizResultDetail {
        public final int quizId;
        public final String title;
        public final String description;
        public final int earnedPoints;
        public final int totalPoints;
        public final int percentage;
        public final boolean passed;
        public final Timestamp startedAt;
        public final Timestamp completedAt;
        public final boolean cheated;
        public final List<QuestionResultDetail> questionResults;

        public QuizResultDetail(int quizId, String title, String description,
                               int earnedPoints, int totalPoints, int percentage,
                               boolean passed, Timestamp startedAt, Timestamp completedAt,
                               boolean cheated, List<QuestionResultDetail> questionResults) {
            this.quizId = quizId;
            this.title = title;
            this.description = description;
            this.earnedPoints = earnedPoints;
            this.totalPoints = totalPoints;
            this.percentage = percentage;
            this.passed = passed;
            this.startedAt = startedAt;
            this.completedAt = completedAt;
            this.cheated = cheated;
            this.questionResults = questionResults;
        }
    }

    public static class QuestionResultDetail {
        public final int questionId;
        public final String question;
        public final String type;
        public final String studentAnswer;
        public final String correctAnswer;
        public final String explanation;
        public final boolean isCorrect;
        public final int pointsEarned;
        public final int maxPoints;

        public QuestionResultDetail(int questionId, String question, String type,
                                   String studentAnswer, String correctAnswer,
                                   String explanation, boolean isCorrect,
                                   int pointsEarned, int maxPoints) {
            this.questionId = questionId;
            this.question = question;
            this.type = type;
            this.studentAnswer = studentAnswer;
            this.correctAnswer = correctAnswer;
            this.explanation = explanation;
            this.isCorrect = isCorrect;
            this.pointsEarned = pointsEarned;
            this.maxPoints = maxPoints;
        }
    }
}
