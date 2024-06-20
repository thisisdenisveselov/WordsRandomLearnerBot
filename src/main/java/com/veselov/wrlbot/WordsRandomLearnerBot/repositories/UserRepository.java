package com.veselov.wrlbot.WordsRandomLearnerBot.repositories;

import com.veselov.wrlbot.WordsRandomLearnerBot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
