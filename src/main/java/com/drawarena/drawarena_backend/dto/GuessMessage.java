package com.drawarena.drawarena_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GuessMessage {

    @NotBlank(message = "Room Id cannot be blank")
    private String roomId;

    @NotBlank(message = "Player Id cannot be blank")
    private String playerId;

    @NotBlank(message = "Guess cannot be blank")
    private String guess;

}
