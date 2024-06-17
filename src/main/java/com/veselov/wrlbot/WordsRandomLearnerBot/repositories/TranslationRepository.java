package com.veselov.wrlbot.WordsRandomLearnerBot.repositories;

import com.veselov.wrlbot.WordsRandomLearnerBot.model.Translation;
import com.veselov.wrlbot.WordsRandomLearnerBot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TranslationRepository extends JpaRepository<Translation, Integer> {

    List<Translation> findAllByUser(User user);
    @Query(
            value = "SELECT * FROM translation " +
                    "WHERE user_id = :id " +
                    "AND (step_number < (:currentStepNumber - :stepsAmount) OR step_number = 0 )" +
                    "AND priority = (" +
                    "SELECT MAX(priority) FROM translation WHERE step_number < (:currentStepNumber - :stepsAmount) OR step_number = 0) " +
                    "ORDER BY random() " +
                    "LIMIT 1",
            nativeQuery = true)
    Translation findCustomRandomPhrase(@Param("id")long userId, @Param("stepsAmount") int stepsAmount, @Param("currentStepNumber") int currentStepNumber);
}
