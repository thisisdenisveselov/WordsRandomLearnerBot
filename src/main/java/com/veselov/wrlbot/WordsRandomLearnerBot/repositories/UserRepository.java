package com.veselov.wrlbot.WordsRandomLearnerBot.repositories;

import com.veselov.wrlbot.WordsRandomLearnerBot.model.User;
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<User, Long> {
}
