package com.drawarena.drawarena_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JoinRoomRequest {

    @NotBlank(message = "Player name cannot be null")
    private String name;
    private String avatar;
}
