package com.drawarena.drawarena_backend.dto;

import com.drawarena.drawarena_backend.model.GameMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRoomRequest {

    @NotBlank(message = "Player name cannot be blank")
    private String name;

    @Min(value = 2, message = "Minimum 2 players are required")
    @Max(value = 15, message = "Maximum 15 players are allowed")
    private int maxPlayers;

    private GameMode gameMode;
    private int drawTimeSeconds;
    private String avatar;
}
