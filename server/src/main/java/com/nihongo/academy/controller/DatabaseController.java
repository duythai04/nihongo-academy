package com.nihongo.academy.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/database")
public class DatabaseController {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/check")
    public Map<String, String> checkConnection() {

        String database = jdbcTemplate.queryForObject(
            "SELECT DATABASE()",
            String.class
        );

        return Map.of(
            "status", "CONNECTED",
            "database", database
        );
    }
}