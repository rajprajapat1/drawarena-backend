package com.drawarena.drawarena_backend.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import com.drawarena.drawarena_backend.dto.GuessMessage;
import com.drawarena.drawarena_backend.service.ConnectionService;
import com.drawarena.drawarena_backend.service.RoomService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class GuessController {

    private final RoomService roomService;
    private final ConnectionService connectionService;

    @MessageMapping("/guess")
    public void guess(@Valid GuessMessage message,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        if (sessionId == null) {
            throw new RuntimeException("WebSocket session not found");
        }

        if (!connectionService.isValidPlayerSession(sessionId,
                message.getRoomId(),
                message.getPlayerId())) {
            throw new RuntimeException("Invalid player session");
        }
        roomService.handleGuess(message);
    }
}
