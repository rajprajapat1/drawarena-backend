package com.drawarena.drawarena_backend.service;

import com.drawarena.drawarena_backend.event.DrawerPrivateEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PrivateDrawerMessagingService {

    private final ConnectionService connectionService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleDrawerPrivateEvent(DrawerPrivateEvent event) {

        String principalName = connectionService.getPrincipalName(
                event.getRoomId(),
                event.getPlayerId());

        if (principalName == null || principalName.isBlank()) {
    return;
}
 
        messagingTemplate.convertAndSendToUser(
                principalName,
                "/queue/drawer",
                event.getNotification());
    }
}