package com.drawarena.drawarena_backend.dto;

import java.util.List;

import com.drawarena.drawarena_backend.model.TurnPhase;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DrawerTurnResponse {

    private String currentDrawerId;
    private TurnPhase turnPhase;

    private List<String> wordChoices;

    private String currentWord;

    private long turnStartTime;
    private long turnEndTime;

}
