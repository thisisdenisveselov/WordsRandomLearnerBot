package com.veselov.wrlbot.WordsRandomLearnerBot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

@Entity(name = "usersData")
@Data
public class User {
    @Id
    @Column(name = "id")
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
