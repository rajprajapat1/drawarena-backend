package com.drawarena.drawarena_backend.model;

import java.util.ArrayList;
import java.util.List;

import com.drawarena.drawarena_backend.dto.SidePanelMessage;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Room {

    private String roomId;
    private String hostPlayerId;
    private String currentDrawerId;

    private int maxPlayers;
    private GameMode gameMode;
    private GameState gameState;
    private int drawTimeSeconds;
    private List<Player> players = new ArrayList<>();
    private int currentRound;
    private int currentTurnIndex;
    private List<String> eligiblePlayerIds = new ArrayList<>();
    private List<String> correctGuesserIds = new ArrayList<>();

    private TurnPhase turnPhase;
    private List<String> wordChoices = new ArrayList<>();
    private String currentWord;

    private long turnStartTime;
    private long turnEndTime;

    private long countdownEndTime;

    private List<SidePanelMessage> sidePanelMessages = new ArrayList<>();

    private List<String> availableWords = new ArrayList<>();
    private List<String> lastWordChoices = new ArrayList<>();
    private List<Integer> revealedHintIndexes = new ArrayList<>();

    private long wordSelectionEndTime;


}
