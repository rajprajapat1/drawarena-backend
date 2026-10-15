package com.drawarena.drawarena_backend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.event.ApplicationEvents;

import com.drawarena.drawarena_backend.dto.CreateRoomRequest;
import com.drawarena.drawarena_backend.dto.DrawerNotification;
import com.drawarena.drawarena_backend.dto.DrawerTurnResponse;
import com.drawarena.drawarena_backend.dto.DrawingMessage;
import com.drawarena.drawarena_backend.dto.GuessMessage;
import com.drawarena.drawarena_backend.dto.GuessResult;
import com.drawarena.drawarena_backend.dto.JoinRoomRequest;
import com.drawarena.drawarena_backend.dto.PlayAgainRequest;
import com.drawarena.drawarena_backend.dto.RoomResponse;
import com.drawarena.drawarena_backend.dto.SelectWordRequest;
import com.drawarena.drawarena_backend.dto.SidePanelMessage;
import com.drawarena.drawarena_backend.dto.StartGameRequest;
import com.drawarena.drawarena_backend.event.DrawerPrivateEvent;
import com.drawarena.drawarena_backend.model.GameMode;
import com.drawarena.drawarena_backend.model.GameState;
import com.drawarena.drawarena_backend.model.Player;
import com.drawarena.drawarena_backend.model.Room;
import com.drawarena.drawarena_backend.model.Team;
import com.drawarena.drawarena_backend.model.TurnPhase;

@SpringBootTest
@RecordApplicationEvents
class RoomServiceTest {

        @Autowired
        private RoomService roomService;

        @Autowired
        private ApplicationEvents applicationEvents;

        @Autowired
        private WordService wordService;

        @MockitoBean
        private SimpMessagingTemplate messagingTemplate;

        @Test
        void createRoomShouldCreateLobbyWithHost() {

                CreateRoomRequest request = new CreateRoomRequest();

                request.setName("Raj");
                request.setMaxPlayers(5);
                request.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(request);

                assertNotNull(room);
                assertNotNull(room.getRoomId());
                assertEquals(6, room.getRoomId().length());
                assertEquals(GameState.LOBBY, room.getGameState());
                assertEquals(1, room.getPlayers().size());
                assertEquals("Raj", room.getPlayers().get(0).getName());
                assertEquals(room.getPlayers().get(0).getPlayerId(),
                                room.getHostPlayerId());
        }

        @Test
        void joinRoomShouldRejectDuplicateNameIgnoringCase() {

                CreateRoomRequest createRequest = new CreateRoomRequest();

                createRequest.setName("Raj");
                createRequest.setMaxPlayers(5);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();

                joinRequest.setName("raj");

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.joinRoom(
                                                room.getRoomId(),
                                                joinRequest));

                assertEquals(
                                "Player name already exists in the room",
                                exception.getMessage());
        }

        @Test
        void joinRoomShouldRejectPlayerWhenRoomIsFull() {

                CreateRoomRequest createRequest = new CreateRoomRequest();

                createRequest.setName("Raj");
                createRequest.setMaxPlayers(2);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest secondPlayer = new JoinRoomRequest();

                secondPlayer.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                secondPlayer);

                JoinRoomRequest thirdPlayer = new JoinRoomRequest();

                thirdPlayer.setName("Aman");

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.joinRoom(
                                                room.getRoomId(),
                                                thirdPlayer));

                assertEquals("Room is full", exception.getMessage());
        }

        @Test
        void startGameShouldRejectNonHost() {

                CreateRoomRequest createRequest = new CreateRoomRequest();

                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();

                joinRequest.setName("Rahul");

                Room updatedRoom = roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                String nonHostPlayerId = updatedRoom.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(
                                                                updatedRoom.getHostPlayerId()))
                                .findFirst()
                                .orElseThrow()
                                .getPlayerId();

                StartGameRequest startRequest = new StartGameRequest();

                startRequest.setPlayerId(nonHostPlayerId);

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.startGame(
                                                room.getRoomId(),
                                                startRequest));

                assertEquals("Only the host can start the game", exception.getMessage());
        }

        @Test
        void startGameShouldRequireAtLeastTwoPlayers() {

                CreateRoomRequest createRequest = new CreateRoomRequest();

                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                StartGameRequest startRequest = new StartGameRequest();

                startRequest.setPlayerId(room.getHostPlayerId());

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.startGame(
                                                room.getRoomId(),
                                                startRequest));

                assertEquals("At least 2 players are required to start", exception.getMessage());
        }

        @Test
        void startGameShouldMoveRoomToStartingState() {

                CreateRoomRequest createRequest = new CreateRoomRequest();

                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();

                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                StartGameRequest startRequest = new StartGameRequest();

                startRequest.setPlayerId(room.getHostPlayerId());

                Room startedRoom = roomService.startGame(
                                room.getRoomId(),
                                startRequest);

                assertEquals(GameState.STARTING, startedRoom.getGameState());
        }

        @Test
        void startGameShouldCreateTenSecondCountdown() {

                CreateRoomRequest createRequest = new CreateRoomRequest();

                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();

                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                StartGameRequest startRequest = new StartGameRequest();

                startRequest.setPlayerId(room.getHostPlayerId());

                long beforeStart = System.currentTimeMillis();

                Room startedRoom = roomService.startGame(room.getRoomId(), startRequest);

                long countdownDuration = startedRoom.getCountdownEndTime() - beforeStart;

                assertTrue(countdownDuration >= 9_000 && countdownDuration <= 11_000);
        }

        @Test
        void initializeRoundOneShouldPrepareFirstDrawer() {

                CreateRoomRequest createRequest = new CreateRoomRequest();

                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();

                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                assertEquals(GameState.ROUND_1, room.getGameState());

                assertEquals(1, room.getCurrentRound());

                assertEquals(2, room.getEligiblePlayerIds().size());

                assertEquals(0, room.getCurrentTurnIndex());

                assertEquals(room.getEligiblePlayerIds().get(0), room.getCurrentDrawerId());

                assertEquals(TurnPhase.WORD_SELECTION, room.getTurnPhase());

                assertEquals(3, room.getWordChoices().size());

                assertNull(room.getCurrentWord());
        }

        @Test
        void selectWordShouldRejectNonDrawer() {

                CreateRoomRequest createRequest = new CreateRoomRequest();

                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();

                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                String nonDrawerId = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(
                                                                room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow()
                                .getPlayerId();

                SelectWordRequest request = new SelectWordRequest();
                request.setPlayerId(nonDrawerId);

                request.setWord(room.getWordChoices().get(0));

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.selectWord(
                                                room.getRoomId(),
                                                request));

                assertEquals("Only the current drawer can select a word", exception.getMessage());
        }

        @Test
        void selectWordShouldStartDrawingPhase() {

                CreateRoomRequest createRequest = new CreateRoomRequest();

                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();

                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest request = new SelectWordRequest();

                request.setPlayerId(room.getCurrentDrawerId());
                request.setWord(selectedWord);

                Room updatedRoom = roomService.selectWord(room.getRoomId(), request);

                assertEquals(TurnPhase.DRAWING, updatedRoom.getTurnPhase());
                assertEquals(selectedWord, updatedRoom.getCurrentWord());

                assertTrue(updatedRoom.getTurnStartTime() > 0);

                assertEquals(60_000, updatedRoom.getTurnEndTime() - updatedRoom.getTurnStartTime());
        }

        @Test
        void roomResponseShouldExposeWordPatternDuringDrawing() {

                CreateRoomRequest createRequest = new CreateRoomRequest();

                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);
                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest request = new SelectWordRequest();

                request.setPlayerId(room.getCurrentDrawerId());
                request.setWord(selectedWord);

                roomService.selectWord(room.getRoomId(), request);
                RoomResponse response = roomService.convertToRoomResponse(room);

                assertNotNull(response.getWordPattern());
                assertFalse(response.getWordPattern().isBlank());
        }

        @Test
        void handleGuessShouldRejectCurrentDrawer() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);
                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();
                selectRequest.setPlayerId(room.getCurrentDrawerId());
                selectRequest.setWord(selectedWord);

                roomService.selectWord(room.getRoomId(), selectRequest);

                GuessMessage guessMessage = new GuessMessage();
                guessMessage.setRoomId(room.getRoomId());
                guessMessage.setPlayerId(room.getCurrentDrawerId());
                guessMessage.setGuess(selectedWord);

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.handleGuess(guessMessage));

                assertEquals("Drawer cannot guess", exception.getMessage());
        }

        @Test
        void correctGuessShouldIncreaseGuesserAndDrawerScores() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);
                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();
                selectRequest.setPlayerId(room.getCurrentDrawerId());
                selectRequest.setWord(selectedWord);

                roomService.selectWord(room.getRoomId(), selectRequest);

                Player drawer = room.getPlayers()
                                .stream()
                                .filter(player -> player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                int drawerScoreBefore = drawer.getScore();
                int guesserScoreBefore = guesser.getScore();

                GuessMessage guessMessage = new GuessMessage();
                guessMessage.setRoomId(room.getRoomId());
                guessMessage.setPlayerId(guesser.getPlayerId());
                guessMessage.setGuess(selectedWord);

                roomService.handleGuess(guessMessage);

                assertTrue(guesser.getScore() > guesserScoreBefore);
                assertTrue(drawer.getScore() > drawerScoreBefore);
        }

        @Test
        void duplicateCorrectGuessShouldNotScoreTwice() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest1 = new JoinRoomRequest();
                joinRequest1.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest1);

                JoinRoomRequest joinRequest2 = new JoinRoomRequest();
                joinRequest2.setName("Aman");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest2);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();
                selectRequest.setPlayerId(room.getCurrentDrawerId());
                selectRequest.setWord(selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                GuessMessage guessMessage = new GuessMessage();
                guessMessage.setRoomId(room.getRoomId());
                guessMessage.setPlayerId(guesser.getPlayerId());
                guessMessage.setGuess(selectedWord);

                roomService.handleGuess(guessMessage);

                int scoreAfterFirstGuess = guesser.getScore();

                roomService.handleGuess(guessMessage);

                assertEquals(
                                scoreAfterFirstGuess,
                                guesser.getScore());

                assertEquals(
                                1,
                                room.getCorrectGuesserIds().size());

                assertEquals(
                                TurnPhase.DRAWING,
                                room.getTurnPhase());
        }

        @Test
        void wrongGuessShouldNotIncreaseScore() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);
                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();
                selectRequest.setPlayerId(room.getCurrentDrawerId());
                selectRequest.setWord(selectedWord);

                roomService.selectWord(room.getRoomId(), selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                int scoreBefore = guesser.getScore();

                GuessMessage guessMessage = new GuessMessage();
                guessMessage.setRoomId(room.getRoomId());
                guessMessage.setPlayerId(guesser.getPlayerId());
                guessMessage.setGuess("definitely-wrong-answer");

                roomService.handleGuess(guessMessage);

                assertEquals(scoreBefore, guesser.getScore());
        }

        @Test
        void allGuessersCorrectShouldEndTurnEarly() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                String firstDrawerId = room.getCurrentDrawerId();
                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();
                selectRequest.setPlayerId(firstDrawerId);
                selectRequest.setWord(selectedWord);

                roomService.selectWord(room.getRoomId(), selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId().equals(firstDrawerId))
                                .findFirst()
                                .orElseThrow();

                GuessMessage guessMessage = new GuessMessage();
                guessMessage.setRoomId(room.getRoomId());
                guessMessage.setPlayerId(guesser.getPlayerId());
                guessMessage.setGuess(selectedWord);

                roomService.handleGuess(guessMessage);

                assertNotEquals(firstDrawerId, room.getCurrentDrawerId());

                assertEquals(TurnPhase.WORD_SELECTION, room.getTurnPhase());
        }

        @Test
        void wrongGuessShouldBeStoredInSidePanelHistory() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();
                selectRequest.setPlayerId(room.getCurrentDrawerId());
                selectRequest.setWord(selectedWord);

                roomService.selectWord(room.getRoomId(), selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                GuessMessage guessMessage = new GuessMessage();
                guessMessage.setRoomId(room.getRoomId());
                guessMessage.setPlayerId(guesser.getPlayerId());
                guessMessage.setGuess("wrong-answer");

                roomService.handleGuess(guessMessage);

                List<SidePanelMessage> messages = roomService.getSidePanelMessages(room.getRoomId());

                assertEquals(1, messages.size());
                assertEquals("wrong-answer", messages.get(0).getMessage());

                assertFalse(messages.get(0).isCorrect());
        }

        @Test
        void correctGuessShouldNotStoreSecretWordInSidePanel() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();
                selectRequest.setPlayerId(room.getCurrentDrawerId());
                selectRequest.setWord(selectedWord);

                roomService.selectWord(room.getRoomId(), selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                GuessMessage guessMessage = new GuessMessage();
                guessMessage.setRoomId(room.getRoomId());
                guessMessage.setPlayerId(guesser.getPlayerId());
                guessMessage.setGuess(selectedWord);

                roomService.handleGuess(guessMessage);

                List<SidePanelMessage> messages = roomService.getSidePanelMessages(room.getRoomId());

                assertEquals(1, messages.size());

                assertNull(messages.get(0).getMessage());

                assertTrue(messages.get(0).isCorrect());
        }

        @Test
        void sidePanelHistoryShouldPersistAcrossTurns() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();
                selectRequest.setPlayerId(room.getCurrentDrawerId());
                selectRequest.setWord(selectedWord);

                roomService.selectWord(room.getRoomId(), selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                GuessMessage guessMessage = new GuessMessage();
                guessMessage.setRoomId(room.getRoomId());
                guessMessage.setPlayerId(guesser.getPlayerId());
                guessMessage.setGuess(selectedWord);

                roomService.handleGuess(guessMessage);

                List<SidePanelMessage> messages = roomService.getSidePanelMessages(room.getRoomId());

                assertEquals(1, messages.size());

                assertEquals(TurnPhase.WORD_SELECTION, room.getTurnPhase());

                assertEquals(1, roomService.getSidePanelMessages(room.getRoomId()).size());
        }

        @Test
        void completingRoundOneShouldMoveToRoundTwo() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                completeCurrentTurn(room);
                completeCurrentTurn(room);

                assertEquals(
                                GameState.ROUND_2,
                                room.getGameState());

                assertEquals(
                                2,
                                room.getCurrentRound());

                assertEquals(
                                TurnPhase.WORD_SELECTION,
                                room.getTurnPhase());
        }

        private void completeCurrentTurn(Room room) {

                String selectedWord = room.getWordChoices().get(0);
                String drawerId = room.getCurrentDrawerId();

                SelectWordRequest selectRequest = new SelectWordRequest();
                selectRequest.setPlayerId(drawerId);
                selectRequest.setWord(selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId().equals(drawerId))
                                .findFirst()
                                .orElseThrow();

                GuessMessage guessMessage = new GuessMessage();
                guessMessage.setRoomId(room.getRoomId());
                guessMessage.setPlayerId(guesser.getPlayerId());
                guessMessage.setGuess(selectedWord);

                roomService.handleGuess(guessMessage);
        }

        private void completeFullGame(Room room) {

                // Round 1
                completeCurrentTurn(room);
                completeCurrentTurn(room);

                // Round 2
                completeCurrentTurn(room);
                completeCurrentTurn(room);

                // Round 3
                completeCurrentTurn(room);
                completeCurrentTurn(room);
        }

        @Test
        void roundTwoShouldStartWithDifferentDrawer() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String roundOneStartingDrawer = room.getCurrentDrawerId();

                completeCurrentTurn(room);
                completeCurrentTurn(room);

                assertEquals(
                                GameState.ROUND_2,
                                room.getGameState());

                assertNotEquals(
                                roundOneStartingDrawer,
                                room.getCurrentDrawerId());
        }

        @Test
        void scoresShouldCarryIntoNextRound() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                completeCurrentTurn(room);
                completeCurrentTurn(room);

                assertEquals(
                                GameState.ROUND_2,
                                room.getGameState());

                Player playerOne = room.getPlayers().get(0);

                Player playerTwo = room.getPlayers().get(1);

                assertTrue(
                                playerOne.getScore() > 0);

                assertTrue(
                                playerTwo.getScore() > 0);
        }

        @Test
        void completingRoundThreeShouldFinishGame() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                // Round 1
                completeCurrentTurn(room);
                completeCurrentTurn(room);

                // Round 2
                completeCurrentTurn(room);
                completeCurrentTurn(room);

                // Round 3
                completeCurrentTurn(room);
                completeCurrentTurn(room);

                assertEquals(
                                GameState.GAME_FINISHED,
                                room.getGameState());

                assertEquals(
                                3,
                                room.getCurrentRound());

                assertNull(
                                room.getCurrentDrawerId());

                assertNull(
                                room.getTurnPhase());

                assertNull(
                                room.getCurrentWord());

                assertEquals(
                                0,
                                room.getTurnStartTime());

                assertEquals(
                                0,
                                room.getTurnEndTime());
        }

        @Test
        void playAgainShouldRejectNonHost() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                completeFullGame(room);

                assertEquals(
                                GameState.GAME_FINISHED,
                                room.getGameState());

                Player nonHost = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getHostPlayerId()))
                                .findFirst()
                                .orElseThrow();

                PlayAgainRequest request = new PlayAgainRequest();

                request.setPlayerId(
                                nonHost.getPlayerId());

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.playAgain(
                                                room.getRoomId(),
                                                request));

                assertEquals(
                                "Only the host can restart the game",
                                exception.getMessage());
        }

        @Test
        void playAgainShouldRejectWhenGameIsNotFinished() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                PlayAgainRequest request = new PlayAgainRequest();

                request.setPlayerId(
                                room.getHostPlayerId());

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.playAgain(
                                                room.getRoomId(),
                                                request));

                assertEquals(
                                "Game is not finished",
                                exception.getMessage());
        }

        @Test
        void playAgainShouldResetRoomToLobby() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                completeFullGame(room);

                assertEquals(
                                GameState.GAME_FINISHED,
                                room.getGameState());

                PlayAgainRequest request = new PlayAgainRequest();

                request.setPlayerId(
                                room.getHostPlayerId());

                Room resetRoom = roomService.playAgain(
                                room.getRoomId(),
                                request);

                assertEquals(
                                GameState.LOBBY,
                                resetRoom.getGameState());

                assertEquals(
                                0,
                                resetRoom.getCurrentRound());

                assertNull(
                                resetRoom.getCurrentDrawerId());

                assertNull(
                                resetRoom.getTurnPhase());

                assertNull(
                                resetRoom.getCurrentWord());

                assertTrue(
                                resetRoom.getWordChoices().isEmpty());

                assertTrue(
                                resetRoom.getEligiblePlayerIds().isEmpty());

                assertTrue(
                                resetRoom.getCorrectGuesserIds().isEmpty());

                assertEquals(
                                0,
                                resetRoom.getTurnStartTime());

                assertEquals(
                                0,
                                resetRoom.getTurnEndTime());

                assertEquals(
                                0,
                                resetRoom.getCountdownEndTime());
        }

        @Test
        void playAgainShouldClearScoresAndSidePanelHistory() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                completeFullGame(room);

                assertTrue(
                                room.getPlayers()
                                                .stream()
                                                .anyMatch(player -> player.getScore() > 0));

                assertFalse(
                                room.getSidePanelMessages().isEmpty());

                PlayAgainRequest request = new PlayAgainRequest();

                request.setPlayerId(
                                room.getHostPlayerId());

                roomService.playAgain(
                                room.getRoomId(),
                                request);

                for (Player player : room.getPlayers()) {

                        assertEquals(
                                        0,
                                        player.getScore());
                }

                assertTrue(
                                room.getSidePanelMessages().isEmpty());
        }

        @Test
        void playerShouldBeAbleToJoinDuringActiveRound() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest firstJoin = new JoinRoomRequest();
                firstJoin.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                firstJoin);

                roomService.initializeRoundOne(room);

                JoinRoomRequest midRoundJoin = new JoinRoomRequest();
                midRoundJoin.setName("Aman");

                Room updatedRoom = roomService.joinRoom(
                                room.getRoomId(),
                                midRoundJoin);

                assertEquals(
                                3,
                                updatedRoom.getPlayers().size());

                assertTrue(
                                updatedRoom.getPlayers()
                                                .stream()
                                                .anyMatch(player -> player.getName().equals("Aman")));
        }

        @Test
        void midRoundJoinerShouldBeAddedToEligiblePlayerList() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest firstJoin = new JoinRoomRequest();
                firstJoin.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                firstJoin);

                roomService.initializeRoundOne(room);

                JoinRoomRequest midRoundJoin = new JoinRoomRequest();
                midRoundJoin.setName("Aman");

                roomService.joinRoom(
                                room.getRoomId(),
                                midRoundJoin);

                Player aman = room.getPlayers()
                                .stream()
                                .filter(player -> player.getName().equals("Aman"))
                                .findFirst()
                                .orElseThrow();

                assertTrue(
                                room.getEligiblePlayerIds()
                                                .contains(aman.getPlayerId()));
        }

        @Test
        void midRoundJoinShouldNotChangeCurrentDrawer() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest firstJoin = new JoinRoomRequest();
                firstJoin.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                firstJoin);

                roomService.initializeRoundOne(room);

                String drawerBeforeJoin = room.getCurrentDrawerId();

                JoinRoomRequest midRoundJoin = new JoinRoomRequest();
                midRoundJoin.setName("Aman");

                roomService.joinRoom(
                                room.getRoomId(),
                                midRoundJoin);

                assertEquals(
                                drawerBeforeJoin,
                                room.getCurrentDrawerId());
        }

        @Test
        void midRoundJoinShouldNotChangeCurrentTurnTimer() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest firstJoin = new JoinRoomRequest();
                firstJoin.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                firstJoin);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                long startBeforeJoin = room.getTurnStartTime();

                long endBeforeJoin = room.getTurnEndTime();

                JoinRoomRequest midRoundJoin = new JoinRoomRequest();

                midRoundJoin.setName("Aman");

                roomService.joinRoom(
                                room.getRoomId(),
                                midRoundJoin);

                assertEquals(
                                startBeforeJoin,
                                room.getTurnStartTime());

                assertEquals(
                                endBeforeJoin,
                                room.getTurnEndTime());
        }

        @Test
        void currentDrawerLeavingShouldMoveToNextDrawer() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String leavingDrawerId = room.getCurrentDrawerId();

                roomService.leaveRoom(
                                room.getRoomId(),
                                leavingDrawerId);

                assertNotNull(
                                room.getCurrentDrawerId());

                assertNotEquals(
                                leavingDrawerId,
                                room.getCurrentDrawerId());

                assertEquals(
                                TurnPhase.WORD_SELECTION,
                                room.getTurnPhase());

                assertNull(
                                room.getCurrentWord());

                assertFalse(
                                room.getWordChoices().isEmpty());
        }

        @Test
        void nonDrawerLeavingShouldNotChangeCurrentDrawer() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest1 = new JoinRoomRequest();
                joinRequest1.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest1);

                JoinRoomRequest joinRequest2 = new JoinRoomRequest();
                joinRequest2.setName("Aman");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest2);

                roomService.initializeRoundOne(room);

                String currentDrawerId = room.getCurrentDrawerId();

                Player nonDrawer = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(currentDrawerId))
                                .findFirst()
                                .orElseThrow();

                roomService.leaveRoom(
                                room.getRoomId(),
                                nonDrawer.getPlayerId());

                assertEquals(
                                currentDrawerId,
                                room.getCurrentDrawerId());
        }

        @Test
        void leavingPlayerShouldBeRemovedFromRoomAndEligibleList() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                Player leavingPlayer = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                String leavingPlayerId = leavingPlayer.getPlayerId();

                roomService.leaveRoom(room.getRoomId(), leavingPlayerId);

                assertFalse(
                                room.getPlayers()
                                                .stream()
                                                .anyMatch(player -> player.getPlayerId()
                                                                .equals(leavingPlayerId)));

                assertFalse(room.getEligiblePlayerIds().contains(leavingPlayerId));

                assertFalse(room.getCorrectGuesserIds().contains(leavingPlayerId));
        }

        @Test
        void hostLeavingShouldTransferHostToRemainingPlayer() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                String oldHostId = room.getHostPlayerId();

                Player remainingPlayer = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(oldHostId))
                                .findFirst()
                                .orElseThrow();

                roomService.leaveRoom(room.getRoomId(), oldHostId);

                assertEquals(remainingPlayer.getPlayerId(), room.getHostPlayerId());

                assertNotEquals(oldHostId, room.getHostPlayerId());
        }

        @Test
        void roomShouldBeDeletedWhenLastPlayerLeaves() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                String roomId = room.getRoomId();

                String hostId = room.getHostPlayerId();

                roomService.leaveRoom(roomId, hostId);

                RuntimeException exception = assertThrows(RuntimeException.class, () -> roomService.getRoom(roomId));

                assertEquals("Room not found with ID: " + roomId, exception.getMessage());
        }

        @Test
        void roomResponseShouldNotExposeCurrentWord() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                RoomResponse response = roomService.convertToRoomResponse(room);

                assertNotEquals(
                                selectedWord,
                                response.getWordPattern());
        }

        @Test
        void roomResponseShouldContainCurrentTurnPhase() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                RoomResponse response = roomService.convertToRoomResponse(room);

                assertEquals(
                                TurnPhase.WORD_SELECTION,
                                response.getTurnPhase());
        }

        @Test
        void drawingResponseShouldShowMaskedWordPattern() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                RoomResponse response = roomService.convertToRoomResponse(room);

                assertNotNull(
                                response.getWordPattern());

                assertFalse(
                                response.getWordPattern().isBlank());

                assertNotEquals(
                                selectedWord,
                                response.getWordPattern());
        }

        @Test
        void roomResponseShouldContainLeaderboardSortedByScore() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                Player raj = room.getPlayers().get(0);

                Player rahul = room.getPlayers().get(1);

                raj.setScore(500);
                rahul.setScore(200);

                RoomResponse response = roomService.convertToRoomResponse(room);

                assertNotNull(
                                response.getLeaderboard());

                assertEquals(
                                2,
                                response.getLeaderboard().size());

                assertEquals(
                                raj.getPlayerId(),
                                response.getLeaderboard()
                                                .get(0)
                                                .getPlayerId());

                assertEquals(
                                500,
                                response.getLeaderboard()
                                                .get(0)
                                                .getScore());
        }

        @Test
        void teamBattleStartShouldAssignTeams() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.TEAM_BATTLE);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                StartGameRequest startRequest = new StartGameRequest();

                startRequest.setPlayerId(
                                room.getHostPlayerId());

                roomService.startGame(
                                room.getRoomId(),
                                startRequest);

                for (Player player : room.getPlayers()) {
                        assertNotNull(player.getTeam());
                }
        }

        @Test
        void teamBattleShouldSplitTwoPlayersAcrossTeams() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.TEAM_BATTLE);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                StartGameRequest startRequest = new StartGameRequest();

                startRequest.setPlayerId(
                                room.getHostPlayerId());

                roomService.startGame(
                                room.getRoomId(),
                                startRequest);

                long redPlayers = room.getPlayers()
                                .stream()
                                .filter(player -> player.getTeam() == Team.RED)
                                .count();

                long bluePlayers = room.getPlayers()
                                .stream()
                                .filter(player -> player.getTeam() == Team.BLUE)
                                .count();

                assertEquals(1, redPlayers);
                assertEquals(1, bluePlayers);
        }

        @Test
        void teamBattleTeamsShouldBeBalanced() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.TEAM_BATTLE);

                Room room = roomService.createRoom(createRequest);

                String[] names = {
                                "Rahul",
                                "Aman",
                                "Neha"
                };

                for (String name : names) {

                        JoinRoomRequest joinRequest = new JoinRoomRequest();

                        joinRequest.setName(name);

                        roomService.joinRoom(
                                        room.getRoomId(),
                                        joinRequest);
                }

                StartGameRequest startRequest = new StartGameRequest();

                startRequest.setPlayerId(
                                room.getHostPlayerId());

                roomService.startGame(
                                room.getRoomId(),
                                startRequest);

                long redPlayers = room.getPlayers()
                                .stream()
                                .filter(player -> player.getTeam() == Team.RED)
                                .count();

                long bluePlayers = room.getPlayers()
                                .stream()
                                .filter(player -> player.getTeam() == Team.BLUE)
                                .count();

                assertEquals(2, redPlayers);
                assertEquals(2, bluePlayers);
        }

        @Test
        void roomResponseShouldCalculateTeamScores() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.TEAM_BATTLE);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                StartGameRequest startRequest = new StartGameRequest();

                startRequest.setPlayerId(
                                room.getHostPlayerId());

                roomService.startGame(
                                room.getRoomId(),
                                startRequest);

                Player firstPlayer = room.getPlayers().get(0);

                Player secondPlayer = room.getPlayers().get(1);

                firstPlayer.setScore(300);
                secondPlayer.setScore(150);

                RoomResponse response = roomService.convertToRoomResponse(room);

                int expectedRedScore = room.getPlayers()
                                .stream()
                                .filter(player -> player.getTeam() == Team.RED)
                                .mapToInt(player -> player.getScore())
                                .sum();

                int expectedBlueScore = room.getPlayers()
                                .stream()
                                .filter(player -> player.getTeam() == Team.BLUE)
                                .mapToInt(player -> player.getScore())
                                .sum();

                assertEquals(
                                expectedRedScore,
                                response.getRedTeamScore());

                assertEquals(
                                expectedBlueScore,
                                response.getBlueTeamScore());
        }

        @Test
        void midGameTeamBattleJoinerShouldGetTeam() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.TEAM_BATTLE);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest firstJoin = new JoinRoomRequest();
                firstJoin.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                firstJoin);

                StartGameRequest startRequest = new StartGameRequest();
                startRequest.setPlayerId(room.getHostPlayerId());

                roomService.startGame(
                                room.getRoomId(),
                                startRequest);

                roomService.initializeRoundOne(room);

                JoinRoomRequest midGameJoin = new JoinRoomRequest();
                midGameJoin.setName("Aman");

                roomService.joinRoom(
                                room.getRoomId(),
                                midGameJoin);

                Player aman = room.getPlayers()
                                .stream()
                                .filter(player -> player.getName().equals("Aman"))
                                .findFirst()
                                .orElseThrow();

                assertNotNull(
                                aman.getTeam());
        }

        @Test
        void midGameJoinerShouldJoinSmallerTeam() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.TEAM_BATTLE);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                StartGameRequest startRequest = new StartGameRequest();
                startRequest.setPlayerId(room.getHostPlayerId());

                roomService.startGame(
                                room.getRoomId(),
                                startRequest);

                roomService.initializeRoundOne(room);

                Player firstPlayer = room.getPlayers().get(0);

                Player secondPlayer = room.getPlayers().get(1);

                // Force an imbalance only for this test
                firstPlayer.setTeam(Team.RED);
                secondPlayer.setTeam(Team.RED);

                JoinRoomRequest midGameJoin = new JoinRoomRequest();

                midGameJoin.setName("Aman");

                roomService.joinRoom(
                                room.getRoomId(),
                                midGameJoin);

                Player aman = room.getPlayers()
                                .stream()
                                .filter(player -> player.getName().equals("Aman"))
                                .findFirst()
                                .orElseThrow();

                assertEquals(
                                Team.BLUE,
                                aman.getTeam());
        }

        @Test
        void roomResponseShouldShowRedAsWinningTeam() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.TEAM_BATTLE);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                Player firstPlayer = room.getPlayers().get(0);

                Player secondPlayer = room.getPlayers().get(1);

                firstPlayer.setTeam(Team.RED);
                secondPlayer.setTeam(Team.BLUE);

                firstPlayer.setScore(500);
                secondPlayer.setScore(200);

                // Winning team is only determined when game is finished
                room.setGameState(GameState.GAME_FINISHED);

                RoomResponse response = roomService.convertToRoomResponse(room);

                assertEquals(
                                Team.RED,
                                response.getWinningTeam());
        }

        @Test
        void roomResponseShouldShowBlueAsWinningTeam() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.TEAM_BATTLE);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                Player firstPlayer = room.getPlayers().get(0);

                Player secondPlayer = room.getPlayers().get(1);

                firstPlayer.setTeam(Team.RED);
                secondPlayer.setTeam(Team.BLUE);

                firstPlayer.setScore(150);
                secondPlayer.setScore(600);

                room.setGameState(
                                GameState.GAME_FINISHED);

                RoomResponse response = roomService.convertToRoomResponse(room);

                assertEquals(
                                150,
                                response.getRedTeamScore());

                assertEquals(
                                600,
                                response.getBlueTeamScore());

                assertEquals(
                                Team.BLUE,
                                response.getWinningTeam());
        }

        @Test
        void teamBattleTieShouldHaveNoWinningTeam() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.TEAM_BATTLE);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                Player firstPlayer = room.getPlayers().get(0);

                Player secondPlayer = room.getPlayers().get(1);

                firstPlayer.setTeam(Team.RED);
                secondPlayer.setTeam(Team.BLUE);

                firstPlayer.setScore(500);
                secondPlayer.setScore(500);

                room.setGameState(
                                GameState.GAME_FINISHED);

                RoomResponse response = roomService.convertToRoomResponse(room);

                assertEquals(
                                500,
                                response.getRedTeamScore());

                assertEquals(
                                500,
                                response.getBlueTeamScore());

                assertNull(
                                response.getWinningTeam());
        }

        @Test
        void classicModeShouldNotAssignTeams() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                StartGameRequest startRequest = new StartGameRequest();

                startRequest.setPlayerId(
                                room.getHostPlayerId());

                roomService.startGame(
                                room.getRoomId(),
                                startRequest);

                for (Player player : room.getPlayers()) {

                        assertNull(
                                        player.getTeam());
                }
        }

        @Test
        void classicModeResponseShouldHaveZeroTeamScores() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                room.getPlayers()
                                .get(0)
                                .setScore(500);

                room.getPlayers()
                                .get(1)
                                .setScore(300);

                RoomResponse response = roomService.convertToRoomResponse(room);

                assertEquals(
                                0,
                                response.getRedTeamScore());

                assertEquals(
                                0,
                                response.getBlueTeamScore());

                assertNull(
                                response.getWinningTeam());
        }

        @Test
        void playAgainShouldClearTeamAssignments() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.TEAM_BATTLE);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                StartGameRequest startRequest = new StartGameRequest();

                startRequest.setPlayerId(
                                room.getHostPlayerId());

                roomService.startGame(
                                room.getRoomId(),
                                startRequest);

                // Make sure teams were assigned
                for (Player player : room.getPlayers()) {
                        assertNotNull(player.getTeam());
                }

                // We use initializeRoundOne because our tests
                // do not wait for the real 10-second countdown.
                roomService.initializeRoundOne(room);

                completeFullGame(room);

                assertEquals(
                                GameState.GAME_FINISHED,
                                room.getGameState());

                PlayAgainRequest playAgainRequest = new PlayAgainRequest();

                playAgainRequest.setPlayerId(
                                room.getHostPlayerId());

                roomService.playAgain(
                                room.getRoomId(),
                                playAgainRequest);

                assertEquals(
                                GameState.LOBBY,
                                room.getGameState());

                for (Player player : room.getPlayers()) {

                        assertNull(
                                        player.getTeam());
                }
        }

        @Test
        void initializeRoundOneShouldPublishDrawerPrivateEvent() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                long eventCount = applicationEvents
                                .stream(DrawerPrivateEvent.class)
                                .count();

                assertTrue(eventCount > 0);
        }

        @Test
        void drawerPrivateEventShouldTargetCurrentDrawer() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                String currentDrawerId = room.getCurrentDrawerId();

                boolean found = applicationEvents
                                .stream(DrawerPrivateEvent.class)
                                .anyMatch(event -> event.getPlayerId()
                                                .equals(currentDrawerId));

                assertTrue(found);
        }

        @Test
        void drawerPrivateEventShouldContainWordChoices() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                boolean found = applicationEvents
                                .stream(DrawerPrivateEvent.class)
                                .anyMatch(event -> {

                                        DrawerNotification notification = event.getNotification();

                                        return notification != null
                                                        && notification.getTurnPhase() == TurnPhase.WORD_SELECTION
                                                        && notification.getWordChoices() != null
                                                        && notification.getWordChoices().size() == 3;
                                });

                assertTrue(found);
        }

        @Test
        void currentDrawerShouldReceivePrivateWordChoices() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                DrawerTurnResponse response = roomService.getDrawerTurn(
                                room.getRoomId(),
                                room.getCurrentDrawerId());

                assertEquals(TurnPhase.WORD_SELECTION, response.getTurnPhase());

                assertNotNull(response.getWordChoices());

                assertEquals(3, response.getWordChoices().size());

                assertNull(response.getCurrentWord());
        }

        @Test
        void nonDrawerShouldNotAccessDrawerTurnData() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                Player nonDrawer = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                assertThrows(RuntimeException.class,
                                () -> roomService.getDrawerTurn(
                                                room.getRoomId(),
                                                nonDrawer.getPlayerId()));
        }

        @Test
        void drawerTurnShouldContainSelectedWordDuringDrawing() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(room.getCurrentDrawerId());

                selectRequest.setWord(selectedWord);

                roomService.selectWord(room.getRoomId(), selectRequest);

                DrawerTurnResponse response = roomService.getDrawerTurn(
                                room.getRoomId(),
                                room.getCurrentDrawerId());

                assertEquals(TurnPhase.DRAWING, response.getTurnPhase());

                assertEquals(selectedWord, response.getCurrentWord());

                assertTrue(response.getTurnStartTime() > 0);

                assertTrue(response.getTurnEndTime() > response.getTurnStartTime());
        }

        @Test
        void getDrawerTurnShouldNormalizeRoomId() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                String lowerCaseRoomId = room.getRoomId().toLowerCase();

                DrawerTurnResponse response = roomService.getDrawerTurn(
                                lowerCaseRoomId,
                                room.getCurrentDrawerId());

                assertEquals(room.getCurrentDrawerId(), response.getCurrentDrawerId());

                assertEquals(TurnPhase.WORD_SELECTION, response.getTurnPhase());
        }

        @Test
        void drawerShouldNotSelectWordOutsideOfferedChoices() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                SelectWordRequest request = new SelectWordRequest();

                request.setPlayerId(room.getCurrentDrawerId());

                request.setWord("this-word-was-never-offered");

                assertThrows(RuntimeException.class,
                                () -> roomService.selectWord(
                                                room.getRoomId(),
                                                request));
        }

        @Test
        void selectingOfferedWordShouldMoveTurnToDrawing() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                assertEquals(TurnPhase.WORD_SELECTION, room.getTurnPhase());

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest request = new SelectWordRequest();

                request.setPlayerId(room.getCurrentDrawerId());
                request.setWord(selectedWord);

                roomService.selectWord(room.getRoomId(), request);

                assertEquals(TurnPhase.DRAWING, room.getTurnPhase());
                assertEquals(selectedWord, room.getCurrentWord());
        }

        @Test
        void drawerShouldNotSelectSecondWordAfterDrawingStarts() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);
                roomService.initializeRoundOne(room);

                String firstWord = room.getWordChoices().get(0);

                SelectWordRequest firstRequest = new SelectWordRequest();

                firstRequest.setPlayerId(room.getCurrentDrawerId());
                firstRequest.setWord(firstWord);
                roomService.selectWord(room.getRoomId(), firstRequest);

                assertEquals(TurnPhase.DRAWING, room.getTurnPhase());

                SelectWordRequest secondRequest = new SelectWordRequest();
                secondRequest.setPlayerId(room.getCurrentDrawerId());

                secondRequest.setWord(firstWord);

                assertThrows(RuntimeException.class,
                                () -> roomService.selectWord(
                                                room.getRoomId(),
                                                secondRequest));
        }

        @Test
        void selectingWordShouldStartExactSixtySecondTimer() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);
                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);
                SelectWordRequest request = new SelectWordRequest();

                request.setPlayerId(room.getCurrentDrawerId());
                request.setWord(selectedWord);

                roomService.selectWord(room.getRoomId(), request);

                assertTrue(room.getTurnStartTime() > 0);
                assertEquals(60_000, room.getTurnEndTime() - room.getTurnStartTime());
        }

        @Test
        void wordSelectionTimeoutShouldAutoSelectWord()
                        throws InterruptedException {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                assertEquals(TurnPhase.WORD_SELECTION, room.getTurnPhase());

                assertNull(room.getCurrentWord());

                Thread.sleep(5500);

                assertEquals(TurnPhase.DRAWING, room.getTurnPhase());

                assertNotNull(room.getCurrentWord());
        }

        @Test
        void autoSelectedWordShouldComeFromOfferedChoices()
                        throws InterruptedException {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                List<String> offeredWords = new ArrayList<>(room.getWordChoices());

                Thread.sleep(5500);

                assertTrue(offeredWords.contains(room.getCurrentWord()));
        }

        @Test
        void autoSelectShouldStartDrawingTimer()
                        throws InterruptedException {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                Thread.sleep(5500);

                assertTrue(room.getTurnStartTime() > 0);

                assertEquals(60_000, room.getTurnEndTime() - room.getTurnStartTime());
        }

        @Test
        void manualSelectionShouldPreventStaleAutoSelect()
                        throws InterruptedException {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);
                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(1);
                SelectWordRequest request = new SelectWordRequest();

                request.setPlayerId(room.getCurrentDrawerId());
                request.setWord(selectedWord);

                roomService.selectWord(room.getRoomId(), request);

                assertEquals(selectedWord, room.getCurrentWord());
                Thread.sleep(5500);

                assertEquals(selectedWord, room.getCurrentWord());
                assertEquals(TurnPhase.DRAWING, room.getTurnPhase());
        }

        @Test
        void drawingTimeoutShouldAdvanceToNextDrawer()
                        throws InterruptedException {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                String firstDrawerId = room.getCurrentDrawerId();
                String selectedWord = room.getWordChoices().get(0);
                SelectWordRequest request = new SelectWordRequest();

                request.setPlayerId(firstDrawerId);
                request.setWord(selectedWord);

                roomService.selectWord(room.getRoomId(), request);
                assertEquals(TurnPhase.DRAWING, room.getTurnPhase());
                Thread.sleep(61_000);
                assertNotEquals(firstDrawerId, room.getCurrentDrawerId());
                assertEquals(TurnPhase.WORD_SELECTION, room.getTurnPhase());
        }

        @Test
        void drawingTimeoutShouldClearPreviousTurnState()
                        throws InterruptedException {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);
                SelectWordRequest request = new SelectWordRequest();

                request.setPlayerId(room.getCurrentDrawerId());
                request.setWord(selectedWord);

                roomService.selectWord(room.getRoomId(), request);
                Thread.sleep(61_000);

                assertNull(room.getCurrentWord());
                assertTrue(room.getCorrectGuesserIds().isEmpty());
                assertEquals(0, room.getTurnStartTime());
                assertEquals(0, room.getTurnEndTime());
        }

        @Test
        void drawingTimeoutShouldPrepareNextDrawerWordChoices()
                        throws InterruptedException {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(room.getRoomId(), joinRequest);
                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);
                SelectWordRequest request = new SelectWordRequest();

                request.setPlayerId(room.getCurrentDrawerId());
                request.setWord(selectedWord);
                roomService.selectWord(room.getRoomId(), request);

                Thread.sleep(61_000);
                assertEquals(TurnPhase.WORD_SELECTION, room.getTurnPhase());
                assertNotNull(room.getWordChoices());
                assertEquals(3, room.getWordChoices().size());
        }

        @Test
        void guessShouldBeRejectedDuringWordSelection() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                GuessMessage guessMessage = new GuessMessage();
                guessMessage.setRoomId(room.getRoomId());
                guessMessage.setPlayerId(guesser.getPlayerId());
                guessMessage.setGuess("apple");

                assertThrows(RuntimeException.class,
                                () -> roomService.handleGuess(guessMessage));

                assertEquals(TurnPhase.WORD_SELECTION, room.getTurnPhase());
        }

        @Test
        void guessShouldRejectUnknownPlayer() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(
                                selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                GuessMessage guessMessage = new GuessMessage();
                guessMessage.setRoomId(room.getRoomId());
                guessMessage.setPlayerId("fake-player-id");
                guessMessage.setGuess(selectedWord);

                assertThrows(
                                RuntimeException.class,
                                () -> roomService.handleGuess(guessMessage));
        }

        @Test
        void blankGuessShouldBeRejected() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(
                                selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                GuessMessage guessMessage = new GuessMessage();
                guessMessage.setRoomId(room.getRoomId());
                guessMessage.setPlayerId(guesser.getPlayerId());
                guessMessage.setGuess("   ");

                assertThrows(
                                RuntimeException.class,
                                () -> roomService.handleGuess(guessMessage));
        }

        @Test
        void invalidGuessShouldNotModifyGameplayState() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(
                                selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                int scoreBefore = guesser.getScore();

                int correctCountBefore = room.getCorrectGuesserIds().size();

                GuessMessage guessMessage = new GuessMessage();
                guessMessage.setRoomId(room.getRoomId());
                guessMessage.setPlayerId(guesser.getPlayerId());
                guessMessage.setGuess("   ");

                assertThrows(
                                RuntimeException.class,
                                () -> roomService.handleGuess(guessMessage));

                assertEquals(
                                scoreBefore,
                                guesser.getScore());

                assertEquals(
                                correctCountBefore,
                                room.getCorrectGuesserIds().size());
        }

        @Test
        void wrongGuessShouldStorePlayerAndTextInSidePanel() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                GuessMessage guessMessage = new GuessMessage();

                guessMessage.setRoomId(room.getRoomId());
                guessMessage.setPlayerId(guesser.getPlayerId());
                guessMessage.setGuess("wrong-answer");

                roomService.handleGuess(guessMessage);

                List<SidePanelMessage> messages = roomService.getSidePanelMessages(
                                room.getRoomId());

                assertEquals(1, messages.size());

                SidePanelMessage message = messages.get(0);

                assertEquals(
                                guesser.getPlayerId(),
                                message.getPlayerId());

                assertEquals(
                                "wrong-answer",
                                message.getMessage());
        }

        @Test
        void correctGuessSidePanelMessageShouldBeMarkedCorrect() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                GuessMessage guessMessage = new GuessMessage();

                guessMessage.setRoomId(room.getRoomId());
                guessMessage.setPlayerId(guesser.getPlayerId());
                guessMessage.setGuess(selectedWord);

                roomService.handleGuess(guessMessage);

                List<SidePanelMessage> messages = roomService.getSidePanelMessages(
                                room.getRoomId());

                assertEquals(1, messages.size());

                assertTrue(
                                messages.get(0).isCorrect());

                assertNull(
                                messages.get(0).getMessage());
        }

        @Test
        void sidePanelHistoryShouldPersistIntoNextRound() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                completeCurrentTurn(room);
                completeCurrentTurn(room);

                assertEquals(
                                GameState.ROUND_2,
                                room.getGameState());

                assertFalse(
                                roomService.getSidePanelMessages(
                                                room.getRoomId()).isEmpty());
        }

        @Test
        void sidePanelHistoryShouldPersistAfterGameFinish() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                completeFullGame(room);

                assertEquals(
                                GameState.GAME_FINISHED,
                                room.getGameState());

                assertFalse(
                                roomService.getSidePanelMessages(
                                                room.getRoomId()).isEmpty());
        }

        @Test
        void drawingShouldBeRejectedDuringWordSelection() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                DrawingMessage drawingMessage = new DrawingMessage();

                drawingMessage.setRoomId(
                                room.getRoomId());

                drawingMessage.setPlayerId(
                                room.getCurrentDrawerId());

                assertThrows(
                                RuntimeException.class,
                                () -> roomService.handleDrawing(
                                                drawingMessage));

                assertEquals(
                                TurnPhase.WORD_SELECTION,
                                room.getTurnPhase());
        }

        @Test
        void nonDrawerShouldNotBeAllowedToDraw() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(
                                selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                Player nonDrawer = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                DrawingMessage drawingMessage = new DrawingMessage();

                drawingMessage.setRoomId(
                                room.getRoomId());

                drawingMessage.setPlayerId(
                                nonDrawer.getPlayerId());

                assertThrows(
                                RuntimeException.class,
                                () -> roomService.handleDrawing(
                                                drawingMessage));
        }

        @Test
        void currentDrawerShouldBeAllowedToDraw() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(
                                selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                DrawingMessage drawingMessage = new DrawingMessage();

                drawingMessage.setRoomId(
                                room.getRoomId());

                drawingMessage.setPlayerId(
                                room.getCurrentDrawerId());

                assertDoesNotThrow(
                                () -> roomService.handleDrawing(
                                                drawingMessage));
        }

        @Test
        void drawingShouldRejectFakePlayer() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(
                                selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                DrawingMessage drawingMessage = new DrawingMessage();

                drawingMessage.setRoomId(
                                room.getRoomId());

                drawingMessage.setPlayerId(
                                "fake-player-id");

                assertThrows(
                                RuntimeException.class,
                                () -> roomService.handleDrawing(
                                                drawingMessage));
        }

        @Test
        void validDrawingShouldBeBroadcastToRoomTopic() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                DrawingMessage drawingMessage = new DrawingMessage();

                drawingMessage.setRoomId(
                                room.getRoomId());

                drawingMessage.setPlayerId(
                                room.getCurrentDrawerId());

                roomService.handleDrawing(
                                drawingMessage);

                verify(messagingTemplate).convertAndSend(
                                "/topic/rooms/"
                                                + room.getRoomId()
                                                + "/drawing",
                                drawingMessage);
        }

        @Test
        void drawingBroadcastShouldUseNormalizedRoomId() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                DrawingMessage drawingMessage = new DrawingMessage();

                drawingMessage.setRoomId(
                                room.getRoomId().toLowerCase());

                drawingMessage.setPlayerId(
                                room.getCurrentDrawerId());

                roomService.handleDrawing(
                                drawingMessage);

                verify(messagingTemplate).convertAndSend(
                                "/topic/rooms/"
                                                + room.getRoomId()
                                                + "/drawing",
                                drawingMessage);
        }

        @Test
        void rejectedDrawingShouldNotBeBroadcast() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                DrawingMessage drawingMessage = new DrawingMessage();

                drawingMessage.setRoomId(
                                room.getRoomId());

                drawingMessage.setPlayerId(
                                room.getCurrentDrawerId());

                assertThrows(
                                RuntimeException.class,
                                () -> roomService.handleDrawing(
                                                drawingMessage));

                verify(
                                messagingTemplate,
                                never()).convertAndSend(
                                                eq(
                                                                "/topic/rooms/"
                                                                                + room.getRoomId()
                                                                                + "/drawing"),
                                                any(DrawingMessage.class));
        }

        @Test
        void wrongGuessShouldBeBroadcastToGuessTopic() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                GuessMessage guessMessage = new GuessMessage();

                guessMessage.setRoomId(
                                room.getRoomId());

                guessMessage.setPlayerId(
                                guesser.getPlayerId());

                guessMessage.setGuess(
                                "wrong-answer");

                roomService.handleGuess(
                                guessMessage);

                verify(messagingTemplate).convertAndSend(
                                eq(
                                                "/topic/rooms/"
                                                                + room.getRoomId()
                                                                + "/guesses"),
                                any(GuessResult.class));
        }

        @Test
        void wrongGuessBroadcastShouldContainGuessText() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);
                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(room.getCurrentDrawerId());
                selectRequest.setWord(selectedWord);

                roomService.selectWord(room.getRoomId(), selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                GuessMessage guessMessage = new GuessMessage();

                guessMessage.setRoomId(room.getRoomId());
                guessMessage.setPlayerId(guesser.getPlayerId());
                guessMessage.setGuess("wrong-answer");

                roomService.handleGuess(guessMessage);

                ArgumentCaptor<GuessResult> captor = ArgumentCaptor.forClass(GuessResult.class);

                verify(messagingTemplate).convertAndSend(
                                eq(
                                                "/topic/rooms/"
                                                                + room.getRoomId()
                                                                + "/guesses"),
                                captor.capture());

                GuessResult result = captor.getValue();

                assertFalse(result.isCorrect());
                assertEquals("wrong-answer", result.getGuess());
        }

        @Test
        void correctGuessBroadcastShouldHideSecretWord() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                GuessMessage guessMessage = new GuessMessage();

                guessMessage.setRoomId(
                                room.getRoomId());

                guessMessage.setPlayerId(
                                guesser.getPlayerId());

                guessMessage.setGuess(
                                selectedWord);

                roomService.handleGuess(
                                guessMessage);

                ArgumentCaptor<GuessResult> captor = ArgumentCaptor.forClass(
                                GuessResult.class);

                verify(messagingTemplate).convertAndSend(
                                eq(
                                                "/topic/rooms/"
                                                                + room.getRoomId()
                                                                + "/guesses"),
                                captor.capture());

                GuessResult result = captor.getValue();

                assertTrue(
                                result.isCorrect());

                assertNull(
                                result.getGuess());
        }

        @Test
        void guessBroadcastShouldUseNormalizedRoomId() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                GuessMessage guessMessage = new GuessMessage();

                guessMessage.setRoomId(
                                room.getRoomId().toLowerCase());

                guessMessage.setPlayerId(
                                guesser.getPlayerId());

                guessMessage.setGuess(
                                "wrong-answer");

                roomService.handleGuess(
                                guessMessage);

                verify(messagingTemplate).convertAndSend(
                                eq(
                                                "/topic/rooms/"
                                                                + room.getRoomId()
                                                                + "/guesses"),
                                any(GuessResult.class));
        }

        @Test
        void roomResponseShouldContainCopiedPlayers() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                RoomResponse response = roomService.convertToRoomResponse(room);

                assertNotSame(
                                room.getPlayers().get(0),
                                response.getPlayers().get(0));
        }

        @Test
        void modifyingResponsePlayerShouldNotModifyRoomPlayer() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                Player realPlayer = room.getPlayers().get(0);

                RoomResponse response = roomService.convertToRoomResponse(room);

                Player responsePlayer = response.getPlayers().get(0);

                responsePlayer.setScore(9999);

                assertNotEquals(
                                9999,
                                realPlayer.getScore());

                assertEquals(
                                0,
                                realPlayer.getScore());
        }

        @Test
        void roomResponsePlayerListShouldBeIndependentFromRoomList() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                RoomResponse response = roomService.convertToRoomResponse(room);

                int roomPlayerCount = room.getPlayers().size();

                response.getPlayers().clear();

                assertEquals(
                                roomPlayerCount,
                                room.getPlayers().size());

                assertFalse(
                                room.getPlayers().isEmpty());
        }

        @Test
        void drawerTurnWordChoicesShouldBeIndependentCopy() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();

                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                DrawerTurnResponse response = roomService.getDrawerTurn(
                                room.getRoomId(),
                                room.getCurrentDrawerId());

                assertEquals(
                                3,
                                response.getWordChoices().size());

                response.getWordChoices().clear();

                assertEquals(
                                3,
                                room.getWordChoices().size());
        }

        @Test
        void leaderboardShouldSortPlayersByScoreDescending() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest1 = new JoinRoomRequest();
                joinRequest1.setName("Rahul");
                roomService.joinRoom(room.getRoomId(), joinRequest1);

                JoinRoomRequest joinRequest2 = new JoinRoomRequest();
                joinRequest2.setName("Aman");
                roomService.joinRoom(room.getRoomId(), joinRequest2);

                room.getPlayers().get(0).setScore(200);
                room.getPlayers().get(1).setScore(700);
                room.getPlayers().get(2).setScore(400);

                RoomResponse response = roomService.convertToRoomResponse(room);

                assertEquals(
                                700,
                                response.getLeaderboard().get(0).getScore());

                assertEquals(
                                400,
                                response.getLeaderboard().get(1).getScore());

                assertEquals(
                                200,
                                response.getLeaderboard().get(2).getScore());
        }

        @Test
        void leaderboardShouldAssignNormalRanks() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest1 = new JoinRoomRequest();
                joinRequest1.setName("Rahul");
                roomService.joinRoom(room.getRoomId(), joinRequest1);

                JoinRoomRequest joinRequest2 = new JoinRoomRequest();
                joinRequest2.setName("Aman");
                roomService.joinRoom(room.getRoomId(), joinRequest2);

                room.getPlayers().get(0).setScore(900);
                room.getPlayers().get(1).setScore(500);
                room.getPlayers().get(2).setScore(100);

                RoomResponse response = roomService.convertToRoomResponse(room);

                assertEquals(
                                1,
                                response.getLeaderboard().get(0).getRank());

                assertEquals(
                                2,
                                response.getLeaderboard().get(1).getRank());

                assertEquals(
                                3,
                                response.getLeaderboard().get(2).getRank());
        }

        @Test
        void tiedPlayersShouldReceiveSameLeaderboardRank() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest1 = new JoinRoomRequest();
                joinRequest1.setName("Rahul");
                roomService.joinRoom(room.getRoomId(), joinRequest1);

                JoinRoomRequest joinRequest2 = new JoinRoomRequest();
                joinRequest2.setName("Aman");
                roomService.joinRoom(room.getRoomId(), joinRequest2);

                room.getPlayers().get(0).setScore(500);
                room.getPlayers().get(1).setScore(500);
                room.getPlayers().get(2).setScore(200);

                RoomResponse response = roomService.convertToRoomResponse(room);

                assertEquals(
                                1,
                                response.getLeaderboard().get(0).getRank());

                assertEquals(
                                1,
                                response.getLeaderboard().get(1).getRank());
        }

        @Test
        void leaderboardShouldUseCompetitionRankingAfterTie() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest1 = new JoinRoomRequest();
                joinRequest1.setName("Rahul");
                roomService.joinRoom(room.getRoomId(), joinRequest1);

                JoinRoomRequest joinRequest2 = new JoinRoomRequest();
                joinRequest2.setName("Aman");
                roomService.joinRoom(room.getRoomId(), joinRequest2);

                room.getPlayers().get(0).setScore(500);
                room.getPlayers().get(1).setScore(500);
                room.getPlayers().get(2).setScore(200);

                RoomResponse response = roomService.convertToRoomResponse(room);

                assertEquals(
                                1,
                                response.getLeaderboard().get(0).getRank());

                assertEquals(
                                1,
                                response.getLeaderboard().get(1).getRank());

                assertEquals(
                                3,
                                response.getLeaderboard().get(2).getRank());
        }

        @Test
        void correctGuessShouldGivePositiveGuesserScore() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                GuessMessage guessMessage = new GuessMessage();

                guessMessage.setRoomId(
                                room.getRoomId());

                guessMessage.setPlayerId(
                                guesser.getPlayerId());

                guessMessage.setGuess(
                                selectedWord);

                roomService.handleGuess(
                                guessMessage);

                assertTrue(
                                guesser.getScore() > 0);
        }

        @Test
        void correctGuessShouldGiveDrawerPoints() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                Player drawer = room.getPlayers()
                                .stream()
                                .filter(player -> player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                drawer.getPlayerId());

                selectRequest.setWord(selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(drawer.getPlayerId()))
                                .findFirst()
                                .orElseThrow();

                GuessMessage guessMessage = new GuessMessage();

                guessMessage.setRoomId(
                                room.getRoomId());

                guessMessage.setPlayerId(
                                guesser.getPlayerId());

                guessMessage.setGuess(
                                selectedWord);

                roomService.handleGuess(
                                guessMessage);

                assertTrue(
                                drawer.getScore() > 0);
        }

        @Test
        void fasterCorrectGuessShouldGiveMorePoints() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest1 = new JoinRoomRequest();
                joinRequest1.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest1);

                JoinRoomRequest joinRequest2 = new JoinRoomRequest();
                joinRequest2.setName("Aman");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest2);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                String drawerId = room.getCurrentDrawerId();

                Player firstGuesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId().equals(drawerId))
                                .findFirst()
                                .orElseThrow();

                Player secondGuesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId().equals(drawerId))
                                .filter(player -> !player.getPlayerId()
                                                .equals(firstGuesser.getPlayerId()))
                                .findFirst()
                                .orElseThrow();

                // First guess happens with almost the full
                // 60-second drawing time remaining.
                GuessMessage firstGuess = new GuessMessage();

                firstGuess.setRoomId(
                                room.getRoomId());

                firstGuess.setPlayerId(
                                firstGuesser.getPlayerId());

                firstGuess.setGuess(
                                selectedWord);

                roomService.handleGuess(
                                firstGuess);

                int firstGuesserScore = firstGuesser.getScore();

                // Simulate a much later point in the same turn
                // without actually waiting.
                room.setTurnEndTime(
                                System.currentTimeMillis() + 10_000);

                GuessMessage secondGuess = new GuessMessage();

                secondGuess.setRoomId(
                                room.getRoomId());

                secondGuess.setPlayerId(
                                secondGuesser.getPlayerId());

                secondGuess.setGuess(
                                selectedWord);

                roomService.handleGuess(
                                secondGuess);

                int secondGuesserScore = secondGuesser.getScore();

                assertTrue(
                                firstGuesserScore > secondGuesserScore);
        }

        @Test
        void drawerShouldAccumulatePointsFromMultipleCorrectGuessers() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest1 = new JoinRoomRequest();
                joinRequest1.setName("Rahul");
                roomService.joinRoom(room.getRoomId(), joinRequest1);

                JoinRoomRequest joinRequest2 = new JoinRoomRequest();
                joinRequest2.setName("Aman");
                roomService.joinRoom(room.getRoomId(), joinRequest2);

                roomService.initializeRoundOne(room);

                String drawerId = room.getCurrentDrawerId();

                Player drawer = room.getPlayers()
                                .stream()
                                .filter(player -> player.getPlayerId().equals(drawerId))
                                .findFirst()
                                .orElseThrow();

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(drawerId);
                selectRequest.setWord(selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                List<Player> guessers = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId().equals(drawerId))
                                .toList();

                GuessMessage firstGuess = new GuessMessage();

                firstGuess.setRoomId(room.getRoomId());
                firstGuess.setPlayerId(
                                guessers.get(0).getPlayerId());
                firstGuess.setGuess(selectedWord);

                roomService.handleGuess(firstGuess);

                int drawerScoreAfterFirstGuess = drawer.getScore();

                GuessMessage secondGuess = new GuessMessage();

                secondGuess.setRoomId(room.getRoomId());
                secondGuess.setPlayerId(
                                guessers.get(1).getPlayerId());
                secondGuess.setGuess(selectedWord);

                roomService.handleGuess(secondGuess);

                assertTrue(
                                drawer.getScore() > drawerScoreAfterFirstGuess);
        }

        @Test
        void roomIdWithSpacesShouldBeNormalized() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                String spacedRoomId = "   " + room.getRoomId().toLowerCase() + "   ";

                Room foundRoom = roomService.getRoom(spacedRoomId);

                assertEquals(room.getRoomId(), foundRoom.getRoomId());
        }

        @Test
        void joinRoomShouldAcceptNormalizedRoomId() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();

                joinRequest.setName("Rahul");

                String modifiedRoomId = "  " + room.getRoomId().toLowerCase() + "  ";

                Room updatedRoom = roomService.joinRoom(
                                modifiedRoomId,
                                joinRequest);

                assertEquals(2, updatedRoom.getPlayers().size());
        }

        @Test
        void blankRoomIdShouldBeRejected() {

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.getRoom("   "));

                assertEquals("Room ID is required", exception.getMessage());
        }

        @Test
        void nullRoomIdShouldBeRejected() {

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.getRoom(null));

                assertEquals(
                                "Room ID is required",
                                exception.getMessage());
        }

        @Test
        void createRoomShouldRejectBlankHostName() {

                CreateRoomRequest request = new CreateRoomRequest();

                request.setName("   ");
                request.setMaxPlayers(4);
                request.setGameMode(GameMode.CLASSIC);

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.createRoom(request));

                assertEquals(
                                "Player name is required",
                                exception.getMessage());
        }

        @Test
        void joinRoomShouldRejectBlankPlayerName() {

                CreateRoomRequest createRequest = new CreateRoomRequest();

                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();

                joinRequest.setName("   ");

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.joinRoom(
                                                room.getRoomId(),
                                                joinRequest));

                assertEquals(
                                "Player name is required",
                                exception.getMessage());
        }

        @Test
        void joinRoomShouldRejectDuplicateNameWithSpaces() {

                CreateRoomRequest createRequest = new CreateRoomRequest();

                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();

                joinRequest.setName("  Raj  ");

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.joinRoom(
                                                room.getRoomId(),
                                                joinRequest));

                assertEquals(
                                "Player name already exists in the room",
                                exception.getMessage());
        }

        @Test
        void joinedPlayerNameShouldBeTrimmed() {

                CreateRoomRequest createRequest = new CreateRoomRequest();

                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();

                joinRequest.setName("  Rahul  ");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                Player rahul = room.getPlayers()
                                .stream()
                                .filter(player -> player.getName().equals("Rahul"))
                                .findFirst()
                                .orElseThrow();

                assertEquals(
                                "Rahul",
                                rahul.getName());
        }

        @Test
        void createdHostNameShouldBeTrimmed() {

                CreateRoomRequest request = new CreateRoomRequest();

                request.setName("   Raj   ");
                request.setMaxPlayers(4);
                request.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(request);

                assertEquals(
                                "Raj",
                                room.getPlayers()
                                                .get(0)
                                                .getName());
        }

        @Test
        void createRoomShouldRejectMaxPlayersBelowTwo() {

                CreateRoomRequest request = new CreateRoomRequest();

                request.setName("Raj");
                request.setMaxPlayers(1);
                request.setGameMode(GameMode.CLASSIC);

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.createRoom(request));

                assertEquals(
                                "Max players must be between 2 and 15",
                                exception.getMessage());
        }

        @Test
        void createRoomShouldRejectMaxPlayersAboveEight() {

                CreateRoomRequest request = new CreateRoomRequest();

                request.setName("Raj");
                request.setMaxPlayers(9);
                request.setGameMode(GameMode.CLASSIC);

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.createRoom(request));

                assertEquals(
                                "Max players must be between 2 and 15",
                                exception.getMessage());
        }

        @Test
        void createRoomShouldRejectMissingGameMode() {

                CreateRoomRequest request = new CreateRoomRequest();

                request.setName("Raj");
                request.setMaxPlayers(4);
                request.setGameMode(null);

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.createRoom(request));

                assertEquals(
                                "Game mode is required",
                                exception.getMessage());
        }

        @Test
        void createRoomShouldAcceptTwoPlayersMaximum() {

                CreateRoomRequest request = new CreateRoomRequest();

                request.setName("Raj");
                request.setMaxPlayers(2);
                request.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(request);

                assertEquals(
                                2,
                                room.getMaxPlayers());
        }

        @Test
        void createRoomShouldAcceptEightPlayersMaximum() {

                CreateRoomRequest request = new CreateRoomRequest();

                request.setName("Raj");
                request.setMaxPlayers(15);
                request.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(request);

                assertEquals(
                                15,
                                room.getMaxPlayers());
        }

        @Test
        void roomShouldAllowPlayersUpToExactCapacity() {

                CreateRoomRequest createRequest = new CreateRoomRequest();

                createRequest.setName("Raj");
                createRequest.setMaxPlayers(2);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();

                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                assertEquals(
                                2,
                                room.getPlayers().size());
        }

        @Test
        void rejectedFullRoomJoinShouldNotModifyPlayers() {

                CreateRoomRequest createRequest = new CreateRoomRequest();

                createRequest.setName("Raj");
                createRequest.setMaxPlayers(2);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest firstJoin = new JoinRoomRequest();

                firstJoin.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                firstJoin);

                assertEquals(
                                2,
                                room.getPlayers().size());

                JoinRoomRequest rejectedJoin = new JoinRoomRequest();

                rejectedJoin.setName("Aman");

                assertThrows(
                                RuntimeException.class,
                                () -> roomService.joinRoom(
                                                room.getRoomId(),
                                                rejectedJoin));

                assertEquals(
                                2,
                                room.getPlayers().size());

                assertFalse(
                                room.getPlayers()
                                                .stream()
                                                .anyMatch(player -> player.getName()
                                                                .equals("Aman")));
        }

        @Test
        void nonHostLeavingShouldKeepCurrentHost() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                String hostId = room.getHostPlayerId();

                Player rahul = room.getPlayers()
                                .stream()
                                .filter(player -> player.getName().equals("Rahul"))
                                .findFirst()
                                .orElseThrow();

                roomService.leaveRoom(
                                room.getRoomId(),
                                rahul.getPlayerId());

                assertEquals(
                                hostId,
                                room.getHostPlayerId());
        }

        @Test
        void hostLeavingShouldTransferHostWhenMultiplePlayersRemain() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest join1 = new JoinRoomRequest();
                join1.setName("Rahul");
                roomService.joinRoom(room.getRoomId(), join1);

                JoinRoomRequest join2 = new JoinRoomRequest();
                join2.setName("Aman");
                roomService.joinRoom(room.getRoomId(), join2);

                String oldHostId = room.getHostPlayerId();

                roomService.leaveRoom(
                                room.getRoomId(),
                                oldHostId);

                assertNotEquals(
                                oldHostId,
                                room.getHostPlayerId());

                assertTrue(
                                room.getPlayers()
                                                .stream()
                                                .anyMatch(player -> player.getPlayerId()
                                                                .equals(room.getHostPlayerId())));
        }

        @Test
        void leaveRoomShouldRejectUnknownPlayer() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.leaveRoom(
                                                room.getRoomId(),
                                                "fake-player-id"));

                assertEquals(
                                "Player not found",
                                exception.getMessage());
        }

        @Test
        void leaveRoomShouldRejectUnknownRoom() {

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.leaveRoom(
                                                "ABC123",
                                                "player-1"));

                assertTrue(
                                exception.getMessage()
                                                .contains("Room not found"));
        }

        @Test
        void startGameShouldRejectBlankRoomId() {

                StartGameRequest request = new StartGameRequest();

                request.setPlayerId("player-1");

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.startGame(
                                                "   ",
                                                request));

                assertEquals(
                                "Room ID is required",
                                exception.getMessage());
        }

        @Test
        void selectWordShouldRejectBlankRoomId() {

                SelectWordRequest request = new SelectWordRequest();

                request.setPlayerId("player-1");
                request.setWord("apple");

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.selectWord(
                                                "   ",
                                                request));

                assertEquals(
                                "Room ID is required",
                                exception.getMessage());
        }

        @Test
        void leaveRoomShouldRejectBlankRoomId() {

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.leaveRoom(
                                                "   ",
                                                "player-1"));

                assertEquals(
                                "Room ID is required",
                                exception.getMessage());
        }

        @Test
        void getSidePanelMessagesShouldRejectBlankRoomId() {

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.getSidePanelMessages(
                                                "   "));

                assertEquals(
                                "Room ID is required",
                                exception.getMessage());
        }

        @Test
        void startGameShouldAcceptNormalizedRoomId() {

                CreateRoomRequest createRequest = new CreateRoomRequest();

                createRequest.setName("Raj");
                createRequest.setMaxPlayers(4);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();

                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                StartGameRequest startRequest = new StartGameRequest();

                startRequest.setPlayerId(
                                room.getHostPlayerId());

                Room startedRoom = roomService.startGame(
                                "  " + room.getRoomId().toLowerCase() + "  ",
                                startRequest);

                assertEquals(
                                GameState.STARTING,
                                startedRoom.getGameState());
        }

        @Test
        void playAgainShouldAcceptNormalizedRoomId() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(2);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                // IMPORTANT
                roomService.initializeRoundOne(room);

                completeFullGame(room);

                assertEquals(
                                GameState.GAME_FINISHED,
                                room.getGameState());

                PlayAgainRequest request = new PlayAgainRequest();

                request.setPlayerId(
                                room.getHostPlayerId());

                Room restartedRoom = roomService.playAgain(
                                "  "
                                                + room.getRoomId().toLowerCase()
                                                + "  ",
                                request);

                assertEquals(
                                GameState.LOBBY,
                                restartedRoom.getGameState());
        }

        @Test
        void getDrawerTurnShouldAcceptNormalizedRoomId() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(2);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");
                roomService.joinRoom(room.getRoomId(), joinRequest);

                StartGameRequest startRequest = new StartGameRequest();
                startRequest.setPlayerId(room.getHostPlayerId());

                roomService.startGame(room.getRoomId(), startRequest);

                roomService.initializeRoundOne(room);

                DrawerTurnResponse response = roomService.getDrawerTurn(
                                "  " + room.getRoomId().toLowerCase() + "  ",
                                room.getCurrentDrawerId());

                assertEquals(
                                room.getCurrentDrawerId(),
                                response.getCurrentDrawerId());
        }

        @Test
        void handleDrawingShouldAcceptNormalizedRoomId() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(2);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");
                roomService.joinRoom(room.getRoomId(), joinRequest);

                roomService.initializeRoundOne(room);

                String word = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(word);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                DrawingMessage message = new DrawingMessage();

                message.setRoomId(
                                "  " + room.getRoomId().toLowerCase() + "  ");

                message.setPlayerId(
                                room.getCurrentDrawerId());

                assertDoesNotThrow(
                                () -> roomService.handleDrawing(message));
        }

        @Test
        void handleGuessShouldAcceptNormalizedRoomId() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(3);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest join1 = new JoinRoomRequest();
                join1.setName("Rahul");
                roomService.joinRoom(room.getRoomId(), join1);

                JoinRoomRequest join2 = new JoinRoomRequest();
                join2.setName("Aman");
                roomService.joinRoom(room.getRoomId(), join2);

                roomService.initializeRoundOne(room);

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(
                                selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                GuessMessage message = new GuessMessage();

                message.setRoomId(
                                "  " + room.getRoomId().toLowerCase() + "  ");

                message.setPlayerId(
                                guesser.getPlayerId());

                message.setGuess(
                                selectedWord);

                assertDoesNotThrow(
                                () -> roomService.handleGuess(message));
        }

        @Test
        void handleDrawingShouldRejectBlankRoomId() {

                DrawingMessage message = new DrawingMessage();

                message.setRoomId("   ");
                message.setPlayerId("player-1");

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.handleDrawing(message));

                assertEquals(
                                "Room ID is required",
                                exception.getMessage());
        }

        @Test
        void handleGuessShouldRejectBlankRoomId() {

                GuessMessage message = new GuessMessage();

                message.setRoomId("   ");
                message.setPlayerId("player-1");
                message.setGuess("apple");

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.handleGuess(message));

                assertEquals(
                                "Room ID is required",
                                exception.getMessage());
        }

        @Test
        void getDrawerTurnShouldRejectBlankRoomId() {

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.getDrawerTurn(
                                                "   ",
                                                "player-1"));

                assertEquals(
                                "Room ID is required",
                                exception.getMessage());
        }

        @Test
        void playAgainShouldRejectBlankRoomId() {

                PlayAgainRequest request = new PlayAgainRequest();

                request.setPlayerId("player-1");

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> roomService.playAgain(
                                                "   ",
                                                request));

                assertEquals(
                                "Room ID is required",
                                exception.getMessage());
        }

        @Test
        void playerExistsInRoomShouldAcceptNormalizedRoomId() {

                CreateRoomRequest request = new CreateRoomRequest();
                request.setName("Raj");
                request.setMaxPlayers(4);
                request.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(request);

                boolean exists = roomService.playerExistsInRoom(
                                "  " + room.getRoomId().toLowerCase() + "  ",
                                room.getHostPlayerId());

                assertTrue(exists);
        }

        @Test
        void playerExistsInRoomShouldReturnFalseForBlankRoomId() {

                boolean exists = roomService.playerExistsInRoom(
                                "   ",
                                "player-1");

                assertFalse(exists);
        }

        @Test
        void playerExistsInRoomShouldReturnFalseForUnknownPlayer() {

                CreateRoomRequest request = new CreateRoomRequest();
                request.setName("Raj");
                request.setMaxPlayers(4);
                request.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(request);

                boolean exists = roomService.playerExistsInRoom(
                                room.getRoomId(),
                                "fake-player-id");

                assertFalse(exists);
        }

        @Test
        void roundOneShouldConsumeThreeWordsFromRoomPool() {

                CreateRoomRequest request = new CreateRoomRequest();
                request.setName("Raj");
                request.setMaxPlayers(4);
                request.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(request);

                int initialSize = room.getAvailableWords().size();

                roomService.initializeRoundOne(room);

                assertEquals(
                                initialSize - 3,
                                room.getAvailableWords().size());

                assertEquals(
                                3,
                                room.getWordChoices().size());
        }

        @Test
        void consecutiveTurnsShouldNotRepeatWordChoices() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(2);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                List<String> firstChoices = new ArrayList<>(room.getWordChoices());

                String selectedWord = firstChoices.get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(
                                selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                GuessMessage guessMessage = new GuessMessage();

                guessMessage.setRoomId(
                                room.getRoomId());

                guessMessage.setPlayerId(
                                guesser.getPlayerId());

                guessMessage.setGuess(
                                selectedWord);

                roomService.handleGuess(
                                guessMessage);

                List<String> secondChoices = new ArrayList<>(room.getWordChoices());

                assertEquals(
                                3,
                                secondChoices.size());

                assertTrue(Collections.disjoint(firstChoices, secondChoices));
        }

        @Test
        void differentRoomsShouldHaveIndependentWordPools() {

                CreateRoomRequest firstRequest = new CreateRoomRequest();
                firstRequest.setName("Raj");
                firstRequest.setMaxPlayers(4);
                firstRequest.setGameMode(GameMode.CLASSIC);

                Room firstRoom = roomService.createRoom(firstRequest);

                CreateRoomRequest secondRequest = new CreateRoomRequest();
                secondRequest.setName("Aman");
                secondRequest.setMaxPlayers(4);
                secondRequest.setGameMode(GameMode.CLASSIC);

                Room secondRoom = roomService.createRoom(secondRequest);

                int secondRoomInitialSize = secondRoom.getAvailableWords().size();

                assertNotSame(
                                firstRoom.getAvailableWords(),
                                secondRoom.getAvailableWords());

                roomService.initializeRoundOne(firstRoom);

                assertEquals(
                                secondRoomInitialSize,
                                secondRoom.getAvailableWords().size());
        }

        @Test
        void playAgainShouldResetWordPool() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(2);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                int fullPoolSize = room.getAvailableWords().size();

                JoinRoomRequest joinRequest = new JoinRoomRequest();

                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                completeFullGame(room);

                assertEquals(
                                GameState.GAME_FINISHED,
                                room.getGameState());

                PlayAgainRequest playAgainRequest = new PlayAgainRequest();

                playAgainRequest.setPlayerId(
                                room.getHostPlayerId());

                roomService.playAgain(
                                room.getRoomId(),
                                playAgainRequest);

                assertEquals(
                                fullPoolSize,
                                room.getAvailableWords().size());
        }

        @Test
        void refreshedWordPoolShouldNotImmediatelyRepeatLastChoices() {

                List<String> availableWords = new ArrayList<>();

                availableWords.add("One");
                availableWords.add("Two");

                List<String> lastChoices = List.of(
                                "Apple",
                                "Car",
                                "House");

                List<String> nextWords = wordService.getNextWords(
                                availableWords,
                                lastChoices);

                assertEquals(
                                3,
                                nextWords.size());

                assertTrue(
                                Collections.disjoint(
                                                lastChoices,
                                                nextWords));
        }

        @Test
        void roundOneShouldStoreLastWordChoices() {

                CreateRoomRequest request = new CreateRoomRequest();
                request.setName("Raj");
                request.setMaxPlayers(4);
                request.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(request);

                roomService.initializeRoundOne(room);

                assertEquals(
                                3,
                                room.getLastWordChoices().size());

                assertEquals(
                                room.getWordChoices(),
                                room.getLastWordChoices());
        }

        @Test
        void nextTurnShouldReplaceLastWordChoices() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(2);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                List<String> firstChoices = new ArrayList<>(
                                room.getLastWordChoices());

                String selectedWord = room.getWordChoices().get(0);

                SelectWordRequest selectRequest = new SelectWordRequest();

                selectRequest.setPlayerId(
                                room.getCurrentDrawerId());

                selectRequest.setWord(
                                selectedWord);

                roomService.selectWord(
                                room.getRoomId(),
                                selectRequest);

                Player guesser = room.getPlayers()
                                .stream()
                                .filter(player -> !player.getPlayerId()
                                                .equals(room.getCurrentDrawerId()))
                                .findFirst()
                                .orElseThrow();

                GuessMessage guessMessage = new GuessMessage();

                guessMessage.setRoomId(
                                room.getRoomId());

                guessMessage.setPlayerId(
                                guesser.getPlayerId());

                guessMessage.setGuess(
                                selectedWord);

                roomService.handleGuess(
                                guessMessage);

                assertEquals(
                                3,
                                room.getLastWordChoices().size());

                assertNotEquals(
                                firstChoices,
                                room.getLastWordChoices());

                assertEquals(
                                room.getWordChoices(),
                                room.getLastWordChoices());
        }

        @Test
        void playAgainShouldClearLastWordChoices() {

                CreateRoomRequest createRequest = new CreateRoomRequest();
                createRequest.setName("Raj");
                createRequest.setMaxPlayers(2);
                createRequest.setGameMode(GameMode.CLASSIC);

                Room room = roomService.createRoom(createRequest);

                JoinRoomRequest joinRequest = new JoinRoomRequest();
                joinRequest.setName("Rahul");

                roomService.joinRoom(
                                room.getRoomId(),
                                joinRequest);

                roomService.initializeRoundOne(room);

                assertFalse(
                                room.getLastWordChoices().isEmpty());

                completeFullGame(room);

                PlayAgainRequest request = new PlayAgainRequest();

                request.setPlayerId(
                                room.getHostPlayerId());

                roomService.playAgain(
                                room.getRoomId(),
                                request);

                assertTrue(
                                room.getLastWordChoices().isEmpty());
        }

        @Test
        void exhaustedWordPoolShouldRefreshAndReturnThreeWords() {

                List<String> availableWords = new ArrayList<>();

                availableWords.add("TemporaryOne");
                availableWords.add("TemporaryTwo");

                List<String> lastChoices = List.of(
                                "Apple",
                                "Car",
                                "House");

                List<String> choices = wordService.getNextWords(
                                availableWords,
                                lastChoices);

                assertEquals(
                                3,
                                choices.size());

                assertTrue(
                                Collections.disjoint(
                                                choices,
                                                lastChoices));
        }

}