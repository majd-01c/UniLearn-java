package service.evaluation;

import entities.Answer;
import entities.Choice;
import entities.Question;
import entities.Quiz;
import entities.UserAnswer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class QuizGradingServiceTest {

    private QuizGradingService gradingService;

    @BeforeEach
    void setUp() {
        gradingService = new QuizGradingService();
    }

    @Test
    void testGradeMultipleChoiceCorrect() {
        // Setup entity directly
        Question entityQ = new Question();
        entityQ.setId(1);
        entityQ.setType("MULTIPLE_CHOICE");
        entityQ.setPoints(10);
        
        Choice entityC1 = new Choice();
        entityC1.setId(101);
        entityC1.setIsCorrect((byte) 1);
        
        Choice entityC2 = new Choice();
        entityC2.setId(102);
        entityC2.setIsCorrect((byte) 0);
        
        entityQ.setChoices(new java.util.HashSet<>(Arrays.asList(entityC1, entityC2)));

        Answer answer = new Answer();
        answer.setQuestion(entityQ);
        answer.setChoice(entityC1); // Student selected c1

        int pointsEarned = gradingService.gradeAnswer(answer, entityQ);
        
        assertEquals(10, pointsEarned);
    }

    @Test
    void testGradeMultipleChoiceIncorrect() {
        Question entityQ = new Question();
        entityQ.setId(1);
        entityQ.setType("MULTIPLE_CHOICE");
        entityQ.setPoints(10);
        
        Choice entityC1 = new Choice();
        entityC1.setId(101);
        entityC1.setIsCorrect((byte) 1);
        
        Choice entityC2 = new Choice();
        entityC2.setId(102);
        entityC2.setIsCorrect((byte) 0);
        
        entityQ.setChoices(new java.util.HashSet<>(Arrays.asList(entityC1, entityC2)));

        Answer answer = new Answer();
        answer.setQuestion(entityQ);
        answer.setChoice(entityC2); // Student selected c2 (incorrect)

        int pointsEarned = gradingService.gradeAnswer(answer, entityQ);
        
        assertEquals(0, pointsEarned);
    }
}
