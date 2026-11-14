

-- 1. JLPT LEVELS

CREATE TABLE jlpt_levels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(5) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

-- 2. VOCABULARIES

CREATE TABLE vocabularies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    source_id VARCHAR(100) NOT NULL,

    level_id BIGINT NOT NULL,

    word VARCHAR(255) NOT NULL,
    reading VARCHAR(255),
    romaji VARCHAR(255),

    jmdict_id BIGINT,

    other_forms JSON,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_vocab_level
        FOREIGN KEY (level_id)
        REFERENCES jlpt_levels(id),

    UNIQUE KEY uk_vocab_source_level (source_id, level_id)
);

-- 3. VOCABULARY MEANINGS

CREATE TABLE vocabulary_meanings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    vocabulary_id BIGINT NOT NULL,

    meaning TEXT NOT NULL,

    CONSTRAINT fk_vocab_meaning
        FOREIGN KEY (vocabulary_id)
        REFERENCES vocabularies(id)
        ON DELETE CASCADE
);

-- 4. VOCABULARY EXAMPLES

CREATE TABLE vocabulary_examples (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    vocabulary_id BIGINT NOT NULL,

    japanese TEXT NOT NULL,
    furigana TEXT,
    english TEXT,
    tatoeba_id BIGINT,

    CONSTRAINT fk_vocab_example
        FOREIGN KEY (vocabulary_id)
        REFERENCES vocabularies(id)
        ON DELETE CASCADE
);

-- 5. VOCABULARY PARTS OF SPEECH

CREATE TABLE vocabulary_pos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    vocabulary_id BIGINT NOT NULL,

    pos_code VARCHAR(100) NOT NULL,

    CONSTRAINT fk_vocab_pos
        FOREIGN KEY (vocabulary_id)
        REFERENCES vocabularies(id)
        ON DELETE CASCADE
);

-- 6. KANJI

CREATE TABLE kanji (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    character_value VARCHAR(10) NOT NULL,

    level_id BIGINT NOT NULL,

    strokes INT,
    grade INT,
    frequency_rank INT,

    radical VARCHAR(50),
    radical_number INT,

    onyomi JSON,
    kunyomi JSON,
    nanori JSON,
    meanings JSON,
    words JSON,

    CONSTRAINT fk_kanji_level
        FOREIGN KEY (level_id)
        REFERENCES jlpt_levels(id),

    UNIQUE KEY uk_kanji_character_level
        (character_value, level_id)
);

-- 7. GRAMMAR

CREATE TABLE grammar_points (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    source_id VARCHAR(100) NOT NULL,

    level_id BIGINT NOT NULL,

    pattern VARCHAR(500) NOT NULL,
    reading VARCHAR(500),
    romaji VARCHAR(500),

    meaning TEXT,
    formation TEXT,
    notes TEXT,

    tags JSON,

    CONSTRAINT fk_grammar_level
        FOREIGN KEY (level_id)
        REFERENCES jlpt_levels(id),

    UNIQUE KEY uk_grammar_source_level
        (source_id, level_id)
);

-- 8. GRAMMAR EXAMPLES

CREATE TABLE grammar_examples (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    grammar_id BIGINT NOT NULL,

    japanese TEXT NOT NULL,
    furigana TEXT,
    english TEXT,

    CONSTRAINT fk_grammar_example
        FOREIGN KEY (grammar_id)
        REFERENCES grammar_points(id)
        ON DELETE CASCADE
);