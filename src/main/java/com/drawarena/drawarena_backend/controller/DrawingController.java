package com.drawarena.drawarena_backend.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import com.drawarena.drawarena_backend.dto.DrawingMessage;
import com.drawarena.drawarena_backend.service.ConnectionService;
import com.drawarena.drawarena_backend.service.RoomService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class DrawingController {

    private final RoomService roomService;
    private final ConnectionService connectionService;

    @MessageMapping("/draw")
    public void draw(@Valid DrawingMessage message,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();

        if (sessionId == null) {
            throw new RuntimeException("WebSocket session not found");
        }

        boolean valid = connectionService.isValidPlayerSession(
                sessionId,
                message.getRoomId(),
                message.getPlayerId());

        if (!valid) {
            throw new RuntimeException("Invalid player session");
        }
        roomService.handleDrawing(message);
    }

    @MessageMapping("/clear-canvas")
    public void clearCanvas(DrawingMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();

        if (sessionId == null) {
            throw new RuntimeException("WebSocket session not found");
        }

        boolean valid = connectionService.isValidPlayerSession(sessionId, message.getRoomId(), message.getPlayerId());
        if (!valid) {
            throw new RuntimeException("Invalid player session");
        }

        roomService.clearCanvas(message.getRoomId(), message.getPlayerId());
    }

}
