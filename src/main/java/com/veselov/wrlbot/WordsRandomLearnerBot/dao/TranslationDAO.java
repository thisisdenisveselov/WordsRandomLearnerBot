package com.veselov.wrlbot.WordsRandomLearnerBot.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veselov.wrlbot.WordsRandomLearnerBot.model.Translation;
import com.veselov.wrlbot.WordsRandomLearnerBot.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class TranslationDAO {

    private final JdbcTemplate jdbcTemplate;

    RowMapper<Translation> rowMapper = (rs, rowNum) -> {
        Translation translation = new Translation();
        translation.setId(rs.getInt("id"));
        translation.setPhraseEng(rs.getString("phrase_eng"));
        translation.setPhraseRu(rs.getString("phrase_ru"));
        translation.setPriority(rs.getInt("priority"));
        translation.setStepNumber(rs.getInt("step_number"));

        return translation;
    };

    public TranslationDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // get random phrase among user's phrases, that haven't been shown for "stepsAmount" iterations and has the highest priority
    public Translation getTranslation(long userId, int stepsAmount) {
        return jdbcTemplate.query("SELECT * FROM translation " +
                        "WHERE user_id = ? " +
                        "AND step_number < ? AND priority = (" +
                        "SELECT MAX(priority) FROM translation WHERE step_number < ?" +
                        ") ORDER BY random() " +
                        "LIMIT 1", new Object[]{userId, stepsAmount, stepsAmount}, rowMapper).stream().findAny().orElse(null);
    }

}
