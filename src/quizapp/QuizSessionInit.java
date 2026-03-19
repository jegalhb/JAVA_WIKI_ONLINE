package quizapp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class QuizSessionInit implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String roomId;
    private final QuizRoomConfig config;
    private final List<String> users;
    private final boolean host;

    public QuizSessionInit(String roomId, QuizRoomConfig config, List<String> users, boolean host) {
        this.roomId = roomId;
        this.config = config;
        this.users = users == null ? new ArrayList<>() : new ArrayList<>(users);
        this.host = host;
    }

    public String getRoomId() {
        return roomId;
    }

    public QuizRoomConfig getConfig() {
        return config;
    }

    public List<String> getUsers() {
        return new ArrayList<>(users);
    }

    public boolean isHost() {
        return host;
    }
}
