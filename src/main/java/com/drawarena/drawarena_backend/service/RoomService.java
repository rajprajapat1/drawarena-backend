package com.drawarena.drawarena_backend.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Comparator;
import java.util.HashMap;
//import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import com.drawarena.drawarena_backend.dto.CanvasEvent;
import com.drawarena.drawarena_backend.dto.CreateRoomRequest;
import com.drawarena.drawarena_backend.dto.DrawerNotification;
import com.drawarena.drawarena_backend.dto.DrawerTurnResponse;
import com.drawarena.drawarena_backend.dto.DrawingMessage;
import com.drawarena.drawarena_backend.dto.GuessMessage;
import com.drawarena.drawarena_backend.dto.GuessResult;
import com.drawarena.drawarena_backend.dto.JoinRoomRequest;
import com.drawarena.drawarena_backend.dto.LeaderboardEntry;
import com.drawarena.drawarena_backend.dto.PlayAgainRequest;
import com.drawarena.drawarena_backend.dto.RoomResponse;
import com.drawarena.drawarena_backend.dto.SelectWordRequest;
import com.drawarena.drawarena_backend.dto.SidePanelEvent;
import com.drawarena.drawarena_backend.dto.SidePanelMessage;
import com.drawarena.drawarena_backend.dto.StartGameRequest;
import com.drawarena.drawarena_backend.model.GameMode;
import com.drawarena.drawarena_backend.model.GameState;
import com.drawarena.drawarena_backend.model.Player;
import com.drawarena.drawarena_backend.model.Room;
import com.drawarena.drawarena_backend.model.Team;
import com.drawarena.drawarena_backend.model.TurnPhase;

import com.drawarena.drawarena_backend.repository.RoomRedisRepository;

import jakarta.annotation.PreDestroy;

import com.drawarena.drawarena_backend.event.DrawerPrivateEvent;

import org.springframework.messaging.Message;
import org.springframework.context.ApplicationEventPublisher;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class RoomService {

    private final WordService wordService;
    private final RoomRedisRepository roomRedisRepository;

    private final SimpMessagingTemplate messagingTemplate;

    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, Object> roomLocks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    public Room createRoom(CreateRoomRequest request) {

        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Player name is required");
        }

        if (request.getMaxPlayers() < 2 || request.getMaxPlayers() > 15) {
            throw new RuntimeException("Max players must be between 2 and 15");
        }

        int drawTime = request.getDrawTimeSeconds();
        if (drawTime != 60 && drawTime != 80 && drawTime != 100 && drawTime != 120) {
            throw new RuntimeException("Draw time must be 60, 80, 100, or 120 seconds");
        }

        if (request.getGameMode() == null) {
            throw new RuntimeException("Game mode is required");
        }

        String hostName = request.getName().trim();

        Room room = new Room();

        room.setAvailableWords(wordService.createShuffledWordPool());

        room.setRoomId(generateUniqueRoomId());

        room.setMaxPlayers(request.getMaxPlayers());
        room.setDrawTimeSeconds(drawTime);
        room.setGameMode(request.getGameMode());
        room.setGameState(GameState.LOBBY);
        room.setCurrentRound(0);
        room.setCurrentDrawerId(null);
        room.setCurrentTurnIndex(0);

        Player host = new Player();

        host.setPlayerId(
                UUID.randomUUID()
                        .toString());
        host.setName(hostName);
        host.setAvatar(request.getAvatar());
        host.setScore(0);

        room.getPlayers().add(host);
        room.setHostPlayerId(host.getPlayerId());

        while (rooms.putIfAbsent(room.getRoomId(), room) != null) {
            room.setRoomId(generateUniqueRoomId());
        }
        roomLocks.putIfAbsent(room.getRoomId(), new Object());
        saveRoom(room);
        return room;
    }

    public Room joinRoom(String roomId, JoinRoomRequest request) {

        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Player name is required");
        }

        String playerName = request.getName().trim();

        String normalizedRoomId = roomId != null
                ? roomId.trim().toUpperCase()
                : null;

        Room existingRoom = getRoomFromStorage(normalizedRoomId);

        if (existingRoom == null) {
            throw new RuntimeException("Room not found with ID: " + roomId);
        }

        synchronized (getRoomLock(normalizedRoomId)) {
            Room room = rooms.get(normalizedRoomId);
            if (room == null) {
                throw new RuntimeException("Room not found with ID: " + roomId);
            }

            if (room.getPlayers().size() >= room.getMaxPlayers()) {
                throw new RuntimeException("Room is full");
            }

            boolean nameExists = room.getPlayers()
                    .stream()
                    .anyMatch(player -> player.getName().equalsIgnoreCase(playerName));

            if (nameExists) {
                throw new RuntimeException("Player name already exists in the room");
            }

            Player player = new Player();
            player.setPlayerId(UUID.randomUUID().toString());
            player.setName(playerName);
            player.setAvatar(request.getAvatar());
            player.setScore(0);

            if (room.getGameState() != GameState.LOBBY) {
                assignTeamToJoiningPlayer(room, player);
            }

            room.getPlayers().add(player);

            if (room.getGameState() == GameState.ROUND_1
                    || room.getGameState() == GameState.ROUND_2
                    || room.getGameState() == GameState.ROUND_3) {

                if (!room.getEligiblePlayerIds()
                        .contains(player.getPlayerId())) {

                    room.getEligiblePlayerIds()
                            .add(player.getPlayerId());
                }
            }
            saveRoom(room);
            broadcastRoomUpdate(room);
            return room;
        }
    }

    public Room getRoom(String roomId) {
    String normalizedRoomId = requireNormalizedRoomId(roomId);
    requireRoom(normalizedRoomId);

    synchronized (getRoomLock(normalizedRoomId)) {
        Room room = rooms.get(normalizedRoomId);
        if (room == null) {
            throw new RuntimeException("Room not found with ID: " + roomId);
        }
        return room;
    }
}

    public Room startGame(String roomId, StartGameRequest request) {

        String normalizedRoomId = requireNormalizedRoomId(roomId);
        requireRoom(normalizedRoomId);

        synchronized (getRoomLock(normalizedRoomId)) {
            Room room = rooms.get(normalizedRoomId);

            if (room == null) {
                throw new RuntimeException("Room not found");
            }
            if (!room.getHostPlayerId().equals(request.getPlayerId())) {
                throw new RuntimeException("Only the host can start the game");
            }
            if (room.getPlayers().size() < 2) {
                throw new RuntimeException("At least 2 players are required to start");
            }
            if (room.getGameState() != GameState.LOBBY) {
                throw new RuntimeException("Game cannot be started in the current state");
            }

            assignTeams(room);
            room.setGameState(GameState.STARTING);
            room.setCountdownEndTime(System.currentTimeMillis() + 10_000);
            saveRoom(room);
            broadcastRoomUpdate(room);
            startCountdown(normalizedRoomId);

            return room;
        }
    }

    private void startCountdown(String roomId) {
    Room room = getRoomFromStorage(roomId);

    if (room == null) {
        return;
    }

    long delay = Math.max(0, room.getCountdownEndTime() - System.currentTimeMillis()
    );

    scheduler.schedule(() -> {
        Room existingRoom = getRoomFromStorage(roomId);

        if (existingRoom == null) {
            return;
        }

        synchronized (getRoomLock(roomId)) {
            Room currentRoom = rooms.get(roomId);

            if (currentRoom == null || currentRoom.getGameState() != GameState.STARTING) {
                return;
            }

            initializeRoundOne(currentRoom);
        }
    }, delay, TimeUnit.MILLISECONDS);
}

    public void initializeRoundOne(Room room) {
        room.setCurrentRound(1);
        room.setGameState(GameState.ROUND_1);

        room.setCountdownEndTime(0);

        room.getEligiblePlayerIds().clear();

        for (Player player : room.getPlayers()) {
            room.getEligiblePlayerIds().add(player.getPlayerId());
        }
        room.setCurrentTurnIndex(0);

        room.getCorrectGuesserIds().clear();

        if (!room.getEligiblePlayerIds().isEmpty()) {
            broadcastCanvasClear(room);
            room.setCurrentDrawerId(room.getEligiblePlayerIds().get(0));

            room.setTurnPhase(TurnPhase.WORD_SELECTION);

            List<String> nextWords = wordService.getNextWords(
                    room.getAvailableWords(),
                    room.getLastWordChoices());
            room.setWordChoices(nextWords);
            room.setLastWordChoices(new ArrayList<>(nextWords));

            room.setCurrentWord(null);
            room.getRevealedHintIndexes().clear();
            room.setWordSelectionEndTime(0);
            broadcastDrawerNotification(room);
            startWordSelectionTimer(room.getRoomId());
            saveRoom(room);
            broadcastRoomUpdate(room);
        }
    }

    public Room selectWord(String roomId, SelectWordRequest request) {

        String normalizedRoomId = requireNormalizedRoomId(roomId);
        requireRoom(normalizedRoomId);

        synchronized (getRoomLock(normalizedRoomId)) {
            Room room = rooms.get(normalizedRoomId);

            if (room == null) {

                throw new RuntimeException(
                        "Room not found");
            }

            if (room.getTurnPhase() != TurnPhase.WORD_SELECTION) {

                throw new RuntimeException(
                        "Word selection is not active");
            }

            if (room.getCurrentDrawerId() == null) {

                throw new RuntimeException(
                        "No active drawer");
            }

            if (!room.getCurrentDrawerId()
                    .equals(
                            request.getPlayerId())) {

                throw new RuntimeException(
                        "Only the current drawer can select a word");
            }

            if (room.getWordChoices() == null
                    ||
                    room.getWordChoices()
                            .isEmpty()) {

                throw new RuntimeException(
                        "No word choices available");
            }

            if (room.getCurrentWord() != null
                    &&
                    !room.getCurrentWord()
                            .isBlank()) {

                throw new RuntimeException(
                        "A word has already been selected");
            }

            String selectedWord = room.getWordChoices()
                    .stream()
                    .filter(
                            word -> word.equalsIgnoreCase(
                                    request.getWord()))
                    .findFirst()
                    .orElseThrow(
                            () -> new RuntimeException(
                                    "Selected word is not one of the available choices"));

            room.setCurrentWord(selectedWord);
            room.getWordChoices().clear();
            startDrawingPhase(room);
            broadcastDrawerNotification(room);
            saveRoom(room);
            broadcastRoomUpdate(room);

            return room;
        }
    }

    private void autoSelectWord(Room room) {

        if (room.getTurnPhase() != TurnPhase.WORD_SELECTION) {
            return;
        }
        if (room.getCurrentDrawerId() == null) {
            return;
        }
        if (room.getWordChoices() == null || room.getWordChoices().isEmpty()) {
            return;
        }

        if (room.getCurrentWord() != null && !room.getCurrentWord().isBlank()) {
            return;
        }

        String selectedWord = room.getWordChoices().get(0);

        room.setCurrentWord(selectedWord);

        room.getWordChoices().clear();

        startDrawingPhase(room);
        broadcastDrawerNotification(room);
        saveRoom(room);
        broadcastRoomUpdate(room);
    }

    private void startWordSelectionTimer(String roomId) {
    Room room = getRoomFromStorage(roomId);

    if (room == null) {
        return;
    }

    if (room.getWordSelectionEndTime() <= 0) {
        room.setWordSelectionEndTime(System.currentTimeMillis() + 5000);
        saveRoom(room);
    }

    String expectedDrawerId = room.getCurrentDrawerId();
    int expectedTurnIndex = room.getCurrentTurnIndex();
    int expectedRound = room.getCurrentRound();
    long expectedEndTime = room.getWordSelectionEndTime();

    long delay = Math.max(0, expectedEndTime - System.currentTimeMillis());

    scheduler.schedule(() -> {
        Room existingRoom = getRoomFromStorage(roomId);

        if (existingRoom == null) {
            return;
        }

        synchronized (getRoomLock(roomId)) {
            Room currentRoom = rooms.get(roomId);

            if (currentRoom == null
                    || currentRoom.getTurnPhase() != TurnPhase.WORD_SELECTION) {
                return;
            }

            if (currentRoom.getCurrentDrawerId() == null
                    || !currentRoom.getCurrentDrawerId().equals(expectedDrawerId)) {
                return;
            }

            if (currentRoom.getCurrentTurnIndex() != expectedTurnIndex
                    || currentRoom.getCurrentRound() != expectedRound) {
                return;
            }

            if (currentRoom.getWordSelectionEndTime() != expectedEndTime) {
                return;
            }

            currentRoom.setWordSelectionEndTime(0);
            autoSelectWord(currentRoom);
        }
    }, delay, TimeUnit.MILLISECONDS);
}

    private void startDrawingPhase(Room room) {
        long startTime = System.currentTimeMillis();
        long durationMillis = room.getDrawTimeSeconds() * 1000L;

            room.setWordSelectionEndTime(0);
        room.setTurnStartTime(startTime);
        room.setTurnEndTime(startTime + durationMillis);

        room.setTurnPhase(TurnPhase.DRAWING);
        room.getRevealedHintIndexes().clear();

        startHintTimers(room);
        startDrawingTimer(room);
    }

    private void startDrawingTimer(Room room) {
    String roomId = room.getRoomId();
    String expectedDrawerId = room.getCurrentDrawerId();
    long expectedStartTime = room.getTurnStartTime();
    int expectedRound = room.getCurrentRound();

    long delay = Math.max(0, room.getTurnEndTime() - System.currentTimeMillis()
    );

    scheduler.schedule(() -> {
        Room existingRoom = getRoomFromStorage(roomId);

        if (existingRoom == null) {
            return;
        }

        synchronized (getRoomLock(roomId)) {
            Room currentRoom = rooms.get(roomId);

            if (currentRoom == null || currentRoom.getTurnPhase() != TurnPhase.DRAWING) {
                return;
            }

            if (currentRoom.getCurrentDrawerId() == null || !currentRoom.getCurrentDrawerId().equals(expectedDrawerId)) {
                return;
            }

            if (currentRoom.getTurnStartTime() != expectedStartTime || currentRoom.getCurrentRound() != expectedRound) {
                return;
            }

            advanceToNextTurn(currentRoom);
        }
    }, delay, TimeUnit.MILLISECONDS);
}

    private void advanceToNextTurn(Room room) {

        String finishedWord = room.getCurrentWord();

        if (finishedWord != null &&
                !finishedWord.isBlank()) {

            broadcastTurnEnd(
                    room,
                    finishedWord);
        }

        room.setTurnPhase(null);

        room.setTurnStartTime(0);
        room.setTurnEndTime(0);
        room.setWordSelectionEndTime(0);

        room.setCurrentWord(null);

        room.getWordChoices().clear();
        room.getRevealedHintIndexes().clear();
        room.getCorrectGuesserIds().clear();

        saveRoom(room);
        broadcastRoomUpdate(room);

        CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS)
                .execute(() -> continueAfterTurnReveal(room.getRoomId()));
    }

    private void handleRoundComplete(Room room) {

        int currentRound = room.getCurrentRound();

        if (currentRound == 1) {

            broadcastRoundComplete(room, 1);

            CompletableFuture
                    .delayedExecutor(2, TimeUnit.SECONDS)
                    .execute(
                            () -> {

                                Room latestRoom = getRoomFromStorage(room.getRoomId());

                                if (latestRoom == null) {
                                    return;
                                }

                                initializeNextRound(latestRoom, 2, GameState.ROUND_2);
                            });

            return;
        }

        if (currentRound == 2) {

            broadcastRoundComplete(room, 2);

            CompletableFuture
                    .delayedExecutor(2, TimeUnit.SECONDS)
                    .execute(
                            () -> {

                                Room latestRoom = getRoomFromStorage(room.getRoomId());

                                if (latestRoom == null) {
                                    return;
                                }

                                initializeNextRound(latestRoom, 3, GameState.ROUND_3);
                            });

            return;
        }

        if (currentRound == 3) {

            broadcastRoundComplete(room, 3);

            CompletableFuture
                    .delayedExecutor(2, TimeUnit.SECONDS)
                    .execute(
                            () -> {

                                Room latestRoom = getRoomFromStorage(room.getRoomId());

                                if (latestRoom == null) {
                                    return;
                                }

                                latestRoom.setGameState(GameState.GAME_FINISHED);
                                latestRoom.setCurrentDrawerId(null);
                                latestRoom.setTurnPhase(null);
                                latestRoom.setCurrentWord(null);
                                latestRoom.getWordChoices().clear();
                                latestRoom.getCorrectGuesserIds().clear();
                                latestRoom.setTurnStartTime(0);
                                latestRoom.setTurnEndTime(0);

                                saveRoom(latestRoom);
                                broadcastRoomUpdate(latestRoom);
                            });
        }
    }

    private void initializeNextRound(Room room, int roundNumber, GameState gameState) {

        room.setCurrentRound(roundNumber);
        room.setGameState(gameState);

        rotateEligiblePlayers(room, roundNumber);

        room.setCurrentTurnIndex(0);

        room.getCorrectGuesserIds().clear();

        if (!room.getEligiblePlayerIds().isEmpty()) {
            broadcastCanvasClear(room);
            room.setCurrentDrawerId(room.getEligiblePlayerIds().get(0));
            room.setTurnPhase(TurnPhase.WORD_SELECTION);

            List<String> nextWords = wordService.getNextWords(
                    room.getAvailableWords(),
                    room.getLastWordChoices());
            room.setWordChoices(nextWords);
            room.setLastWordChoices(new ArrayList<>(nextWords));

            room.setCurrentWord(null);
            room.getRevealedHintIndexes().clear();
            room.setWordSelectionEndTime(0);
            broadcastDrawerNotification(room);
            room.setTurnStartTime(0);
            room.setTurnEndTime(0);

            startWordSelectionTimer(room.getRoomId());
            saveRoom(room);
            broadcastRoomUpdate(room);
        }
    }

    private void rotateEligiblePlayers(Room room, int roundNumber) {

        List<String> playerIds = new ArrayList<>();

        for (Player player : room.getPlayers()) {

            playerIds.add(
                    player.getPlayerId());
        }

        room.getEligiblePlayerIds()
                .clear();

        if (playerIds.isEmpty()) {

            return;
        }

        int rotation = (roundNumber - 1)
                % playerIds.size();

        Collections.rotate(
                playerIds,
                -rotation);

        room.getEligiblePlayerIds()
                .addAll(
                        playerIds);
    }

    public void leaveRoom(String roomId, String playerId) {

        String normalizedRoomId = requireNormalizedRoomId(roomId);

     requireRoom(normalizedRoomId);

    synchronized (getRoomLock(normalizedRoomId)) {
        Room room = rooms.get(normalizedRoomId);

            if (room == null) {
                throw new RuntimeException("Room not found");
            }

            Player leavingPlayer = room.getPlayers()
                    .stream()
                    .filter(player -> player.getPlayerId().equals(playerId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Player not found"));

            boolean wasCurrentDrawer = playerId.equals(room.getCurrentDrawerId());
            boolean wasHost = playerId.equals(room.getHostPlayerId());

            int leavingIndex = room.getEligiblePlayerIds().indexOf(playerId);
            int currentIndex = room.getCurrentTurnIndex();

            TurnPhase currentPhase = room.getTurnPhase();
            boolean isTurnRevealPeriod = currentPhase == null && wasCurrentDrawer;

            room.getPlayers().remove(leavingPlayer);
            room.getEligiblePlayerIds().remove(playerId);
            room.getCorrectGuesserIds().remove(playerId);

            if (wasHost && !room.getPlayers().isEmpty()) {
                Player newHost = room.getPlayers().get(0);
                room.setHostPlayerId(newHost.getPlayerId());
            }

            if (room.getPlayers().isEmpty()) {
                rooms.remove(normalizedRoomId);
                roomLocks.remove(normalizedRoomId);
                roomRedisRepository.delete(normalizedRoomId);
                return;
            }

            if (room.getPlayers().size() == 1
                    && room.getGameState() != GameState.LOBBY
                    && room.getGameState() != GameState.GAME_FINISHED) {

                Player remainingPlayer = room.getPlayers().get(0);
                room.setHostPlayerId(remainingPlayer.getPlayerId());

                resetRoomToLobby(room);
                return;
            }

            if (!wasCurrentDrawer
                    && leavingIndex != -1
                    && leavingIndex < currentIndex) {

                room.setCurrentTurnIndex(currentIndex - 1);
            }

            if (isTurnRevealPeriod) {
                if (leavingIndex != -1 && leavingIndex == currentIndex) {
                    room.setCurrentTurnIndex(currentIndex - 1);
                }

                saveRoom(room);
                broadcastRoomUpdate(room);
                return;
            }

            if (wasCurrentDrawer
                    && (currentPhase == TurnPhase.WORD_SELECTION
                            || currentPhase == TurnPhase.DRAWING)) {

                handleCurrentDrawerLeaving(room);
                return;
            }

            if (room.getTurnPhase() == TurnPhase.DRAWING
                    && haveAllGuessersGuessedCorrectly(room)) {

                saveRoom(room);
                broadcastRoomUpdate(room);
                advanceToNextTurn(room);
                return;
            }

            saveRoom(room);
            broadcastRoomUpdate(room);
        }
    }

    private void handleCurrentDrawerLeaving(Room room) {

        room.setCurrentWord(
                null);

        room.getWordChoices()
                .clear();

        room.getRevealedHintIndexes()
                .clear();

        room.getCorrectGuesserIds()
                .clear();

        room.setTurnStartTime(
                0);

        room.setTurnEndTime(
                0);
room.setWordSelectionEndTime(0);
        broadcastCanvasClear(
                room);

        /*
         * =========================
         * NO ELIGIBLE PLAYERS
         * =========================
         */

        if (room.getEligiblePlayerIds()
                .isEmpty()) {

            room.setCurrentDrawerId(
                    null);

            room.setTurnPhase(
                    null);

            saveRoom(room);
            broadcastRoomUpdate(
                    room);

            return;
        }

        int currentIndex = room.getCurrentTurnIndex();

        /*
         * =========================
         * END OF ROUND
         * =========================
         */

        if (currentIndex >= room.getEligiblePlayerIds()
                .size()) {

            room.setCurrentDrawerId(
                    null);

            room.setTurnPhase(
                    null);

            handleRoundComplete(
                    room);

            return;
        }
        /*
         * =========================
         * NEXT DRAWER
         * =========================
         */

        String nextDrawerId = room.getEligiblePlayerIds()
                .get(currentIndex);

        room.setCurrentDrawerId(
                nextDrawerId);

        room.setTurnPhase(
                TurnPhase.WORD_SELECTION);

        /*
         * =========================
         * NEW WORD CHOICES
         * =========================
         */

        List<String> nextWords = wordService.getNextWords(
                room.getAvailableWords(),
                room.getLastWordChoices());

        room.setWordChoices(
                nextWords);

        room.setLastWordChoices(
                new ArrayList<>(
                        nextWords));

        /*
         * =========================
         * NOTIFY DRAWER
         * =========================
         */

        room.setWordSelectionEndTime(0);
        broadcastDrawerNotification(
                room);

        /*
         * =========================
         * START WORD TIMER
         * =========================
         */

        startWordSelectionTimer(
                room.getRoomId());

        /*
         * =========================
         * ROOM UPDATE
         * =========================
         */

        saveRoom(room);
        broadcastRoomUpdate(
                room);
    }

    public Room playAgain(String roomId, PlayAgainRequest request) {

        String normalizedRoomId = requireNormalizedRoomId(roomId);

        requireRoom(normalizedRoomId);

        synchronized (getRoomLock(normalizedRoomId)) {
            Room room = rooms.get(normalizedRoomId);

            if (room == null) {
                throw new RuntimeException("Room not found");
            }

            if (!room.getHostPlayerId().equals(request.getPlayerId())) {
                throw new RuntimeException("Only the host can restart the game");
            }

            if (room.getGameState() != GameState.GAME_FINISHED) {
                throw new RuntimeException("Game is not finished");
            }

            for (Player player : room.getPlayers()) {
                player.setScore(0);
                player.setTeam(null);
            }

            room.setGameState(GameState.LOBBY);
            room.setCurrentRound(0);

            room.setCountdownEndTime(0);
            room.setWordSelectionEndTime(0);

            room.setCurrentDrawerId(null);
            room.setCurrentTurnIndex(0);

            room.getEligiblePlayerIds().clear();
            room.getCorrectGuesserIds().clear();
            // room.setAvailableWords(wordService.createShuffledWordPool());
            // room.getLastWordChoices().clear();
            room.getSidePanelMessages().clear();

            broadcastSidePanelClear(room);

            room.setTurnPhase(null);

            room.getWordChoices().clear();
            room.setCurrentWord(null);
            room.getRevealedHintIndexes().clear();
            room.setTurnStartTime(0);
            room.setTurnEndTime(0);

            saveRoom(room);
            broadcastRoomUpdate(room);
            return room;
        }
    }

    public RoomResponse convertToRoomResponse(Room room) {

        synchronized (requireRoomLock(room.getRoomId())) {
            RoomResponse response = new RoomResponse();

            response.setRoomId(room.getRoomId());
            response.setHostPlayerId(room.getHostPlayerId());

            response.setMaxPlayers(room.getMaxPlayers());
            response.setDrawTimeSeconds(room.getDrawTimeSeconds());
            response.setGameMode(room.getGameMode());
            response.setGameState(room.getGameState());

            response.setCountdownEndTime(room.getCountdownEndTime());

            response.setCurrentRound(room.getCurrentRound());
            response.setCurrentTurnIndex(room.getCurrentTurnIndex());
            response.setCurrentDrawerId(room.getCurrentDrawerId());

            response.setTurnPhase(room.getTurnPhase());

            response.setPlayers(copyPlayers(room));

            if (room.getTurnPhase() == TurnPhase.DRAWING && room.getCurrentWord() != null) {
                response.setWordPattern(buildWordPattern(room.getCurrentWord(), room.getRevealedHintIndexes()));
            } else {
                response.setWordPattern(null);
            }

            response.setCorrectGuessCount(
                    room.getCorrectGuesserIds()
                            .size());

            response.setCorrectGuesserIds(
                    new ArrayList<>(
                            room.getCorrectGuesserIds()));

            int eligibleGuesserCount = 0;

            if (room.getCurrentDrawerId() != null) {
                eligibleGuesserCount = (int) room.getPlayers()
                        .stream()
                        .filter(player -> !player.getPlayerId()
                                .equals(
                                        room.getCurrentDrawerId()))
                        .count();
            }

            response.setEligibleGuesserCount(eligibleGuesserCount);

            response.setTurnStartTime(room.getTurnStartTime());
            response.setTurnEndTime(room.getTurnEndTime());

            if (room.getGameMode() == GameMode.TEAM_BATTLE) {
                response.setRedTeamScore(calculateTeamScore(room, Team.RED));
                response.setBlueTeamScore(calculateTeamScore(room, Team.BLUE));
            } else {
                response.setRedTeamScore(0);
                response.setBlueTeamScore(0);
            }

            if (room.getGameMode() == GameMode.TEAM_BATTLE
                    && room.getGameState() == GameState.GAME_FINISHED) {

                int redScore = response.getRedTeamScore();
                int blueScore = response.getBlueTeamScore();

                if (redScore > blueScore) {
                    response.setWinningTeam(Team.RED);
                } else if (blueScore > redScore) {
                    response.setWinningTeam(Team.BLUE);
                } else {
                    response.setWinningTeam(null);
                }
            } else {
                response.setWinningTeam(null);
            }

            response.setLeaderboard(buildLeaderboard(room));
            return response;
        }
    }

    public DrawerTurnResponse getDrawerTurn(String roomId, String playerId) {
        String normalizedRoomId = requireNormalizedRoomId(roomId);

        requireRoom(normalizedRoomId);

        synchronized (getRoomLock(normalizedRoomId)) {
            Room room = rooms.get(normalizedRoomId);

            if (room.getCurrentDrawerId() == null || !room.getCurrentDrawerId().equals(playerId)) {
                throw new RuntimeException("Only the current drawer can access drawer turn data");
            }

            return convertToDrawerTurnResponse(room);
        }
    }

    public DrawerTurnResponse convertToDrawerTurnResponse(Room room) {

        synchronized (requireRoomLock(room.getRoomId())) {
            DrawerTurnResponse response = new DrawerTurnResponse();

            response.setCurrentDrawerId(room.getCurrentDrawerId());
            response.setTurnPhase(room.getTurnPhase());
            response.setWordChoices(new ArrayList<>(room.getWordChoices()));
            response.setCurrentWord(room.getCurrentWord());
            response.setTurnStartTime(room.getTurnStartTime());
            response.setTurnEndTime(room.getTurnEndTime());
            return response;
        }
    }

    private void broadcastRoomUpdate(Room room) {

        synchronized (requireRoomLock(room.getRoomId())) {

            RoomResponse response = convertToRoomResponse(room);
            messagingTemplate.convertAndSend(
                    "/topic/rooms/" + room.getRoomId(),
                    response);
        }
    }

    //
    public void handleDrawing(DrawingMessage message) {
        String roomId = requireNormalizedRoomId(message.getRoomId());

        requireRoom(roomId);

        synchronized (getRoomLock(roomId)) {
            Room room = rooms.get(roomId);

            if (room.getTurnPhase() != TurnPhase.DRAWING) {
                throw new RuntimeException("Drawing is not currently active");
            }

            if (room.getCurrentDrawerId() == null || !room.getCurrentDrawerId().equals(message.getPlayerId())) {
                throw new RuntimeException("Only the current drawer can draw");
            }

            broadcastDrawing(room.getRoomId(), message);
        }
    }

    //
    private void broadcastDrawing(String roomId, DrawingMessage message) {

        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/drawing", message);
    }

    public void clearCanvas(String roomId, String playerId) {
        String normalizedRoomId = requireNormalizedRoomId(roomId);

        requireRoom(normalizedRoomId);

        synchronized (getRoomLock(normalizedRoomId)) {
            Room room = rooms.get(normalizedRoomId);

            if (room.getTurnPhase() != TurnPhase.DRAWING) {
                throw new RuntimeException("Drawing is not currently active");
            }

            if (room.getCurrentDrawerId() == null || !room.getCurrentDrawerId().equals(playerId)) {
                throw new RuntimeException("Only the current drawer can clear the canvas");
            }

            broadcastCanvasClear(room);
        }
    }

    private void broadcastCanvasClear(Room room) {

        CanvasEvent event = new CanvasEvent("CLEAR");
        messagingTemplate.convertAndSend("/topic/rooms/" + room.getRoomId() + "/canvas", event);
    }

    //
    public void handleGuess(GuessMessage message) {

        String roomId = requireNormalizedRoomId(message.getRoomId());

       requireRoom(roomId);

        synchronized (getRoomLock(roomId)) {
            Room room = rooms.get(roomId);

            /*
             * =========================
             * ROOM / TURN VALIDATION
             * =========================
             */

            if (room == null) {

                throw new RuntimeException(
                        "Room not found");
            }

            if (room.getTurnPhase() != TurnPhase.DRAWING) {

                throw new RuntimeException(
                        "Guessing is not currently active");
            }

            if (room.getCurrentDrawerId() == null) {

                throw new RuntimeException(
                        "Current drawer not found");
            }

            /*
             * =========================
             * GUESS VALIDATION
             * =========================
             */

            if (message.getGuess() == null
                    ||
                    message.getGuess()
                            .isBlank()) {

                throw new RuntimeException(
                        "Guess cannot be blank");
            }

            String playerId = message.getPlayerId();

            if (playerId == null
                    ||
                    playerId.isBlank()) {

                throw new RuntimeException(
                        "Player ID is required");
            }

            if (room.getCurrentDrawerId()
                    .equals(playerId)) {

                throw new RuntimeException(
                        "Drawer cannot guess");
            }

            Player guessingPlayer = room.getPlayers()
                    .stream()
                    .filter(
                            player -> player.getPlayerId()
                                    .equals(playerId))
                    .findFirst()
                    .orElseThrow(
                            () -> new RuntimeException(
                                    "Player not found in room"));

            String normalizedGuess = message.getGuess()
                    .trim();

            String currentWord = room.getCurrentWord();

            if (currentWord == null
                    ||
                    currentWord.isBlank()) {

                throw new RuntimeException(
                        "No active word");
            }

            /*
             * =========================
             * CHECK ANSWER
             * =========================
             */

            boolean correct = currentWord
                    .equalsIgnoreCase(
                            normalizedGuess);

            if (correct
                    &&
                    room.getCorrectGuesserIds()
                            .contains(playerId)) {

                return;
            }

            /*
             * =========================
             * SIDE PANEL MESSAGE
             * =========================
             */

            SidePanelMessage sidePanelMessage;

            if (correct) {

                sidePanelMessage = new SidePanelMessage(
                        guessingPlayer
                                .getPlayerId(),

                        guessingPlayer
                                .getName(),

                        null,

                        true,

                        System.currentTimeMillis());

            } else {

                sidePanelMessage = new SidePanelMessage(
                        guessingPlayer
                                .getPlayerId(),

                        guessingPlayer
                                .getName(),

                        normalizedGuess,

                        false,

                        System.currentTimeMillis());
            }

            room.getSidePanelMessages()
                    .add(
                            sidePanelMessage);

            /*
             * =========================
             * SCORING
             * =========================
             */

            if (correct) {

                room.getCorrectGuesserIds()
                        .add(playerId);

                int guesserPoints = calculateGuesserScore(
                        room);

                guessingPlayer.setScore(
                        guessingPlayer.getScore()
                                + guesserPoints);

                Player drawer = room.getPlayers()
                        .stream()
                        .filter(
                                player -> player.getPlayerId()
                                        .equals(
                                                room.getCurrentDrawerId()))
                        .findFirst()
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Drawer not found"));

                int drawerPoints = calculateDrawerScore(
                        room);

                drawer.setScore(
                        drawer.getScore()
                                + drawerPoints);
            }

            /*
             * =========================
             * BROADCAST GUESS
             * =========================
             */
            saveRoom(room);

            GuessResult result;

            if (correct) {

                result = new GuessResult(
                        playerId,
                        null,
                        true);

            } else {

                result = new GuessResult(
                        playerId,
                        normalizedGuess,
                        false);
            }

            broadcastGuess(
                    room.getRoomId(),
                    result);

            /*
             * =========================
             * CORRECT GUESS UPDATE
             * =========================
             */

            if (correct) {

                broadcastRoomUpdate(
                        room);

                if (haveAllGuessersGuessedCorrectly(
                        room)) {

                    advanceToNextTurn(
                            room);
                }
            }
        }
    }

    private void broadcastGuess(String roomId, GuessResult result) {
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/guesses", result);
    }

    private int calculateGuesserScore(Room room) {

        long now = System.currentTimeMillis();
        long remainingTime = room.getTurnEndTime() - now;
        long remainingSeconds = Math.max(0, remainingTime / 1000);

        return 100 + (int) (remainingSeconds * 5);
    }

    private int calculateDrawerScore(Room room) {

        long now = System.currentTimeMillis();
        long remainingTime = room.getTurnEndTime() - now;
        long remainingSeconds = Math.max(0, remainingTime / 1000);

        return 20 + (int) (remainingSeconds * 2);
    }

    private boolean haveAllGuessersGuessedCorrectly(Room room) {

        long eligibleGuessers = room.getPlayers()
                .stream()
                .filter(player -> !player.getPlayerId()
                        .equals(room.getCurrentDrawerId()))
                .count();

        return eligibleGuessers > 0 && room.getCorrectGuesserIds().size() >= eligibleGuessers;
    }

    public List<SidePanelMessage> getSidePanelMessages(String roomId) {

        String normalizedRoomId = requireNormalizedRoomId(roomId);
        requireRoom(normalizedRoomId);
        synchronized (getRoomLock(normalizedRoomId)) {
            Room room = rooms.get(normalizedRoomId);
            return new ArrayList<>(room.getSidePanelMessages());
        }
    }

    public boolean playerExistsInRoom(String roomId, String playerId) {
        String normalizedRoomId = normalizeRoomId(roomId);

        if (normalizedRoomId == null || normalizedRoomId.isBlank()) {
            return false;
        }

        Room existingRoom = getRoomFromStorage(normalizedRoomId);
        if (existingRoom == null) {
            return false;
        }

        synchronized (getRoomLock(normalizedRoomId)) {
            Room room = rooms.get(normalizedRoomId);

            return room.getPlayers()
                    .stream()
                    .anyMatch(player -> player.getPlayerId().equals(playerId));
        }
    }

    //
    private void broadcastSidePanelClear(Room room) {

        SidePanelEvent event = new SidePanelEvent("CLEAR");
        messagingTemplate.convertAndSend("/topic/rooms/" + room.getRoomId() + "/messages-control", event);
    }

    private void assignTeams(Room room) {

        if (room.getGameMode() != GameMode.TEAM_BATTLE) {
            return;
        }
        List<Player> shuffledPlayers = new ArrayList<>(room.getPlayers());
        Collections.shuffle(shuffledPlayers);

        for (int i = 0; i < shuffledPlayers.size(); i++) {
            Player player = shuffledPlayers.get(i);
            if (i % 2 == 0) {
                player.setTeam(Team.RED);
            } else {
                player.setTeam(Team.BLUE);
            }
        }
    }

    private int calculateTeamScore(Room room, Team team) {

        return room.getPlayers()
                .stream()
                .filter(player -> player.getTeam() == team)
                .mapToInt(player -> player.getScore())
                .sum();
    }

    private void assignTeamToJoiningPlayer(Room room, Player player) {

        if (room.getGameMode() != GameMode.TEAM_BATTLE) {
            return;
        }

        long redCount = room.getPlayers()
                .stream()
                .filter(existing -> existing.getTeam() == Team.RED)
                .count();

        long blueCount = room.getPlayers()
                .stream()
                .filter(existing -> existing.getTeam() == Team.BLUE)
                .count();

        if (redCount < blueCount) {
            player.setTeam(Team.RED);
        } else if (blueCount < redCount) {
            player.setTeam(Team.BLUE);
        } else {
            if (Math.random() < 0.5) {
                player.setTeam(Team.RED);
            } else {
                player.setTeam(Team.BLUE);
            }
        }
    }

    private List<LeaderboardEntry> buildLeaderboard(Room room) {

        List<Player> sortedPlayers = new ArrayList<>(room.getPlayers());
        sortedPlayers.sort(Comparator.comparingInt((Player player) -> player.getScore()).reversed());

        List<LeaderboardEntry> leaderboard = new ArrayList<>();

        int rank = 0;
        int previousScore = -1;

        for (int i = 0; i < sortedPlayers.size(); i++) {
            Player player = sortedPlayers.get(i);

            if (i == 0 || player.getScore() != previousScore) {
                rank = i + 1;
            }

            LeaderboardEntry entry = new LeaderboardEntry(
                    rank,
                    player.getPlayerId(),
                    player.getName(),
                    player.getScore(),
                    player.getTeam());

            leaderboard.add(entry);
            previousScore = player.getScore();
        }
        return leaderboard;
    }

    private String buildWordPattern(String word, List<Integer> revealedIndexes) {

        if (word == null
                ||
                word.isBlank()) {
            return null;
        }

        StringBuilder pattern = new StringBuilder();

        for (int i = 0; i < word.length(); i++) {

            char ch = word.charAt(i);

            if (Character.isLetterOrDigit(ch)) {

                if (revealedIndexes != null
                        &&
                        revealedIndexes.contains(i)) {

                    pattern.append(
                            Character.toUpperCase(ch));

                } else {

                    pattern.append("_");
                }

            } else {

                pattern.append(ch);
            }

            if (i < word.length() - 1) {

                pattern.append(" ");
            }
        }

        return pattern.toString();
    }

    private DrawerNotification createDrawerNotification(Room room) {

        return new DrawerNotification(
                room.getCurrentDrawerId(),
                room.getTurnPhase(),
                new ArrayList<>(room.getWordChoices()),
                room.getCurrentWord(),
                room.getTurnStartTime(),
                room.getTurnEndTime());
    }

    private void broadcastDrawerNotification(Room room) {

        if (room.getCurrentDrawerId() == null) {
            return;
        }
        DrawerNotification notification = createDrawerNotification(room);

        eventPublisher.publishEvent(
                new DrawerPrivateEvent(
                        room.getRoomId(),
                        room.getCurrentDrawerId(),
                        notification));

    }

    private Object getRoomLock(String roomId) {

        return roomLocks.computeIfAbsent(
                roomId,
                key -> new Object());
    }

    private Object findRoomLock(String roomId) {

        return roomLocks.get(roomId);
    }

    private Object requireRoomLock(String roomId) {

        Object lock = findRoomLock(roomId);
        if (lock == null) {
            throw new RuntimeException("Room not found with ID: " + roomId);
        }
        return lock;
    }

    private Player copyPlayer(Player original) {

        Player copy = new Player();

        copy.setPlayerId(original.getPlayerId());
        copy.setName(original.getName());
        copy.setAvatar(original.getAvatar());
        copy.setScore(original.getScore());
        copy.setTeam(original.getTeam());
        return copy;
    }

    private List<Player> copyPlayers(Room room) {

        List<Player> copies = new ArrayList<>();
        for (Player player : room.getPlayers()) {
            copies.add(copyPlayer(player));
        }
        return copies;
    }

    private String normalizeRoomId(String roomId) {

        if (roomId == null) {
            return null;
        }
        return roomId.trim().toUpperCase();
    }

    private String requireNormalizedRoomId(String roomId) {

        String normalizedRoomId = normalizeRoomId(roomId);

        if (normalizedRoomId == null || normalizedRoomId.isBlank()) {
            throw new RuntimeException("Room ID is required");
        }
        return normalizedRoomId;
    }

    @PreDestroy
    public void shutdownScheduler() {

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

    private String generateRoomId() {

        return UUID.randomUUID()
                .toString()
                .substring(0, 6)
                .toUpperCase();
    }

    private String generateUniqueRoomId() {

        String roomId;
        do {
            roomId = generateRoomId();
        } while (rooms.containsKey(roomId) || roomRedisRepository.exists(roomId));

        return roomId;
    }

    private void revealHint(Room room) {

        if (room == null || room.getTurnPhase() != TurnPhase.DRAWING) {
            return;
        }

        String word = room.getCurrentWord();

        if (word == null || word.isBlank()) {
            return;
        }

        List<Integer> hiddenIndexes = new ArrayList<>();

        for (int i = 0; i < word.length(); i++) {

            char character = word.charAt(i);

            if (Character.isLetterOrDigit(
                    character)
                    &&
                    !room
                            .getRevealedHintIndexes()
                            .contains(i)) {

                hiddenIndexes.add(i);
            }
        }

        if (hiddenIndexes.size() <= 2) {
            return;
        }

        Collections.shuffle(
                hiddenIndexes);

        int revealIndex = hiddenIndexes.get(0);

        room.getRevealedHintIndexes()
                .add(revealIndex);

        saveRoom(room);
        broadcastRoomUpdate(
                room);
    }

    private void startHintTimers(Room room) {
    String roomId = room.getRoomId();
    String expectedDrawerId = room.getCurrentDrawerId();
    long expectedStartTime = room.getTurnStartTime();
    int expectedRound = room.getCurrentRound();

    long drawMillis = room.getDrawTimeSeconds() * 1000L;
    long firstHintTime = expectedStartTime + Math.round(drawMillis / 3.0);
    long secondHintTime = expectedStartTime + Math.round((drawMillis * 2) / 3.0);
    long now = System.currentTimeMillis();

    int revealedCount = room.getRevealedHintIndexes().size();

    if (revealedCount < 1) {
        long delay = Math.max(0, firstHintTime - now);

        scheduler.schedule(() -> revealScheduledHint(
                        roomId,
                        expectedDrawerId,
                        expectedStartTime,
                        expectedRound),
                delay,
                TimeUnit.MILLISECONDS
        );
    }

    if (revealedCount < 2) {
        long delay = Math.max(0, secondHintTime - now);

        scheduler.schedule(() -> revealScheduledHint(
                        roomId,
                        expectedDrawerId,
                        expectedStartTime,
                        expectedRound),
                delay,
                TimeUnit.MILLISECONDS
        );
    }
}

    private void revealScheduledHint(
            String roomId,
            String expectedDrawerId,
            long expectedStartTime,
            int expectedRound) {

        Room existingRoom = getRoomFromStorage(roomId);

        if (existingRoom == null) {
            return;
        }

        synchronized (getRoomLock(roomId)) {
            Room room = rooms.get(roomId);

            if (room == null) {
                return;
            }

            if (room.getTurnPhase() != TurnPhase.DRAWING) {
                return;
            }

            if (room.getCurrentDrawerId() == null
                    || !room.getCurrentDrawerId().equals(expectedDrawerId)) {
                return;
            }

            if (room.getTurnStartTime() != expectedStartTime) {
                return;
            }

            if (room.getCurrentRound() != expectedRound) {
                return;
            }

            revealHint(room);
        }
    }

    private void broadcastTurnEnd(Room room, String finishedWord) {

        Map<String, Object> event = new HashMap<>();

        event.put(
                "type",
                "TURN_END");

        event.put(
                "word",
                finishedWord);

        event.put(
                "roomId",
                room.getRoomId());

        messagingTemplate.convertAndSend(
                "/topic/rooms/"
                        + room.getRoomId()
                        + "/turn-end",
                (Object) event);

    }

    private void continueAfterTurnReveal(String roomId) {
        Room room = getRoomFromStorage(roomId);

        if (room == null) {
            return;
        }

        synchronized (getRoomLock(roomId)) {
            room = rooms.get(roomId);

            if (room == null) {
                return;
            }

            int nextIndex = room.getCurrentTurnIndex() + 1;
            room.setCurrentTurnIndex(nextIndex);

            if (nextIndex < room.getEligiblePlayerIds().size()) {
                broadcastCanvasClear(room);

                String nextDrawerId = room.getEligiblePlayerIds().get(nextIndex);
                room.setCurrentDrawerId(nextDrawerId);
                room.setTurnPhase(TurnPhase.WORD_SELECTION);

                List<String> nextWords = wordService.getNextWords(
                        room.getAvailableWords(),
                        room.getLastWordChoices());

                room.setWordChoices(nextWords);
                room.setLastWordChoices(new ArrayList<>(nextWords));

                room.setWordSelectionEndTime(0);
                broadcastDrawerNotification(room);
                startWordSelectionTimer(room.getRoomId());

                saveRoom(room);
                broadcastRoomUpdate(room);
                return;
            }

            handleRoundComplete(room);
        }
    }

    private void broadcastRoundComplete(Room room, int completedRound) {

        Map<String, Object> event = new HashMap<>();

        event.put("type", "ROUND_COMPLETE");

        event.put("round", completedRound);

        event.put("roomId", room.getRoomId());

        messagingTemplate.convertAndSend(
                "/topic/rooms/"
                        + room.getRoomId()
                        + "/round-complete",
                (Object) event);
    }

    private void resetRoomToLobby(Room room) {
        room.setGameState(GameState.LOBBY);
        room.setCurrentRound(0);
        room.setCurrentDrawerId(null);
        room.setCurrentTurnIndex(0);
        room.setTurnPhase(null);
        room.setCurrentWord(null);

        room.getWordChoices().clear();
        room.getCorrectGuesserIds().clear();
        room.getEligiblePlayerIds().clear();
        room.getRevealedHintIndexes().clear();

        room.setTurnStartTime(0);
        room.setTurnEndTime(0);
        room.setCountdownEndTime(0);
        room.setWordSelectionEndTime(0);

        for (Player player : room.getPlayers()) {
            player.setScore(0);
        }

        saveRoom(room);
        broadcastCanvasClear(room);
        broadcastRoomUpdate(room);
    }

    private void saveRoom(Room room) {
        if (room != null) {
            roomRedisRepository.save(room);
        }
    }

    private Room getRoomFromStorage(String roomId) {
    Room room = rooms.get(roomId);

    if (room != null) {
        return room;
    }

    room = roomRedisRepository.findById(roomId).orElse(null);

    if (room != null) {
        rooms.putIfAbsent(roomId, room);
        roomLocks.putIfAbsent(roomId, new Object());

        Room restoredRoom = rooms.get(roomId);
        recoverRoomTimers(restoredRoom);
        return restoredRoom;
    }
    return null;
}


    private void recoverRoomTimers(Room room) {
    if (room == null) {
        return;
    }

    if (room.getGameState() == GameState.STARTING) {
        startCountdown(room.getRoomId());
        return;
    }

    if (room.getTurnPhase() == TurnPhase.WORD_SELECTION) {
        startWordSelectionTimer(room.getRoomId());
        return;
    }

    if (room.getTurnPhase() == TurnPhase.DRAWING) {
        startHintTimers(room);
        startDrawingTimer(room);
        return;
    }

    boolean activeRound =
            room.getGameState() == GameState.ROUND_1
            || room.getGameState() == GameState.ROUND_2
            || room.getGameState() == GameState.ROUND_3;

    if (activeRound && room.getTurnPhase() == null) {
        scheduler.schedule(
                () -> continueAfterTurnReveal(room.getRoomId()),
                0,
                TimeUnit.MILLISECONDS
        );
    }
}

private Room requireRoom(String roomId) {
    Room room = getRoomFromStorage(roomId);
    if (room == null) {
        throw new RuntimeException("Room not found with ID: " + roomId);
    }
    return room;
}
}
