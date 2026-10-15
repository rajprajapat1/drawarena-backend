package com.drawarena.drawarena_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DrawingMessage {

    @NotBlank(message = "Room Id cannot be blank")
    private String roomId;

    @NotBlank(message = "Player Id cannot be blank")
    private String playerId;

    private String type;

    private double x;
    private double y;

    private double previousX;
    private double previousY;

    @NotBlank(message = "Color cannot be blank")
    private String color;

    @Positive(message = "Brush size must be greater than zero")
    private double brushSize;

    private String strokeId;
}