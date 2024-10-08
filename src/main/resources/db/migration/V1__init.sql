CREATE TABLE IF NOT EXISTS users_data
(
    chat_id             INT  GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY NOT NULL,
    current_language    VARCHAR(255),
    current_step_number INT,
    first_name          VARCHAR(255),
    last_name           VARCHAR(255),
    last_phrase_id      INT,
    registered_at       TIMESTAMP(6),
    user_name           VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS translation
(
    id                  INT  GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY NOT NULL,
    phrase_eng          VARCHAR(255),
    phrase_ru           VARCHAR(255),
    priority            INT,
    step_number         INT,
    user_id             INT REFERENCES users_data(chat_id)
);

CREATE SEQUENCE translation_seq START 1 INCREMENT 50;