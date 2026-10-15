package com.drawarena.drawarena_backend.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.drawarena.drawarena_backend.model.PlayerConnection;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConnectionService {

    private final RoomService roomService;

    private final Map<String, PlayerConnection> connections = new ConcurrentHashMap<>();

    private final Map<String, String> playerPrincipals = new ConcurrentHashMap<>();

    private final Map<String, ScheduledFuture<?>> pendingDisconnects = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private String playerKey(String roomId, String playerId) {

        String normalizedRoomId = normalizeRoomId(roomId);
        return normalizedRoomId + ":" + playerId;
    }

    private String normalizeRoomId(String roomId) {

        if (roomId == null) {
            return null;
        }
        return roomId.trim().toUpperCase();
    }

    public void registerConnection(String sessionId, String roomId, String playerId, String principalName) {

        if (principalName == null || principalName.isBlank()) {
            throw new RuntimeException("WebSocket principal is missing");
        }

        String normalizedRoomId = normalizeRoomId(roomId);
        if (normalizedRoomId == null || normalizedRoomId.isBlank()) {
            throw new RuntimeException("Room ID is required");
        }
        if (!roomService.playerExistsInRoom(normalizedRoomId, playerId)) {
            throw new RuntimeException("Player does not exist in room");
        }

        PlayerConnection existingConnection = connections.get(sessionId);
        if (existingConnection != null) {
            boolean samePlayer = existingConnection.getRoomId().equals(normalizedRoomId)
                    && existingConnection.getPlayerId().equals(playerId);
            if (!samePlayer) {
                throw new RuntimeException("WebSocket session is already registered");
            }
        }

        PlayerConnection connection = new PlayerConnection(sessionId, normalizedRoomId, playerId, principalName);
        connections.put(sessionId, connection);

        String key = playerKey(normalizedRoomId, playerId);
        playerPrincipals.put(key, principalName);

        ScheduledFuture<?> pending = pendingDisconnects.remove(key);

        if (pending != null) {
            pending.cancel(false);
        }
    }

    public void handleDisconnect(String sessionId) {

        PlayerConnection connection = connections.remove(sessionId);

        if (connection == null) {
            return;
        }

        String roomId = connection.getRoomId();
        String playerId = connection.getPlayerId();
        String key = playerKey(roomId, playerId);

        String disconnectedPrincipal = connection.getPrincipalName();

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            pendingDisconnects.remove(key);

            if (hasActiveConnection(roomId, playerId)) {
                return;
            }
            playerPrincipals.remove(key, disconnectedPrincipal);
            try {
                roomService.leaveRoom(roomId, playerId);
            } catch (RuntimeException ignored) {
                // Room/player may already have been removed.
            }
        }, 5, TimeUnit.SECONDS);
        pendingDisconnects.put(key, future);
    }

    public void removePlayerConnections(String roomId, String playerId) {

        String normalizedRoomId = normalizeRoomId(roomId);
        if (normalizedRoomId == null || normalizedRoomId.isBlank()) {
            return;
        }

        connections.entrySet()
                .removeIf(entry -> {
                    PlayerConnection connection = entry.getValue();
                    return connection.getRoomId().equals(normalizedRoomId)
                            && connection.getPlayerId().equals(playerId);
                });

        String key = playerKey(normalizedRoomId, playerId);
        playerPrincipals.remove(key);
        ScheduledFuture<?> pending = pendingDisconnects.remove(key);

        if (pending != null) {
            pending.cancel(false);
        }
    }

    public boolean isValidPlayerSession(String sessionId, String roomId, String playerId) {

        String normalizedRoomId = normalizeRoomId(roomId);
        if (normalizedRoomId == null) {
            return false;
        }
        PlayerConnection connection = connections.get(sessionId);
        if (connection == null) {
            return false;
        }
        return connection.getRoomId().equals(normalizedRoomId)
                && connection.getPlayerId().equals(playerId);
    }

    public String getPrincipalName(String roomId, String playerId) {

        String normalizedRoomId = normalizeRoomId(roomId);
        if (normalizedRoomId == null) {
            return null;
        }
        return playerPrincipals.get(playerKey(normalizedRoomId, playerId));
    }

    @PreDestroy
    public void shutdownScheduler() {

        for (ScheduledFuture<?> pending : pendingDisconnects.values()) {
            if (pending != null) {
                pending.cancel(false);
            }
        }
        pendingDisconnects.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException exception) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private boolean hasActiveConnection(String roomId, String playerId) {

        return connections.values()
                .stream()
                .anyMatch(connection -> connection.getRoomId().equals(roomId)
                        && connection.getPlayerId().equals(playerId));
    }
}
