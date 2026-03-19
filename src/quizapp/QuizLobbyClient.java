package quizapp;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class QuizLobbyClient implements Closeable {
    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    public QuizLobbyClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    public QuizSessionInit createRoom(QuizRoomConfig config, String nickname) throws Exception {
        out.writeUTF("CREATE_ROOM");
        out.writeObject(config);
        out.writeUTF(nickname);
        out.flush();

        String response = in.readUTF();
        if ("ROOM_CREATED".equals(response)) {
            return (QuizSessionInit) in.readObject();
        }
        throwError(response);
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<QuizRoomInfo> listRooms() throws Exception {
        out.writeUTF("LIST_ROOMS");
        out.flush();

        String response = in.readUTF();
        if ("ROOM_LIST".equals(response)) {
            Object obj = in.readObject();
            if (obj instanceof List) {
                return (List<QuizRoomInfo>) obj;
            }
            return new ArrayList<>();
        }
        throwError(response);
        return new ArrayList<>();
    }

    public QuizSessionInit joinRoom(String roomId, String nickname) throws Exception {
        out.writeUTF("JOIN_ROOM");
        out.writeUTF(roomId);
        out.writeUTF(nickname);
        out.flush();

        String response = in.readUTF();
        if ("JOIN_OK".equals(response)) {
            return (QuizSessionInit) in.readObject();
        }
        throwError(response);
        return null;
    }

    public RoomRuntimeState startGame(String roomId, String nickname) throws Exception {
        out.writeUTF("START_GAME");
        out.writeUTF(roomId);
        out.writeUTF(nickname);
        out.flush();

        String response = in.readUTF();
        if ("START_OK".equals(response)) {
            return (RoomRuntimeState) in.readObject();
        }
        throwError(response);
        return null;
    }

    public RoomRuntimeState getRoomState(String roomId) throws Exception {
        out.writeUTF("GET_ROOM_STATE");
        out.writeUTF(roomId);
        out.flush();

        String response = in.readUTF();
        if ("ROOM_STATE".equals(response)) {
            return (RoomRuntimeState) in.readObject();
        }
        throwError(response);
        return null;
    }

    public RoomRuntimeState updateRoomConfig(String roomId, String nickname, QuizRoomConfig config) throws Exception {
        out.writeUTF("UPDATE_ROOM_CONFIG");
        out.writeUTF(roomId);
        out.writeUTF(nickname);
        out.writeObject(config);
        out.flush();

        String response = in.readUTF();
        if ("UPDATE_OK".equals(response)) {
            return (RoomRuntimeState) in.readObject();
        }
        throwError(response);
        return null;
    }

    public RoomRuntimeState submitAnswer(String roomId, String nickname, int round, boolean correct, String answer) throws Exception {
        out.writeUTF("SUBMIT_ANSWER");
        out.writeUTF(roomId);
        out.writeUTF(nickname);
        out.writeInt(round);
        out.writeBoolean(correct);
        out.writeUTF(answer == null ? "" : answer);
        out.flush();

        String response = in.readUTF();
        if ("SUBMIT_OK".equals(response)) {
            return (RoomRuntimeState) in.readObject();
        }
        throwError(response);
        return null;
    }

    public RoomRuntimeState advanceRound(String roomId, String nickname, int round, String reason) throws Exception {
        out.writeUTF("ADVANCE_ROUND");
        out.writeUTF(roomId);
        out.writeUTF(nickname);
        out.writeInt(round);
        out.writeUTF(reason == null ? "" : reason);
        out.flush();

        String response = in.readUTF();
        if ("ADVANCE_OK".equals(response)) {
            return (RoomRuntimeState) in.readObject();
        }
        throwError(response);
        return null;
    }

    public void leaveRoom(String roomId, String nickname) throws Exception {
        out.writeUTF("LEAVE_ROOM");
        out.writeUTF(roomId);
        out.writeUTF(nickname);
        out.flush();

        String response = in.readUTF();
        if ("LEAVE_OK".equals(response)) {
            in.readObject();
            return;
        }
        throwError(response);
    }

    public void closeRoom(String roomId, String nickname) throws Exception {
        out.writeUTF("CLOSE_ROOM");
        out.writeUTF(roomId);
        out.writeUTF(nickname);
        out.flush();

        String response = in.readUTF();
        if ("CLOSE_OK".equals(response)) {
            in.readObject();
            return;
        }
        throwError(response);
    }

    private void throwError(String response) throws Exception {
        if ("ERROR".equals(response)) {
            throw new IllegalStateException((String) in.readObject());
        }
        throw new IllegalStateException("예상하지 못한 응답: " + response);
    }

    @Override
    public void close() throws IOException {
        try {
            out.writeUTF("DISCONNECT");
            out.flush();
        } catch (Exception ignored) {
        }
        try {
            in.close();
        } catch (Exception ignored) {
        }
        try {
            out.close();
        } catch (Exception ignored) {
        }
        socket.close();
    }
}
