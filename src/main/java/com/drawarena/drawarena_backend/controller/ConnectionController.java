package com.drawarena.drawarena_backend.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import com.drawarena.drawarena_backend.dto.ConnectionRegisterMessage;
import com.drawarena.drawarena_backend.service.ConnectionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;

    @MessageMapping("/register")
    public void register(
            @Valid ConnectionRegisterMessage message,
            SimpMessageHeaderAccessor headerAccessor,
            Principal principal) {

        String sessionId = headerAccessor.getSessionId();

        if (sessionId == null) {
            return;
        }

        if (principal == null) {
            return;
        }

        connectionService.registerConnection(sessionId, message.getRoomId(), message.getPlayerId(),
                principal.getName());
    }
}
