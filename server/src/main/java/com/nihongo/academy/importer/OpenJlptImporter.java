package com.nihongo.academy.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(
    name = "openjlpt.import.enabled",
    havingValue = "true"
)
public class OpenJlptImporter implements CommandLineRunner {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${openjlpt.data.path}")
    private String dataPath;

    public OpenJlptImporter(
        JdbcTemplate jdbc,
        TransactionTemplate transaction
    ) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    @Override
    public void run(String... args) throws Exception {

        System.out.println("========== OPENJLPT IMPORT ==========");

        Map<String, Long> levels = loadLevels();

        String[] types = {"vocab", "kanji", "grammar"};
        String[] codes = {"N5", "N4", "N3", "N2", "N1"};

        for (String type : types) {
            for (String code : codes) {

                Path file = Path.of(
                    dataPath, type, code.toLowerCase() + ".json"
                );

                if (!Files.isRegularFile(file)) {
                    throw new IllegalStateException(
                        "Không tìm thấy file: " + file
                    );
                }

                JsonNode records = mapper.readTree(file.toFile());

                if (!records.isArray()) {
                    throw new IllegalStateException(
                        "JSON không phải mảng: " + file
                    );
                }

                Long levelId = levels.get(code);

                if (levelId == null) {
                    throw new IllegalStateException(
                        "Không tìm thấy cấp độ " + code
                    );
                }

                transaction.executeWithoutResult(status -> {

                    for (JsonNode item : records) {

                        String itemLevel = required(item, "level");

                        if (!code.equals(itemLevel)) {
                            throw new IllegalArgumentException(
                                "Sai cấp độ trong " + file
                            );
                        }

                        switch (type) {
                            case "vocab" ->
                                importVocabulary(item, levelId);

                            case "kanji" ->
                                importKanji(item, levelId);

                            case "grammar" ->
                                importGrammar(item, levelId);

                            default ->
                                throw new IllegalArgumentException(type);
                        }
                    }
                });

                System.out.printf(
                    "[OK] %-8s %-2s : %d records%n",
                    type, code, records.size()
                );
            }
        }

        System.out.println("========== IMPORT COMPLETED ==========");
    }

    private Map<String, Long> loadLevels() {

    Map<String, Long> result = new HashMap<>();

    jdbc.query(
        "SELECT id, code FROM jlpt_levels",
        (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            result.put(
                rs.getString("code"),
                rs.getLong("id")
            );
        }
    );

    return result;
}

    private void importVocabulary(JsonNode item, Long levelId) {

        String sourceId = required(item, "id");

        jdbc.update("""
            INSERT INTO vocabularies
            (source_id, level_id, word, reading, romaji,
             jmdict_id, other_forms)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                word = VALUES(word),
                reading = VALUES(reading),
                romaji = VALUES(romaji),
                jmdict_id = VALUES(jmdict_id),
                other_forms = VALUES(other_forms)
            """,
            sourceId,
            levelId,
            required(item, "word"),
            value(item, "reading"),
            value(item, "romaji"),
            longValue(item, "jmdict_id"),
            json(item, "other_forms")
        );

        Long id = jdbc.queryForObject("""
            SELECT id FROM vocabularies
            WHERE source_id = ? AND level_id = ?
            """,
            Long.class, sourceId, levelId
        );

        jdbc.update(
            "DELETE FROM vocabulary_meanings WHERE vocabulary_id = ?",
            id
        );

        jdbc.update(
            "DELETE FROM vocabulary_pos WHERE vocabulary_id = ?",
            id
        );

        jdbc.update(
            "DELETE FROM vocabulary_examples WHERE vocabulary_id = ?",
            id
        );

        for (JsonNode meaning : array(item, "meanings")) {
            jdbc.update("""
                INSERT INTO vocabulary_meanings
                (vocabulary_id, meaning)
                VALUES (?, ?)
                """,
                id, meaning.asText()
            );
        }

        for (JsonNode pos : array(item, "pos")) {
            jdbc.update("""
                INSERT INTO vocabulary_pos
                (vocabulary_id, pos_code)
                VALUES (?, ?)
                """,
                id, pos.asText()
            );
        }

        for (JsonNode example : array(item, "examples")) {
            jdbc.update("""
                INSERT INTO vocabulary_examples
                (vocabulary_id, japanese, furigana,
                 english, tatoeba_id)
                VALUES (?, ?, ?, ?, ?)
                """,
                id,
                required(example, "ja"),
                value(example, "furigana"),
                value(example, "en"),
                longValue(example, "tatoeba_id")
            );
        }
    }

    private void importKanji(JsonNode item, Long levelId) {

        jdbc.update("""
            INSERT INTO kanji
            (character_value, level_id, strokes, grade,
             frequency_rank, radical, radical_number,
             onyomi, kunyomi, nanori, meanings, words)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                strokes = VALUES(strokes),
                grade = VALUES(grade),
                frequency_rank = VALUES(frequency_rank),
                radical = VALUES(radical),
                radical_number = VALUES(radical_number),
                onyomi = VALUES(onyomi),
                kunyomi = VALUES(kunyomi),
                nanori = VALUES(nanori),
                meanings = VALUES(meanings),
                words = VALUES(words)
            """,
            required(item, "character"),
            levelId,
            integer(item, "strokes"),
            integer(item, "grade"),
            integer(item, "freq"),
            value(item, "radical"),
            integer(item, "radical_number"),
            json(item, "onyomi"),
            json(item, "kunyomi"),
            json(item, "nanori"),
            json(item, "meanings"),
            json(item, "words")
        );
    }

    private void importGrammar(JsonNode item, Long levelId) {

        String sourceId = required(item, "id");

        jdbc.update("""
            INSERT INTO grammar_points
            (source_id, level_id, pattern, reading,
             romaji, meaning, formation, notes, tags)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                pattern = VALUES(pattern),
                reading = VALUES(reading),
                romaji = VALUES(romaji),
                meaning = VALUES(meaning),
                formation = VALUES(formation),
                notes = VALUES(notes),
                tags = VALUES(tags)
            """,
            sourceId,
            levelId,
            required(item, "pattern"),
            value(item, "reading"),
            value(item, "romaji"),
            value(item, "meaning"),
            value(item, "formation"),
            value(item, "notes"),
            json(item, "tags")
        );

        Long id = jdbc.queryForObject("""
            SELECT id FROM grammar_points
            WHERE source_id = ? AND level_id = ?
            """,
            Long.class, sourceId, levelId
        );

        jdbc.update(
            "DELETE FROM grammar_examples WHERE grammar_id = ?",
            id
        );

        for (JsonNode example : array(item, "examples")) {
            jdbc.update("""
                INSERT INTO grammar_examples
                (grammar_id, japanese, furigana, english)
                VALUES (?, ?, ?, ?)
                """,
                id,
                required(example, "ja"),
                value(example, "furigana"),
                value(example, "en")
            );
        }
    }

    private String required(JsonNode node, String field) {

        String result = value(node, field);

        if (result == null || result.isBlank()) {
            throw new IllegalArgumentException(
                "Thiếu trường bắt buộc: " + field
            );
        }

        return result;
    }

    private String value(JsonNode node, String field) {

        JsonNode child = node.path(field);

        if (child.isMissingNode() || child.isNull()) {
            return null;
        }

        return child.asText();
    }

    private Integer integer(JsonNode node, String field) {

        JsonNode child = node.path(field);

        if (child.isMissingNode() || child.isNull()) {
            return null;
        }

        return child.asInt();
    }

    private Long longValue(JsonNode node, String field) {

        JsonNode child = node.path(field);

        if (child.isMissingNode() || child.isNull()) {
            return null;
        }

        return child.asLong();
    }

    private String json(JsonNode node, String field) {

        JsonNode child = node.path(field);

        if (child.isMissingNode() || child.isNull()) {
            return null;
        }

        return child.toString();
    }

    private JsonNode array(JsonNode node, String field) {

        JsonNode child = node.path(field);

        if (child.isMissingNode() || child.isNull()) {
            return mapper.createArrayNode();
        }

        if (!child.isArray()) {
            throw new IllegalArgumentException(
                "Trường không phải mảng: " + field
            );
        }

        return child;
    }
}