package com.drawarena.drawarena_backend.dto;

import java.util.List;

import com.drawarena.drawarena_backend.model.GameMode;
import com.drawarena.drawarena_backend.model.GameState;
import com.drawarena.drawarena_backend.model.Player;
import com.drawarena.drawarena_backend.model.Team;
import com.drawarena.drawarena_backend.model.TurnPhase;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoomResponse {

    private String roomId;
    private String hostPlayerId;

    private int maxPlayers;
    private GameMode gameMode;
    private GameState gameState;

    private int drawTimeSeconds;

    private List<Player> players;

    private int currentRound;

    private long countdownEndTime;

    private int currentTurnIndex;
    private String currentDrawerId;

    private TurnPhase turnPhase;

    private String wordPattern;

    private long turnStartTime;
    private long turnEndTime;

    private int redTeamScore;
    private int blueTeamScore;
    private Team winningTeam;

    private List<LeaderboardEntry> leaderboard;

    private int correctGuessCount;
    private int eligibleGuesserCount;

    private List<String> correctGuesserIds;
}
