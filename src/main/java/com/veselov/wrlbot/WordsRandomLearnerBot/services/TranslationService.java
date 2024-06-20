package com.veselov.wrlbot.WordsRandomLearnerBot.services;

import com.veselov.wrlbot.WordsRandomLearnerBot.model.Translation;
import com.veselov.wrlbot.WordsRandomLearnerBot.model.User;
import com.veselov.wrlbot.WordsRandomLearnerBot.repositories.TranslationRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;

//@Component
@Slf4j
@Service
@Transactional(readOnly = true)
public class TranslationService {
    private TranslationRepository translationRepository;
    private static final int STEPS_AMOUNT = 20;  // the number of iterations after which the phrase can be shown again
    private static final int DEFAULT_PRIORITY = 0;
    private static final int DEFAULT_STEP_NUMBER = 0;

    @Autowired
    public TranslationService(TranslationRepository translationRepository) {
        this.translationRepository = translationRepository;
    }

    public Optional<Translation> getById(Integer id) {
        return translationRepository.findById(id);
    }
    public Translation getCustomRandomPhrase(long chatId, User user) {
        return translationRepository.findCustomRandomPhrase(chatId, STEPS_AMOUNT, user.getCurrentStepNumber());
    }

    @Transactional
    public void updateCurrentStepNumber(Translation translation, int currentStepNumber) {
        translation.setStepNumber(currentStepNumber);
        translationRepository.save(translation);
    }

    @Transactional
    public void changePriority(User user, boolean increase) {
        Integer lastPhraseId = user.getLastPhraseId();

        Translation translation = getById(lastPhraseId).orElse(null);

        if (increase)
            translation.setPriority(translation.getPriority() + 1);
        else
            translation.setPriority(translation.getPriority() - 1);

        translationRepository.save(translation);
    }

    @Transactional
    public void copyPhrases(User user, User sourceUser) { // Test it !!!!
        List<Translation> translations = translationRepository.findAllByUser(sourceUser);

        for(Translation translation : translations) {
            translation.setId(null);
            translation.setPriority(DEFAULT_PRIORITY);
            translation.setStepNumber(DEFAULT_STEP_NUMBER);
            translation.setUser(user);
        }

        translationRepository.saveAll(translations);
    }


    @Transactional
    public void updateAll(List<Translation> translations, User user) {
        List<Translation> translationsOld = translationRepository.findAllByUser(user);

        if (!translationsOld.isEmpty()) {
            List<Translation> translationsToDelete = new ArrayList<>(translationsOld);

            translationsToDelete.removeAll(translations);
            translations.removeAll(translationsOld);

            translationRepository.deleteAll(translationsToDelete); //remove records from db that don't exist in new file
        }
        translationRepository.saveAll(translations); //add new records
    }
}