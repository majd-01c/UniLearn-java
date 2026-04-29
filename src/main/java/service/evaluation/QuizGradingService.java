package service.evaluation;

import entities.Answer;
import entities.Assessment;
import entities.Choice;
import entities.Grade;
import entities.Question;
import entities.Quiz;
import entities.User;
import entities.UserAnswer;
import services.ServiceAnswer;
import services.ServiceAssessment;
import services.ServiceChoice;
import services.ServiceGrade;
import services.ServiceQuestion;
import services.ServiceQuiz;
import services.ServiceUser;
import services.ServiceUserAnswer;
import service.lms.ContenuService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service for grading student quiz attempts
 * - Auto-grades multiple choice questions
 * - Handles open-ended question evaluation (via AI if enabled)
 * - Records grades and quiz completion status
 */
public class QuizGradingService {

    private static final Logger logger = LoggerFactory.getLogger(QuizGradingService.class);

    private final ServiceQuiz quizService = new ServiceQuiz();
    private final ServiceQuestion questionService = new ServiceQuestion();
    private final ServiceAnswer answerService = new ServiceAnswer();
    private final ServiceChoice choiceService = new ServiceChoice();
    private final ServiceAssessment assessmentService = new ServiceAssessment();
    private final ServiceGrade gradeService = new ServiceGrade();
    private final ServiceUser userService = new ServiceUser();
    private final ServiceUserAnswer userAnswerService = new ServiceUserAnswer();
    private final AiQuizGenerationService aiQuizGenerationService = new AiQuizGenerationService();
    private final ContenuService contenuService = new ContenuService();

    /**
     * Grade a completed quiz and record the grade
     */
    public QuizGradeResult gradeAndRecordQuiz(Integer quizId, Integer studentId, Integer teacherId) {
        try {
            // Retrieve quiz and student attempt
            Quiz quiz = quizService.getALL().stream()
                    .filter(q -> q.getId() == quizId)
                    .findFirst()
                    .orElse(null);
            
            if (quiz == null) {
                logger.warn("Quiz not found: " + quizId);
                return new QuizGradeResult(false, "Quiz not found", 0, 0, 0.0f);
            }

            UserAnswer userAttempt = getUserAnswerForQuiz(quizId, studentId);
            if (userAttempt == null) {
                logger.warn("User attempt not found for quiz: " + quizId + ", student: " + studentId);
                return new QuizGradeResult(false, "No attempt found", 0, 0, 0.0f);
            }

            // Grade the quiz
            int totalPoints = calculateTotalPoints(quiz);
            int earnedPoints = gradeAllAnswers(userAttempt, quiz);
            float percentage = (totalPoints == 0) ? 0 : (float) earnedPoints / totalPoints * 100;

            // Check if passed
            boolean isPassed = isPassed(earnedPoints, totalPoints, quiz);
            userAttempt.setScore(earnedPoints);
            userAttempt.setTotalPoints(totalPoints);
            userAttempt.setIsPassed((byte) (isPassed ? 1 : 0));
            userAttempt.setCompletedAt(new Timestamp(System.currentTimeMillis()));

            // Update user attempt
            if (userAnswerService != null) {
                userAnswerService.update(userAttempt);
            }

            // Record grade in Assessment if linked
            if (quiz.getContenu() != null && quiz.getContenu().getAssessments() != null 
                    && !quiz.getContenu().getAssessments().isEmpty()) {
                Assessment assessment = quiz.getContenu().getAssessments().iterator().next();
                recordGrade(assessment, studentId, teacherId, percentage);
            }

            logger.info("Quiz graded: ID=" + quizId + ", Student=" + studentId 
                    + ", Score=" + earnedPoints + "/" + totalPoints + ", Percentage=" + percentage);

            return new QuizGradeResult(true, "Quiz graded successfully", earnedPoints, 
                    totalPoints, percentage);

        } catch (Exception e) {
            logger.error("Error grading quiz: " + e.getMessage(), e);
            return new QuizGradeResult(false, "Error grading quiz: " + e.getMessage(), 0, 0, 0.0f);
        }
    }

    /**
     * Grade all answers in a user attempt
     */
    private int gradeAllAnswers(UserAnswer userAttempt, Quiz quiz) {
        int totalEarned = 0;

        // Use targeted query instead of getALL()
        List<Answer> userAnswers = answerService.getByUserAnswerId(userAttempt.getId());

        if (userAnswers.isEmpty()) {
            return 0;
        }

        for (Answer answer : userAnswers) {
            Question question = answer.getQuestion();
            if (question == null) continue;

            // The question from ServiceAnswer is a shallow reference (only ID set).
            // Reload the full question from the database to get type, points, etc.
            final int qId = question.getId();
            Question fullQuestion = questionService.getALL().stream()
                    .filter(q -> q.getId() == qId)
                    .findFirst()
                    .orElse(question);

            int pointsEarned = gradeAnswer(answer, fullQuestion);
            totalEarned += pointsEarned;
        }

        return totalEarned;
    }

    /**
     * Grade a single answer based on question type
     */
    int gradeAnswer(Answer answer, Question question) {
        try {
            String questionType = question.getType();

            if ("MULTIPLE_CHOICE".equals(questionType) || "TRUE_FALSE".equals(questionType) || "MCQ".equals(questionType)) {
                return gradeMultipleChoice(answer, question);
            } else if ("TEXT".equals(questionType)) {
                return gradeTextAnswer(answer, question);
            }

            return 0;

        } catch (Exception e) {
            logger.error("Error grading answer: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Auto-grade multiple choice or true/false questions.
     * IMPORTANT: answer.getChoice() from ServiceAnswer is a shallow reference
     * with only the ID set. We must reload the full Choice from the DB to
     * check isCorrect.
     */
    int gradeMultipleChoice(Answer answer, Question question) {
        if (answer.getChoice() == null) {
            return 0; // No choice selected
        }

        // Reload the full Choice from the database to get isCorrect
        Choice selectedChoice = choiceService.getById(answer.getChoice().getId());
        if (selectedChoice == null) {
            logger.warn("Could not reload choice ID=" + answer.getChoice().getId());
            return 0;
        }

        // Check if selected choice is correct
        if (selectedChoice.getIsCorrect() == 1) {
            // Update the answer record with correctness info
            answer.setIsCorrect((byte) 1);
            answer.setPointsEarned(question.getPoints());
            answerService.update(answer);
            return question.getPoints();
        }

        // Mark as incorrect
        answer.setIsCorrect((byte) 0);
        answer.setPointsEarned(0);
        answerService.update(answer);
        return 0;
    }

    /**
     * Grade text answers - use AI evaluation if enabled
     */
    private int gradeTextAnswer(Answer answer, Question question) {
        String studentAnswer = answer.getTextAnswer();
        if (studentAnswer == null || studentAnswer.trim().isEmpty()) {
            return 0;
        }

        try {
            String evaluationResult = aiQuizGenerationService.evaluateAnswer(
                question.getQuestionText(),
                question.getExplanation(),
                studentAnswer,
                question.getPoints()
            );

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(evaluationResult);
            int score = root.path("score").asInt(0);

            // Update answer with earned points
            answer.setPointsEarned(score);
            answer.setIsCorrect(score > 0 ? (byte) 1 : (byte) 0);
            answerService.update(answer);

            return score;

        } catch (Exception e) {
            logger.error("Error grading text answer: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Calculate total possible points for a quiz
     */
    private int calculateTotalPoints(Quiz quiz) {
        if (quiz == null) {
            return 0;
        }

        int totalPoints = 0;
        List<Question> allQuestions = questionService.getALL();
        if (allQuestions != null) {
            for (Question question : allQuestions) {
                if (question.getQuiz() != null && question.getQuiz().getId() == quiz.getId()) {
                    totalPoints += question.getPoints();
                }
            }
        }

        return totalPoints;
    }

    /**
     * Determine if quiz is passed based on passing score
     */
    private boolean isPassed(int earnedPoints, int totalPoints, Quiz quiz) {
        if (totalPoints == 0 || quiz.getPassingScore() == null) {
            return true; // No passing score set, consider passed
        }

        float percentage = (float) earnedPoints / totalPoints * 100;
        return percentage >= quiz.getPassingScore();
    }

    /**
     * Record grade in Assessment
     */
    private void recordGrade(Assessment assessment, Integer studentId, Integer teacherId, float percentage) {
        try {
            User student = userService.getALL().stream()
                    .filter(u -> u.getId() == studentId)
                    .findFirst()
                    .orElse(null);
            
            User teacher = userService.getALL().stream()
                    .filter(u -> u.getId() == teacherId)
                    .findFirst()
                    .orElse(null);

            if (student == null || teacher == null) {
                logger.warn("Student or teacher not found for grade recording");
                return;
            }

            // Check if grade already exists
            List<Grade> existingGrades = gradeService.getALL().stream()
                    .filter(g -> g.getAssessment().getId() == assessment.getId()
                            && g.getUserByStudentId().getId() == studentId)
                    .toList();

            Grade grade;
            Timestamp now = new Timestamp(System.currentTimeMillis());

            if (!existingGrades.isEmpty()) {
                // Update existing grade
                grade = existingGrades.get(0);
                grade.setScore(percentage);
                grade.setUpdatedAt(now);
                gradeService.update(grade);
            } else {
                // Create new grade
                grade = new Grade(assessment, student, teacher, percentage, now, now);
                gradeService.add(grade);
            }

            logger.info("Grade recorded: Assessment=" + assessment.getId() 
                    + ", Student=" + studentId + ", Score=" + percentage);

        } catch (Exception e) {
            logger.error("Error recording grade: " + e.getMessage(), e);
        }
    }

    /**
     * Get user attempt for a quiz
     */
    private UserAnswer getUserAnswerForQuiz(Integer quizId, Integer studentId) {
        try {
            return userAnswerService.getByUserAndQuiz(studentId, quizId);
        } catch (Exception e) {
            logger.error("Error getting user attempt: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Result object for quiz grading
     */
    public static class QuizGradeResult {
        public final boolean success;
        public final String message;
        public final int earnedPoints;
        public final int totalPoints;
        public final float percentage;

        public QuizGradeResult(boolean success, String message, int earnedPoints, 
                              int totalPoints, float percentage) {
            this.success = success;
            this.message = message;
            this.earnedPoints = earnedPoints;
            this.totalPoints = totalPoints;
            this.percentage = percentage;
        }
    }
}
