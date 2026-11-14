package com.nihongo.academy.controller;

import com.nihongo.academy.service.JlptService;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/kanji")
@CrossOrigin(origins = "http://localhost:5173")
public class KanjiController {

    private final JlptService service;

    public KanjiController(JlptService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> getAll(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {

        return service.paginate(
                "kanji",
                level,
                search,
                page,
                size
        );
    }

    @GetMapping("/{id}")
    public Map<String, Object> getById(
            @PathVariable Long id
    ) {

        return service.getKanji(id);
    }
}