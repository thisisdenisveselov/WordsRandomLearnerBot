package com.veselov.wrlbot.WordsRandomLearnerBot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "translation")
@Getter
@Setter
public class Translation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "translation_seq")
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
    @JoinColumn(name = "user_id", referencedColumnName = "chat_id")
    private User user;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Translation that = (Translation) o;

        if (!phraseEng.equals(that.phraseEng)) return false;
        if (!phraseRu.equals(that.phraseRu)) return false;
        return user.equals(that.user);
    }

    @Override
    public int hashCode() {
        int result = phraseEng.hashCode();
        result = 31 * result + phraseRu.hashCode();
        result = 31 * result + user.hashCode();
        return result;
    }
}
