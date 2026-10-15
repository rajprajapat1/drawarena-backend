package com.drawarena.drawarena_backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.drawarena.drawarena_backend.dto.CreateRoomRequest;
import com.drawarena.drawarena_backend.dto.DrawerTurnResponse;
import com.drawarena.drawarena_backend.dto.JoinRoomRequest;
import com.drawarena.drawarena_backend.dto.PlayAgainRequest;
import com.drawarena.drawarena_backend.dto.RoomResponse;
import com.drawarena.drawarena_backend.dto.RoomSessionResponse;
import com.drawarena.drawarena_backend.dto.SelectWordRequest;
import com.drawarena.drawarena_backend.dto.SidePanelMessage;
import com.drawarena.drawarena_backend.dto.StartGameRequest;
import com.drawarena.drawarena_backend.model.Room;
import com.drawarena.drawarena_backend.service.ConnectionService;
import com.drawarena.drawarena_backend.service.RoomService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
// @CrossOrigin(origins = "*")
public class RoomController {

    private final RoomService roomService;
    private final ConnectionService connectionService;

    @PostMapping
    public RoomSessionResponse createRoom(
            @Valid @RequestBody CreateRoomRequest request) {
        Room room = roomService.createRoom(request);
        RoomResponse roomResponse = roomService.convertToRoomResponse(room);
        return new RoomSessionResponse(room.getRoomId(), room.getHostPlayerId(), roomResponse);
    }

    @PostMapping("/{roomId}/join")
    public RoomSessionResponse joinRoom(
            @PathVariable String roomId,
            @Valid @RequestBody JoinRoomRequest request) {

        Room room = roomService.joinRoom(roomId, request);
        String playerName = request.getName().trim();
        String playerId = room.getPlayers()
                .stream()
                .filter(player -> player.getName()
                        .equalsIgnoreCase(playerName))
                .findFirst()
                .orElseThrow(
                        () -> new RuntimeException("Joined player not found"))
                .getPlayerId();

        RoomResponse roomResponse = roomService.convertToRoomResponse(room);
        return new RoomSessionResponse(room.getRoomId(), playerId, roomResponse);
    }

    @GetMapping("/{roomId}")
    public RoomResponse getRoom(
            @PathVariable String roomId) {
        Room room = roomService.getRoom(roomId);
        return roomService.convertToRoomResponse(room);
    }

    @PostMapping("/{roomId}/start")
    public RoomResponse startGame(
            @PathVariable String roomId,
            @RequestBody StartGameRequest request) {
        Room room = roomService.startGame(roomId, request);
        return roomService.convertToRoomResponse(room);
    }

    @PostMapping("/{roomId}/select-word")
    public DrawerTurnResponse selectWord(
            @PathVariable String roomId,
            @Valid @RequestBody SelectWordRequest request) {
        Room room = roomService.selectWord(roomId, request);
        return roomService.convertToDrawerTurnResponse(room);
    }

    @DeleteMapping("/{roomId}/players/{playerId}")
    public void leaveRoom(
            @PathVariable String roomId,
            @PathVariable String playerId) {
        connectionService.removePlayerConnections(roomId, playerId);
        roomService.leaveRoom(roomId, playerId);
    }

    @PostMapping("/{roomId}/play-again")
    public RoomResponse playAgain(
            @PathVariable String roomId,
            @Valid @RequestBody PlayAgainRequest request) {
        Room room = roomService.playAgain(roomId, request);
        return roomService.convertToRoomResponse(room);
    }

    @GetMapping("/{roomId}/drawer/{playerId}")
    public DrawerTurnResponse getDrawerTurn(
            @PathVariable String roomId,
            @PathVariable String playerId) {
        return roomService.getDrawerTurn(roomId, playerId);
    }

    @GetMapping("/{roomId}/messages")
    public List<SidePanelMessage> getMessages(
            @PathVariable String roomId) {
        return roomService.getSidePanelMessages(roomId);
    }
}
