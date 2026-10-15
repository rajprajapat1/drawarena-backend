package com.drawarena.drawarena_backend.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class WordService {

    private final List<String> masterWords = new ArrayList<>();

    @PostConstruct
    public void loadWords() {

        InputStream inputStream = getClass()
                .getClassLoader()
                .getResourceAsStream("words.txt");

        if (inputStream == null) {
            throw new RuntimeException("words.txt not found");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            reader.lines()
                    .map(line -> line.trim())
                    .filter(word -> !word.isBlank())
                    .distinct()
                    .forEach(masterWords::add);

        } catch (Exception exception) {

            throw new RuntimeException("Failed to load words", exception);
        }

        if (masterWords.size() < 3) {
            throw new RuntimeException("At least 3 words are required");
        }
    }

    public List<String> createShuffledWordPool() {

        List<String> shuffledWords = new ArrayList<>(masterWords);

        Collections.shuffle(shuffledWords);

        return shuffledWords;
    }

    public List<String> getNextWords(
            List<String> availableWords,
            List<String> lastWordChoices) {

        if (availableWords == null) {
            throw new RuntimeException(
                    "Word pool is not initialized");
        }

        if (availableWords.size() < 3) {

            List<String> refreshedPool = createShuffledWordPool();

            if (lastWordChoices != null && !lastWordChoices.isEmpty()) {

                refreshedPool.removeIf(lastWordChoices::contains);
            }

            availableWords.clear();

            availableWords.addAll(refreshedPool);
        }

        if (availableWords.size() < 3) {
            throw new RuntimeException("Not enough unique words available");
        }

        List<String> choices = new ArrayList<>(availableWords.subList(
                0,
                3));

        availableWords.subList(0, 3).clear();
        return choices;
    }

}