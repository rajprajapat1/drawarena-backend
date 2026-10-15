package com.drawarena.drawarena_backend.service;

import com.drawarena.drawarena_backend.dto.DrawerNotification;
import com.drawarena.drawarena_backend.event.DrawerPrivateEvent;
import com.drawarena.drawarena_backend.model.TurnPhase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.*;

import java.util.List;

class PrivateDrawerMessagingServiceTest {

    private ConnectionService connectionService;
    private SimpMessagingTemplate messagingTemplate;
    private PrivateDrawerMessagingService service;

    @BeforeEach
    void setUp() {

        connectionService = Mockito.mock(ConnectionService.class);

        messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);

        service = new PrivateDrawerMessagingService(
                connectionService,
                messagingTemplate);
    }

    @Test
    void drawerNotificationShouldBeSentToMappedPrincipal() {

        DrawerNotification notification = new DrawerNotification(
                "player-1",
                TurnPhase.WORD_SELECTION,
                List.of("apple", "house", "river"),
                null,
                0L,
                0L);

        DrawerPrivateEvent event = new DrawerPrivateEvent(
                "ABC123",
                "player-1",
                notification);

        when(connectionService.getPrincipalName(
                "ABC123",
                "player-1")).thenReturn("principal-1");

        service.handleDrawerPrivateEvent(event);

        verify(messagingTemplate).convertAndSendToUser(
                "principal-1",
                "/queue/drawer",
                notification);
    }

    @Test
    void drawerNotificationShouldNotSendWhenPrincipalMissing() {

        DrawerNotification notification = new DrawerNotification(
                "player-1",
                TurnPhase.WORD_SELECTION,
                List.of("apple", "house", "river"),
                null,
                0L,
                0L);

        DrawerPrivateEvent event = new DrawerPrivateEvent(
                "ABC123",
                "player-1",
                notification);

        when(connectionService.getPrincipalName(
                "ABC123",
                "player-1"))
                .thenReturn(null);

        service.handleDrawerPrivateEvent(event);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void drawerNotificationShouldNotSendWhenPrincipalBlank() {

        DrawerNotification notification = new DrawerNotification(
                "player-1",
                TurnPhase.WORD_SELECTION,
                List.of("apple", "house", "river"),
                null,
                0L,
                0L);

        DrawerPrivateEvent event = new DrawerPrivateEvent(
                "ABC123",
                "player-1",
                notification);

        when(connectionService.getPrincipalName(
                "ABC123",
                "player-1")).thenReturn("   ");

        service.handleDrawerPrivateEvent(event);

        verifyNoInteractions(messagingTemplate);
    }
}