package com.drawarena.drawarena_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RoomSessionResponse {

    private String roomId;
    private String playerId;
    private RoomResponse room;
}