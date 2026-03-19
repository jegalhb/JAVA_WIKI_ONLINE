package quizapp;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import Reproject.ConceptRepository;
import Reproject.SearchService;

public class JavaQuizGameMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                String host = JOptionPane.showInputDialog(null, "서버 IP", "127.0.0.1");
                if (host == null || host.trim().isEmpty()) {
                    return;
                }

                String portText = JOptionPane.showInputDialog(null, "서버 포트", String.valueOf(QuizLobbyServer.DEFAULT_PORT));
                if (portText == null || portText.trim().isEmpty()) {
                    return;
                }
                int port = Integer.parseInt(portText.trim());

                String nickname = JOptionPane.showInputDialog(null, "호스트 닉네임", "host");
                if (nickname == null || nickname.trim().isEmpty()) {
                    return;
                }

                String roomName = JOptionPane.showInputDialog(null, "방 이름", "JAVA 학습방");
                if (roomName == null || roomName.trim().isEmpty()) {
                    return;
                }

                QuizRoomConfig config = new QuizRoomConfig(roomName.trim(), QuizMode.CHOSEONG, 10, 300, 20, 8);
                QuizSessionInit init;
                try (QuizLobbyClient client = new QuizLobbyClient(host.trim(), port)) {
                    init = client.createRoom(config, nickname.trim());
                }

                ConceptRepository repository = new ConceptRepository();
                SearchService searchService = new SearchService(repository);
                JavaQuizGameFrame frame = new JavaQuizGameFrame(repository, searchService, true);
                frame.applyRoomConfig(init.getConfig());
                frame.setConnectedUsers(init.getUsers());

                String serverHost = host.trim();
                int serverPort = port;
                String roomId = init.getRoomId();
                String hostNick = nickname.trim();
                final long[] lastStartSequence = new long[]{0L};

                frame.setHostConfigUpdatedAction(() -> {
                    try (QuizLobbyClient client = new QuizLobbyClient(serverHost, serverPort)) {
                        RoomRuntimeState state = client.updateRoomConfig(roomId, hostNick, frame.getCurrentRoomConfig());
                        frame.applyRoomConfig(state.getConfig());
                        frame.setConnectedUsers(state.getUsers());
                        frame.applyRuntimeState(state);
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "방 설정 동기화 실패: " + e.getMessage()));
                    }
                });

                frame.setStartGameAction(() -> {
                    try (QuizLobbyClient client = new QuizLobbyClient(serverHost, serverPort)) {
                        RoomRuntimeState state = client.startGame(roomId, hostNick);
                        frame.applyRoomConfig(state.getConfig());
                        frame.setConnectedUsers(state.getUsers());
                        frame.startGameWithSeed(state.getGameSeed());
                        frame.applyRuntimeState(state);
                        lastStartSequence[0] = state.getStartSequence();
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "게임 시작 동기화 실패: " + e.getMessage()));
                    }
                });

                frame.setAnswerSubmitAction((answer, round) -> {
                    try (QuizLobbyClient client = new QuizLobbyClient(serverHost, serverPort)) {
                        boolean correct = frame.isAnswerCorrectForRound(answer, round);
                        RoomRuntimeState state = client.submitAnswer(roomId, hostNick, round, correct, answer);
                        frame.applyRuntimeState(state);
                    } catch (Exception ignored) {
                    }
                });

                frame.setRoundTimeoutAction(round -> {
                    try (QuizLobbyClient client = new QuizLobbyClient(serverHost, serverPort)) {
                        RoomRuntimeState state = client.advanceRound(roomId, hostNick, round, "R" + round + " 시간 초과");
                        frame.applyRuntimeState(state);
                    } catch (Exception ignored) {
                    }
                });

                Timer pollTimer = new Timer(700, e -> {
                    try (QuizLobbyClient client = new QuizLobbyClient(serverHost, serverPort)) {
                        RoomRuntimeState state = client.getRoomState(roomId);
                        frame.applyRoomConfig(state.getConfig());
                        frame.setConnectedUsers(state.getUsers());

                        if (state.getStartSequence() > lastStartSequence[0]) {
                            lastStartSequence[0] = state.getStartSequence();
                            frame.startGameWithSeed(state.getGameSeed());
                        }
                        frame.applyRuntimeState(state);
                    } catch (Exception ignored) {
                    }
                });
                pollTimer.start();

                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        closeHostRoom(serverHost, serverPort, roomId, hostNick, pollTimer);
                    }

                    @Override
                    public void windowClosed(WindowEvent e) {
                        closeHostRoom(serverHost, serverPort, roomId, hostNick, pollTimer);
                    }
                });

                frame.setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "호스트 시작 실패: " + e.getMessage());
            }
        });
    }

    private static void closeHostRoom(String host, int port, String roomId, String hostNick, Timer pollTimer) {
        pollTimer.stop();
        try (QuizLobbyClient client = new QuizLobbyClient(host, port)) {
            client.closeRoom(roomId, hostNick);
        } catch (Exception ignored) {
        }
    }
}
