package com.drawarena.drawarena_backend.service;

import com.drawarena.drawarena_backend.dto.CreateRoomRequest;
import com.drawarena.drawarena_backend.dto.JoinRoomRequest;
import com.drawarena.drawarena_backend.model.GameMode;
import com.drawarena.drawarena_backend.model.Player;
import com.drawarena.drawarena_backend.model.Room;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ConnectionServiceTest {

    @Autowired
    private ConnectionService connectionService;

    @Autowired
    private RoomService roomService;

    @Test
    void validPlayerShouldRegisterWebSocketConnection() {

        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("Raj");
        request.setMaxPlayers(4);
        request.setGameMode(GameMode.CLASSIC);

        Room room = roomService.createRoom(request);

        String playerId = room.getHostPlayerId();

        String sessionId = "session-337";

        String principalName = "principal-337";

        connectionService.registerConnection(
                sessionId,
                room.getRoomId(),
                playerId,
                principalName);

        assertTrue(connectionService.isValidPlayerSession(
                sessionId,
                room.getRoomId(),
                playerId));

        connectionService.removePlayerConnections(room.getRoomId(), playerId);
    }

    @Test
    void registeredPlayerShouldHavePrincipalName() {

        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("Raj");
        request.setMaxPlayers(4);
        request.setGameMode(GameMode.CLASSIC);

        Room room = roomService.createRoom(request);

        String playerId = room.getHostPlayerId();

        String principalName = "principal-338";

        connectionService.registerConnection(
                "session-338",
                room.getRoomId(),
                playerId,
                principalName);

        assertEquals(principalName,
                connectionService.getPrincipalName(
                        room.getRoomId(),
                        playerId));

        connectionService.removePlayerConnections(room.getRoomId(), playerId);
    }

    @Test
    void registerConnectionShouldRejectUnknownPlayer() {

        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("Raj");
        request.setMaxPlayers(4);
        request.setGameMode(GameMode.CLASSIC);

        Room room = roomService.createRoom(request);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> connectionService.registerConnection(
                        "session-339",
                        room.getRoomId(),
                        "fake-player-id",
                        "principal-339"));

        assertEquals("Player does not exist in room", exception.getMessage());
    }

    @Test
    void registerConnectionShouldRejectMissingPrincipal() {

        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("Raj");
        request.setMaxPlayers(4);
        request.setGameMode(GameMode.CLASSIC);

        Room room = roomService.createRoom(request);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> connectionService.registerConnection(
                        "session-340",
                        room.getRoomId(),
                        room.getHostPlayerId(),
                        null));

        assertEquals("WebSocket principal is missing", exception.getMessage());
    }

    @Test
    void roomIdShouldBeNormalizedDuringConnectionRegistration() {

        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("Raj");
        request.setMaxPlayers(4);
        request.setGameMode(GameMode.CLASSIC);

        Room room = roomService.createRoom(request);

        String lowerCaseRoomId = room.getRoomId().toLowerCase();

        connectionService.registerConnection(
                "session-341",
                lowerCaseRoomId,
                room.getHostPlayerId(),
                "principal-341");

        assertTrue(connectionService.isValidPlayerSession(
                "session-341",
                room.getRoomId(),
                room.getHostPlayerId()));

        connectionService.removePlayerConnections(room.getRoomId(), room.getHostPlayerId());
    }

    @Test
    void sameSessionShouldNotBeRegisteredToDifferentPlayer() {

        CreateRoomRequest createRequest = new CreateRoomRequest();
        createRequest.setName("Raj");
        createRequest.setMaxPlayers(4);
        createRequest.setGameMode(GameMode.CLASSIC);

        Room room = roomService.createRoom(createRequest);

        JoinRoomRequest joinRequest = new JoinRoomRequest();

        joinRequest.setName("Rahul");

        roomService.joinRoom(room.getRoomId(), joinRequest);

        String rajId = room.getHostPlayerId();

        String rahulId = room.getPlayers()
                .stream()
                .filter(player -> !player.getPlayerId()
                        .equals(rajId))
                .findFirst()
                .orElseThrow()
                .getPlayerId();

        String sessionId = "session-342";

        connectionService.registerConnection(
                sessionId,
                room.getRoomId(),
                rajId,
                "principal-raj");

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> connectionService.registerConnection(
                        sessionId,
                        room.getRoomId(),
                        rahulId,
                        "principal-rahul"));

        assertEquals("WebSocket session is already registered", exception.getMessage());

        connectionService.removePlayerConnections(room.getRoomId(), rajId);
    }

    @Test
    void removePlayerConnectionsShouldInvalidateSession() {

        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("Raj");
        request.setMaxPlayers(4);
        request.setGameMode(GameMode.CLASSIC);

        Room room = roomService.createRoom(request);

        String playerId = room.getHostPlayerId();

        String sessionId = "session-343";

        connectionService.registerConnection(
                sessionId,
                room.getRoomId(),
                playerId,
                "principal-343");

        assertTrue(connectionService.isValidPlayerSession(
                sessionId,
                room.getRoomId(),
                playerId));

        connectionService.removePlayerConnections(room.getRoomId(), playerId);

        assertFalse(connectionService.isValidPlayerSession(
                sessionId,
                room.getRoomId(),
                playerId));
    }

    @Test
    void removePlayerConnectionsShouldClearPrincipalMapping() {

        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("Raj");
        request.setMaxPlayers(4);
        request.setGameMode(GameMode.CLASSIC);

        Room room = roomService.createRoom(request);

        String playerId = room.getHostPlayerId();

        connectionService.registerConnection(
                "session-344",
                room.getRoomId(),
                playerId,
                "principal-344");

        assertEquals("principal-344", connectionService.getPrincipalName(room.getRoomId(), playerId));

        connectionService.removePlayerConnections(room.getRoomId(), playerId);

        assertNull(connectionService.getPrincipalName(room.getRoomId(), playerId));
    }

    @Test
    void reconnectShouldUpdatePrincipalMapping() {

        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("Raj");
        request.setMaxPlayers(4);
        request.setGameMode(GameMode.CLASSIC);

        Room room = roomService.createRoom(request);

        String playerId = room.getHostPlayerId();

        connectionService.registerConnection(
                "session-old",
                room.getRoomId(),
                playerId,
                "principal-old");

        connectionService.registerConnection(
                "session-new",
                room.getRoomId(),
                playerId,
                "principal-new");

        assertEquals("principal-new",
                connectionService.getPrincipalName(
                        room.getRoomId(),
                        playerId));

        connectionService.removePlayerConnections(room.getRoomId(), playerId);
    }

    @Test
    void samePlayerMayHaveMultipleActiveSessions() {

        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("Raj");
        request.setMaxPlayers(4);
        request.setGameMode(GameMode.CLASSIC);

        Room room = roomService.createRoom(request);

        String playerId = room.getHostPlayerId();

        connectionService.registerConnection(
                "session-a",
                room.getRoomId(),
                playerId,
                "principal-a");

        connectionService.registerConnection(
                "session-b",
                room.getRoomId(),
                playerId,
                "principal-b");

        assertTrue(connectionService.isValidPlayerSession(
                "session-a",
                room.getRoomId(),
                playerId));

        assertTrue(connectionService.isValidPlayerSession(
                "session-b",
                room.getRoomId(),
                playerId));

        connectionService.removePlayerConnections(room.getRoomId(), playerId);
    }

    @Test
    void removePlayerConnectionsShouldClearAllPlayerSessions() {

        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("Raj");
        request.setMaxPlayers(4);
        request.setGameMode(GameMode.CLASSIC);

        Room room = roomService.createRoom(request);

        String playerId = room.getHostPlayerId();

        connectionService.registerConnection(
                "session-1",
                room.getRoomId(),
                playerId,
                "principal-1");

        connectionService.registerConnection(
                "session-2",
                room.getRoomId(),
                playerId,
                "principal-2");

        connectionService.removePlayerConnections(room.getRoomId(), playerId);

        assertFalse(connectionService.isValidPlayerSession(
                "session-1",
                room.getRoomId(),
                playerId));

        assertFalse(connectionService.isValidPlayerSession(
                "session-2",
                room.getRoomId(),
                playerId));

        assertNull(connectionService.getPrincipalName(
                room.getRoomId(),
                playerId));
    }

    @Test
    void principalLookupAndRemovalShouldNormalizeRoomId() {

        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("Raj");
        request.setMaxPlayers(4);
        request.setGameMode(GameMode.CLASSIC);

        Room room = roomService.createRoom(request);

        String playerId = room.getHostPlayerId();

        connectionService.registerConnection(
                "session-349",
                room.getRoomId(),
                playerId,
                "principal-349");

        String lowerCaseRoomId = room.getRoomId().toLowerCase();

        assertEquals("principal-349",
                connectionService.getPrincipalName(
                        lowerCaseRoomId,
                        playerId));

        connectionService.removePlayerConnections(lowerCaseRoomId, playerId);

        assertNull(connectionService.getPrincipalName(room.getRoomId(), playerId));
    }

    @Test
    void disconnectShouldNotRemovePlayerImmediately() {

        CreateRoomRequest createRequest = new CreateRoomRequest();
        createRequest.setName("Raj");
        createRequest.setMaxPlayers(4);
        createRequest.setGameMode(GameMode.CLASSIC);

        Room room = roomService.createRoom(createRequest);

        JoinRoomRequest joinRequest = new JoinRoomRequest();

        joinRequest.setName("Rahul");

        roomService.joinRoom(room.getRoomId(), joinRequest);

        Player rahul = room.getPlayers()
                .stream()
                .filter(player -> player.getName().equals("Rahul"))
                .findFirst()
                .orElseThrow();

        String sessionId = "session-351";

        connectionService.registerConnection(
                sessionId,
                room.getRoomId(),
                rahul.getPlayerId(),
                "principal-351");

        connectionService.handleDisconnect(sessionId);

        assertTrue(
                roomService.playerExistsInRoom(room.getRoomId(), rahul.getPlayerId()));
    }

    @Test
    void disconnectedPlayerShouldBeRemovedAfterReconnectWindow()
            throws InterruptedException {

        CreateRoomRequest createRequest = new CreateRoomRequest();
        createRequest.setName("Raj");
        createRequest.setMaxPlayers(4);
        createRequest.setGameMode(GameMode.CLASSIC);

        Room room = roomService.createRoom(createRequest);

        JoinRoomRequest joinRequest = new JoinRoomRequest();

        joinRequest.setName("Rahul");

        roomService.joinRoom(room.getRoomId(), joinRequest);

        Player rahul = room.getPlayers()
                .stream()
                .filter(player -> player.getName().equals("Rahul"))
                .findFirst()
                .orElseThrow();

        String sessionId = "session-352";

        connectionService.registerConnection(
                sessionId,
                room.getRoomId(),
                rahul.getPlayerId(),
                "principal-352");

        connectionService.handleDisconnect(sessionId);

        Thread.sleep(5500);

        assertFalse(roomService.playerExistsInRoom(
                room.getRoomId(),
                rahul.getPlayerId()));
    }

    @Test
    void reconnectShouldCancelPendingPlayerRemoval()
            throws InterruptedException {

        CreateRoomRequest createRequest = new CreateRoomRequest();
        createRequest.setName("Raj");
        createRequest.setMaxPlayers(4);
        createRequest.setGameMode(GameMode.CLASSIC);

        Room room = roomService.createRoom(createRequest);

        JoinRoomRequest joinRequest = new JoinRoomRequest();

        joinRequest.setName("Rahul");

        roomService.joinRoom(room.getRoomId(), joinRequest);

        Player rahul = room.getPlayers()
                .stream()
                .filter(player -> player.getName().equals("Rahul"))
                .findFirst()
                .orElseThrow();

        connectionService.registerConnection(
                "old-session-353",
                room.getRoomId(),
                rahul.getPlayerId(),
                "old-principal-353");

        connectionService.handleDisconnect("old-session-353");

        // Reconnect before the 5-second tolerance expires
        connectionService.registerConnection(
                "new-session-353",
                room.getRoomId(),
                rahul.getPlayerId(),
                "new-principal-353");

        Thread.sleep(5500);

        assertTrue(roomService.playerExistsInRoom(
                room.getRoomId(),
                rahul.getPlayerId()));

        assertTrue(connectionService.isValidPlayerSession(
                "new-session-353",
                room.getRoomId(),
                rahul.getPlayerId()));

        connectionService.removePlayerConnections(room.getRoomId(), rahul.getPlayerId());
    }

    @Test
    void reconnectShouldUseNewestPrincipal() {

        CreateRoomRequest createRequest = new CreateRoomRequest();
        createRequest.setName("Raj");
        createRequest.setMaxPlayers(4);
        createRequest.setGameMode(GameMode.CLASSIC);

        Room room = roomService.createRoom(createRequest);

        String playerId = room.getHostPlayerId();

        connectionService.registerConnection(
                "old-session-354",
                room.getRoomId(),
                playerId,
                "old-principal-354");

        connectionService.handleDisconnect("old-session-354");

        connectionService.registerConnection(
                "new-session-354",
                room.getRoomId(),
                playerId,
                "new-principal-354");

        assertEquals("new-principal-354",
                connectionService.getPrincipalName(
                        room.getRoomId(),
                        playerId));

        connectionService.removePlayerConnections(room.getRoomId(), playerId);
    }
}
