package com.nihongo.academy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class JlptService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public JlptService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // pagination

    public Map<String, Object> paginate(
            String type,
            String level,
            String search,
            int page,
            int size
    ) {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be >= 0"
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "size must be between 1 and 100"
            );
        }

        String table;
        String alias;
        String searchCondition;
        String orderBy;

        switch (type) {

            case "vocabulary" -> {
                table = "vocabularies";
                alias = "v";
                searchCondition = """
                    (v.word LIKE ?
                    OR v.reading LIKE ?
                    OR v.romaji LIKE ?)
                    """;
                orderBy = "v.id";
            }

            case "kanji" -> {
                table = "kanji";
                alias = "k";
                searchCondition = """
                    (k.character_value LIKE ?
                    OR JSON_SEARCH(k.meanings, 'one', ?) IS NOT NULL)
                    """;
                orderBy = "k.id";
            }

            case "grammar" -> {
                table = "grammar_points";
                alias = "g";
                searchCondition = """
                    (g.pattern LIKE ?
                    OR g.reading LIKE ?
                    OR g.romaji LIKE ?
                    OR g.meaning LIKE ?)
                    """;
                orderBy = "g.id";
            }

            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid resource type"
            );
        }

        StringBuilder from = new StringBuilder(
                " FROM " + table + " " + alias +
                " JOIN jlpt_levels l ON " +
                alias + ".level_id = l.id WHERE 1=1"
        );

        List<Object> params = new ArrayList<>();

        if (level != null && !level.isBlank()) {

            String normalized = level.trim().toUpperCase();

            if (!normalized.matches("N[1-5]")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid JLPT level"
                );
            }

            from.append(" AND l.code = ?");
            params.add(normalized);
        }

        if (search != null && !search.isBlank()) {

            from.append(" AND ").append(searchCondition);

            String keyword = "%" + search.trim() + "%";

            int count = switch (type) {
                case "vocabulary" -> 3;
                case "kanji" -> 2;
                case "grammar" -> 4;
                default -> 0;
            };

            for (int i = 0; i < count; i++) {
                params.add(keyword);
            }
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*)" + from,
                Long.class,
                params.toArray()
        );

        long totalElements = total == null ? 0 : total;

        List<Object> queryParams = new ArrayList<>(params);

        queryParams.add(size);
        queryParams.add((long) page * size);

        String sql = """
                SELECT %s.*, l.code AS level
                %s
                ORDER BY %s
                LIMIT ? OFFSET ?
                """.formatted(alias, from, orderBy);

        List<Map<String, Object>> rows =
                jdbc.queryForList(sql, queryParams.toArray());

        rows.forEach(this::parseJsonColumns);

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("content", rows);
        response.put("page", page);
        response.put("size", size);
        response.put("totalElements", totalElements);
        response.put(
                "totalPages",
                (totalElements + size - 1) / size
        );
        response.put(
                "last",
                ((long) page + 1) * size >= totalElements
        );

        return response;
    }

    // vocabulary detail

    public Map<String, Object> getVocabulary(Long id) {

        Map<String, Object> item = findOne("""
                SELECT v.*, l.code AS level
                FROM vocabularies v
                JOIN jlpt_levels l ON v.level_id = l.id
                WHERE v.id = ?
                """, id);

        item.put("meanings", jdbc.queryForList("""
                SELECT meaning
                FROM vocabulary_meanings
                WHERE vocabulary_id = ?
                ORDER BY id
                """, String.class, id));

        item.put("pos", jdbc.queryForList("""
                SELECT pos_code
                FROM vocabulary_pos
                WHERE vocabulary_id = ?
                ORDER BY id
                """, String.class, id));

        item.put("examples", jdbc.queryForList("""
                SELECT
                    id,
                    japanese,
                    furigana,
                    english,
                    tatoeba_id
                FROM vocabulary_examples
                WHERE vocabulary_id = ?
                ORDER BY id
                """, id));

        return item;
    }

    // kanji detail 

    public Map<String, Object> getKanji(Long id) {

        return findOne("""
                SELECT k.*, l.code AS level
                FROM kanji k
                JOIN jlpt_levels l ON k.level_id = l.id
                WHERE k.id = ?
                """, id);
    }

    // grammar detail

    public Map<String, Object> getGrammar(Long id) {

        Map<String, Object> item = findOne("""
                SELECT g.*, l.code AS level
                FROM grammar_points g
                JOIN jlpt_levels l ON g.level_id = l.id
                WHERE g.id = ?
                """, id);

        item.put("examples", jdbc.queryForList("""
                SELECT
                    id,
                    japanese,
                    furigana,
                    english
                FROM grammar_examples
                WHERE grammar_id = ?
                ORDER BY id
                """, id));

        return item;
    }

    // helpers

    private Map<String, Object> findOne(
            String sql,
            Long id
    ) {

        List<Map<String, Object>> results =
                jdbc.queryForList(sql, id);

        if (results.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Resource not found"
            );
        }

        Map<String, Object> item = results.get(0);

        parseJsonColumns(item);

        return item;
    }

    private void parseJsonColumns(Map<String, Object> row) {

        String[] fields = {
                "other_forms",
                "onyomi",
                "kunyomi",
                "nanori",
                "meanings",
                "words",
                "tags"
        };

        for (String field : fields) {

            Object value = row.get(field);

            if (value instanceof String json) {

                try {
                    row.put(
                            field,
                            mapper.readTree(json)
                    );
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "Invalid JSON in column: " + field,
                            e
                    );
                }
            }
        }
    }
}