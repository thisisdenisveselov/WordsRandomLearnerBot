package com.veselov.wrlbot.WordsRandomLearnerBot.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity(name = "Translation")
@Data
public class Translation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Integer id;

    @Column(name = "phrase_eng")
    private String phraseEng;

    @Column(name = "phrase_ru")
    private String phraseRu;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "step_number")
    private int stepNumber; // the step ordinal number when was the last time this phrase occurred

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;
}
