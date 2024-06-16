package com.veselov.wrlbot.WordsRandomLearnerBot.repositories;

import com.veselov.wrlbot.WordsRandomLearnerBot.model.Translation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface TranslationRepository extends JpaRepository<Translation, Integer> {

    @Query(
            value = "SELECT * FROM translation " +
                    "WHERE user_id = :id " +
                    "AND step_number < :stepsAmount AND priority = (" +
                    "SELECT MAX(priority) FROM translation WHERE step_number < :stepsAmount) " +
                    "ORDER BY random() " +
                    "LIMIT 1",
            nativeQuery = true)
    Translation findCustomRandomPhrase(@Param("id")long userId, @Param("stepsAmount") int stepsAmount);
}
