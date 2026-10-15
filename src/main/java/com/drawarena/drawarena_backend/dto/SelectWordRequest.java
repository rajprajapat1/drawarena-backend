package com.drawarena.drawarena_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SelectWordRequest {

    @NotBlank(message = "Player ID cannot be blank")
    private String playerId;

    @NotBlank(message = "word cannot be blank")
    private String word;
}
