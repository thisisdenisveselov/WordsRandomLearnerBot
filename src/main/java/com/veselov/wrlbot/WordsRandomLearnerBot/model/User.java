package com.veselov.wrlbot.WordsRandomLearnerBot.model;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

@Entity(name = "usersData")
@Data
public class User {
    @Id
   // @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "registered_at")
    private Timestamp registeredAt;

    @Column(name = "current_language")
    private String currentLanguage;

    @Column(name = "last_phrase_id")
    private Integer lastPhraseId;

    @Column(name = "current_step_number")
    private int currentStepNumber; //current step(request for phrase translation) in the chat

    @OneToMany(mappedBy = "user")
    private List<Translation> translations;

    @Override
    public String toString() {
        return "User{" +
                "chatId=" + chatId +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", userName='" + userName + '\'' +
                ", registeredAt=" + registeredAt +
                ", currentLanguage='" + currentLanguage + '\'' +
                ", lastPhraseId=" + lastPhraseId +
                '}';
    }
}
