package com.drawarena.drawarena_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SidePanelMessage {

    private String playerId;
    private String playerName;
    private String message;
    private boolean correct;
    private long timestamp;

}
