package quizapp;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import Reproject.ConceptRepository;
import Reproject.SearchService;

public class JavaClientMain {
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

                String nickname = JOptionPane.showInputDialog(null, "닉네임", "guest");
                if (nickname == null || nickname.trim().isEmpty()) {
                    return;
                }

                QuizSessionInit init;
                try (QuizLobbyClient client = new QuizLobbyClient(host.trim(), port)) {
                    List<QuizRoomInfo> rooms = client.listRooms();
                    if (rooms.isEmpty()) {
                        JOptionPane.showMessageDialog(null, "참여 가능한 게임 방이 없습니다.");
                        return;
                    }

                    QuizRoomInfo selected = (QuizRoomInfo) JOptionPane.showInputDialog(
                            null,
                            "참여할 게임 방을 선택하세요.",
                            "방 목록",
                            JOptionPane.PLAIN_MESSAGE,
                            null,
                            rooms.toArray(),
                            rooms.get(0)
                    );
                    if (selected == null) {
                        return;
                    }

                    init = client.joinRoom(selected.getRoomId(), nickname.trim());
                }

                ConceptRepository repository = new ConceptRepository();
                SearchService searchService = new SearchService(repository);
                JavaQuizGameFrame frame = new JavaQuizGameFrame(repository, searchService, false);
                frame.applyRoomConfig(init.getConfig());
                frame.setConnectedUsers(init.getUsers());

                String serverHost = host.trim();
                int serverPort = port;
                String roomId = init.getRoomId();
                String clientNick = nickname.trim();
                final long[] lastStartSequence = new long[]{0L};
                final boolean[] left = new boolean[]{false};

                frame.setAnswerSubmitAction((answer, round) -> {
                    try (QuizLobbyClient client = new QuizLobbyClient(serverHost, serverPort)) {
                        boolean correct = frame.isAnswerCorrectForRound(answer, round);
                        RoomRuntimeState state = client.submitAnswer(roomId, clientNick, round, correct, answer);
                        frame.applyRuntimeState(state);
                    } catch (Exception ignored) {
                    }
                });

                frame.setRoundTimeoutAction(round -> {
                    try (QuizLobbyClient client = new QuizLobbyClient(serverHost, serverPort)) {
                        RoomRuntimeState state = client.advanceRound(roomId, clientNick, round, "R" + round + " 시간 초과");
                        frame.applyRuntimeState(state);
                    } catch (Exception ignored) {
                    }
                });

                Timer pollTimer = new Timer(650, e -> {
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
                    private void leaveOnce() {
                        if (left[0]) {
                            return;
                        }
                        left[0] = true;
                        pollTimer.stop();
                        try (QuizLobbyClient client = new QuizLobbyClient(serverHost, serverPort)) {
                            client.leaveRoom(roomId, clientNick);
                        } catch (Exception ignored) {
                        }
                    }

                    @Override
                    public void windowClosing(WindowEvent e) {
                        leaveOnce();
                    }

                    @Override
                    public void windowClosed(WindowEvent e) {
                        leaveOnce();
                    }
                });

                frame.setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "클라이언트 시작 실패: " + e.getMessage());
            }
        });
    }
}
