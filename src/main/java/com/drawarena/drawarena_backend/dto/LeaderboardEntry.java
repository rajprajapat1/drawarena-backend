package com.drawarena.drawarena_backend.dto;

import com.drawarena.drawarena_backend.model.Team;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LeaderboardEntry {

    private int rank;
    private String playerId;
    private String playerName;
    private int score;
    private Team team;
}
