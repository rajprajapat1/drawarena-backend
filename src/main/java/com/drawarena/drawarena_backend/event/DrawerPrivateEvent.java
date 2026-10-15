package com.drawarena.drawarena_backend.event;

import com.drawarena.drawarena_backend.dto.DrawerNotification;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DrawerPrivateEvent {

    private String roomId;
    private String playerId;
    private DrawerNotification notification;
}