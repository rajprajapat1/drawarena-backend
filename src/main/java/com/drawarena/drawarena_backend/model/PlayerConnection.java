package com.drawarena.drawarena_backend.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PlayerConnection {

    private String sessionId;
    private String roomId;
    private String playerId;
    private String principalName;
}
