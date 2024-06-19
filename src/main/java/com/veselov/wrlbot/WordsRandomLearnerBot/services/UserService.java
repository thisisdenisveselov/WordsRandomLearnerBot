package com.veselov.wrlbot.WordsRandomLearnerBot.services;

import com.veselov.wrlbot.WordsRandomLearnerBot.model.*;
import com.veselov.wrlbot.WordsRandomLearnerBot.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.sql.Timestamp;
import java.util.*;

//@Component
@Slf4j
@Service
public class UserService {

    private UserRepository userRepository;
    private static final int DEFAULT_STEP_NUMBER = 0;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> getUserByChatId(long chatId) {
        return userRepository.findById(chatId);
    }

    public void saveUser(Message msg) {
        var chatId = msg.getChatId();
        var chat = msg.getChat();

        User user = new User();

        user.setChatId(chatId);
        user.setFirstName(chat.getFirstName());
        user.setLastName(chat.getLastName());
        user.setUserName(chat.getUserName());
        user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
        user.setCurrentStepNumber(DEFAULT_STEP_NUMBER);

        userRepository.save(user);
        log.info("User saved: " + user);
    }

    public void updateUser(User user, int currentStepNumber, Integer id, String currentLanguage, int translationId) {
        user.setCurrentStepNumber(currentStepNumber);
        user.setLastPhraseId(translationId);
        user.setCurrentLanguage(currentLanguage);
        userRepository.save(user);
    }
}