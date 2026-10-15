package com.drawarena.drawarena_backend.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Player {

    private String playerId;
    private String name;
    private int score;
    private Team team;
    private String avatar;
}
