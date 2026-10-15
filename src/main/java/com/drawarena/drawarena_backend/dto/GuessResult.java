package com.drawarena.drawarena_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GuessResult {

    private String playerId;
    private String guess;
    private boolean correct;

}
