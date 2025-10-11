package com.example.cms_be.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.cms_be.model.Question;
import com.example.cms_be.model.Answer;
import com.example.cms_be.model.SetupStepQuestion;
import com.example.cms_be.repository.QuestionRepository;

import java.util.List;

@Service
@Transactional
public class QuestionService {

    @Autowired
    private QuestionRepository questionRepository;

    public Question createQuestion(Question question) {
        if (question.getAnswers() != null) {
            for (Answer answer : question.getAnswers()) {
                answer.setQuestion(question);
            }
        }

        if (question.getSetupStepQuestions() != null) {
            for (SetupStepQuestion setupStep : question.getSetupStepQuestions()) {
                setupStep.setQuestion(question);
            }
        }

        return questionRepository.save(question);
    }

    public Question getRandomQuestion() {
        List<Question> questions = questionRepository.findAll();
        if (questions.isEmpty()) {
            throw new RuntimeException("No questions found");
        }
        int randomIndex = (int) (Math.random() * questions.size());
        return questions.get(randomIndex);
    }

}
