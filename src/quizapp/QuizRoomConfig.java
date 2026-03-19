package quizapp;

import java.io.Serializable;

public class QuizRoomConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String roomName;
    private final QuizMode mode;
    private final int totalRounds;
    private final int totalGameTimeSeconds;
    private final int questionTimeSeconds;
    private final int maxPlayers;

    public QuizRoomConfig(String roomName, QuizMode mode, int totalRounds, int totalGameTimeSeconds, int questionTimeSeconds, int maxPlayers) {
        this.roomName = roomName;
        this.mode = mode == null ? QuizMode.CHOSEONG : mode;
        this.totalRounds = Math.max(1, totalRounds);
        this.totalGameTimeSeconds = Math.max(1, totalGameTimeSeconds);
        this.questionTimeSeconds = Math.max(1, questionTimeSeconds);
        this.maxPlayers = Math.max(1, maxPlayers);
    }

    public String getRoomName() {
        return roomName;
    }

    public QuizMode getMode() {
        return mode;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    public int getTotalGameTimeSeconds() {
        return totalGameTimeSeconds;
    }

    public int getQuestionTimeSeconds() {
        return questionTimeSeconds;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }
}
