package com.drawarena.drawarena_backend.dto;

import java.util.List;

import com.drawarena.drawarena_backend.model.TurnPhase;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DrawerNotification {

    private String currentDrawerId;
    private TurnPhase turnPhase;
    private List<String> wordChoices;
    private String currentWord;
    private long turnStartTime;
    private long turnEndTime;
}
