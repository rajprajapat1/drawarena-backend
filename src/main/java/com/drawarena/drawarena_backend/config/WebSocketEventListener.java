package com.drawarena.drawarena_backend.config;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.drawarena.drawarena_backend.service.ConnectionService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final ConnectionService connectionService;

    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {

        String sessionId = event.getSessionId();
        connectionService.handleDisconnect(sessionId);
    }
}
