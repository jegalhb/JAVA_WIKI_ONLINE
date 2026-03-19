package quizapp;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QuizLobbyServer {
    public static final int DEFAULT_PORT = 10001;

    private final int port;
    private final Map<String, RoomState> rooms = Collections.synchronizedMap(new HashMap<>());

    public QuizLobbyServer(int port) {
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("=== Quiz Lobby Server started on " + port + " ===");
            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket).start();
            }
        } catch (IOException e) {
            System.err.println("QuizLobbyServer error: " + e.getMessage());
        }
    }

    private List<QuizRoomInfo> snapshotRoomInfos() {
        List<QuizRoomInfo> infos = new ArrayList<>();
        synchronized (rooms) {
            for (Map.Entry<String, RoomState> entry : rooms.entrySet()) {
                RoomState room = entry.getValue();
                infos.add(new QuizRoomInfo(entry.getKey(), room.config, room.users.size()));
            }
        }
        return infos;
    }

    private RoomRuntimeState snapshotRoomState(String roomId) {
        synchronized (rooms) {
            RoomState room = rooms.get(roomId);
            if (room == null) {
                return null;
            }
            return toRuntimeState(roomId, room);
        }
    }

    private RoomRuntimeState toRuntimeState(String roomId, RoomState room) {
        return new RoomRuntimeState(
                roomId,
                room.config,
                room.users,
                room.startSequence,
                room.gameSeed,
                room.currentRound,
                room.roundSequence,
                room.lastRoundEvent
        );
    }

    private static class RoomState {
        private QuizRoomConfig config;
        private final String hostNickname;
        private final List<String> users = new ArrayList<>();
        private long startSequence;
        private long gameSeed;
        private int currentRound;
        private long roundSequence;
        private String lastRoundEvent = "";

        private RoomState(QuizRoomConfig config, String hostNickname) {
            this.config = config;
            this.hostNickname = hostNickname;
        }
    }

    private class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;

        private ClientHandler(Socket socket) {
            this.socket = socket;
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                while (true) {
                    String command = in.readUTF();
                    if ("CREATE_ROOM".equals(command)) {
                        handleCreateRoom();
                    } else if ("LIST_ROOMS".equals(command)) {
                        send("ROOM_LIST", snapshotRoomInfos());
                    } else if ("JOIN_ROOM".equals(command)) {
                        handleJoinRoom();
                    } else if ("START_GAME".equals(command)) {
                        handleStartGame();
                    } else if ("GET_ROOM_STATE".equals(command)) {
                        handleGetRoomState();
                    } else if ("UPDATE_ROOM_CONFIG".equals(command)) {
                        handleUpdateRoomConfig();
                    } else if ("SUBMIT_ANSWER".equals(command)) {
                        handleSubmitAnswer();
                    } else if ("ADVANCE_ROUND".equals(command)) {
                        handleAdvanceRound();
                    } else if ("LEAVE_ROOM".equals(command)) {
                        handleLeaveRoom();
                    } else if ("CLOSE_ROOM".equals(command)) {
                        handleCloseRoom();
                    } else if ("DISCONNECT".equals(command)) {
                        break;
                    } else {
                        send("ERROR", "지원하지 않는 명령: " + command);
                    }
                }
            } catch (EOFException ignored) {
            } catch (Exception e) {
                System.err.println("Client handler error: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }

        private void handleCreateRoom() throws Exception {
            Object configObj = in.readObject();
            String nick = in.readUTF();
            if (!(configObj instanceof QuizRoomConfig config)) {
                send("ERROR", "방 설정 데이터가 올바르지 않습니다.");
                return;
            }
            if (nick == null || nick.trim().isEmpty()) {
                send("ERROR", "닉네임이 필요합니다.");
                return;
            }

            String hostNick = nick.trim();
            String roomId = UUID.randomUUID().toString().substring(0, 8);
            RoomState room = new RoomState(config, hostNick);
            room.users.add(hostNick);

            synchronized (rooms) {
                rooms.put(roomId, room);
            }

            send("ROOM_CREATED", new QuizSessionInit(roomId, config, room.users, true));
        }

        private void handleJoinRoom() throws Exception {
            String roomId = in.readUTF();
            String nick = in.readUTF();
            if (roomId == null || roomId.trim().isEmpty()) {
                send("ERROR", "방 ID가 비어 있습니다.");
                return;
            }
            if (nick == null || nick.trim().isEmpty()) {
                send("ERROR", "닉네임이 필요합니다.");
                return;
            }

            String normalizedNick = nick.trim();
            RoomState room;
            synchronized (rooms) {
                room = rooms.get(roomId);
                if (room == null) {
                    send("ERROR", "방이 존재하지 않습니다.");
                    return;
                }
                if (!room.users.contains(normalizedNick) && room.users.size() >= room.config.getMaxPlayers()) {
                    send("ERROR", "방이 가득 찼습니다.");
                    return;
                }
                if (!room.users.contains(normalizedNick)) {
                    room.users.add(normalizedNick);
                    room.lastRoundEvent = normalizedNick + " 님이 입장했습니다.";
                }
            }

            send("JOIN_OK", new QuizSessionInit(roomId, room.config, room.users, false));
        }

        private void handleStartGame() throws Exception {
            String roomId = in.readUTF();
            String nick = in.readUTF();

            synchronized (rooms) {
                RoomState room = rooms.get(roomId);
                if (room == null) {
                    send("ERROR", "방이 존재하지 않습니다.");
                    return;
                }
                if (!room.hostNickname.equals(nick)) {
                    send("ERROR", "호스트만 게임 시작이 가능합니다.");
                    return;
                }

                room.startSequence += 1;
                room.gameSeed = System.currentTimeMillis();
                room.currentRound = 1;
                room.roundSequence += 1;
                room.lastRoundEvent = "게임이 시작되었습니다.";
                send("START_OK", toRuntimeState(roomId, room));
            }
        }

        private void handleGetRoomState() throws Exception {
            String roomId = in.readUTF();
            RoomRuntimeState state = snapshotRoomState(roomId);
            if (state == null) {
                send("ERROR", "방이 존재하지 않습니다.");
                return;
            }
            send("ROOM_STATE", state);
        }

        private void handleUpdateRoomConfig() throws Exception {
            String roomId = in.readUTF();
            String nick = in.readUTF();
            Object configObj = in.readObject();
            if (!(configObj instanceof QuizRoomConfig config)) {
                send("ERROR", "방 설정 데이터가 올바르지 않습니다.");
                return;
            }

            synchronized (rooms) {
                RoomState room = rooms.get(roomId);
                if (room == null) {
                    send("ERROR", "방이 존재하지 않습니다.");
                    return;
                }
                if (!room.hostNickname.equals(nick)) {
                    send("ERROR", "호스트만 방 설정 변경이 가능합니다.");
                    return;
                }
                room.config = config;
                if (room.users.size() > config.getMaxPlayers()) {
                    room.users.subList(config.getMaxPlayers(), room.users.size()).clear();
                }
                send("UPDATE_OK", toRuntimeState(roomId, room));
            }
        }

        private void handleSubmitAnswer() throws Exception {
            String roomId = in.readUTF();
            String nick = in.readUTF();
            int round = in.readInt();
            boolean correct = in.readBoolean();
            String answer = in.readUTF();

            synchronized (rooms) {
                RoomState room = rooms.get(roomId);
                if (room == null) {
                    send("ERROR", "방이 존재하지 않습니다.");
                    return;
                }

                if (!room.users.contains(nick)) {
                    send("ERROR", "현재 방 참여자가 아닙니다.");
                    return;
                }

                if (room.currentRound <= 0 || round != room.currentRound) {
                    send("SUBMIT_OK", toRuntimeState(roomId, room));
                    return;
                }

                if (!correct) {
                    send("SUBMIT_OK", toRuntimeState(roomId, room));
                    return;
                }

                String answerText = answer == null ? "" : answer.trim();
                if (answerText.isEmpty()) {
                    answerText = "(정답)";
                }

                room.lastRoundEvent = String.format("R%d %s 정답: %s", room.currentRound, nick, answerText);
                room.roundSequence += 1;

                if (room.currentRound >= room.config.getTotalRounds()) {
                    room.currentRound = room.config.getTotalRounds() + 1;
                } else {
                    room.currentRound += 1;
                }
                send("SUBMIT_OK", toRuntimeState(roomId, room));
            }
        }

        private void handleAdvanceRound() throws Exception {
            String roomId = in.readUTF();
            String nick = in.readUTF();
            int round = in.readInt();
            String reason = in.readUTF();

            synchronized (rooms) {
                RoomState room = rooms.get(roomId);
                if (room == null) {
                    send("ERROR", "방이 존재하지 않습니다.");
                    return;
                }

                if (room.currentRound <= 0 || round != room.currentRound) {
                    send("ADVANCE_OK", toRuntimeState(roomId, room));
                    return;
                }

                room.lastRoundEvent = (reason == null || reason.trim().isEmpty())
                        ? String.format("R%d 시간 종료", room.currentRound)
                        : reason.trim();
                room.roundSequence += 1;

                if (room.currentRound >= room.config.getTotalRounds()) {
                    room.currentRound = room.config.getTotalRounds() + 1;
                } else {
                    room.currentRound += 1;
                }
                send("ADVANCE_OK", toRuntimeState(roomId, room));
            }
        }

        private void handleCloseRoom() throws Exception {
            String roomId = in.readUTF();
            String nick = in.readUTF();
            synchronized (rooms) {
                RoomState room = rooms.get(roomId);
                if (room == null) {
                    send("ERROR", "방이 존재하지 않습니다.");
                    return;
                }
                if (!room.hostNickname.equals(nick)) {
                    send("ERROR", "호스트만 방 종료가 가능합니다.");
                    return;
                }
                rooms.remove(roomId);
            }
            send("CLOSE_OK", "OK");
        }

        private void handleLeaveRoom() throws Exception {
            String roomId = in.readUTF();
            String nick = in.readUTF();
            synchronized (rooms) {
                RoomState room = rooms.get(roomId);
                if (room == null) {
                    send("LEAVE_OK", "OK");
                    return;
                }
                room.users.remove(nick);
                room.lastRoundEvent = nick + " 님이 퇴장했습니다.";
                if (room.users.isEmpty()) {
                    rooms.remove(roomId);
                }
            }
            send("LEAVE_OK", "OK");
        }

        private void send(String type, Object payload) {
            try {
                out.writeUTF(type);
                if (payload != null) {
                    out.writeObject(payload);
                }
                out.flush();
                out.reset();
            } catch (IOException ignored) {
            }
        }
    }

    public static void main(String[] args) {
        new QuizLobbyServer(DEFAULT_PORT).start();
    }
}
