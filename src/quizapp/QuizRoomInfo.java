package quizapp;

import java.io.Serializable;

public class QuizRoomInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String roomId;
    private final QuizRoomConfig config;
    private final int currentPlayers;

    public QuizRoomInfo(String roomId, QuizRoomConfig config, int currentPlayers) {
        this.roomId = roomId;
        this.config = config;
        this.currentPlayers = currentPlayers;
    }

    public String getRoomId() {
        return roomId;
    }

    public QuizRoomConfig getConfig() {
        return config;
    }

    public int getCurrentPlayers() {
        return currentPlayers;
    }

    @Override
    public String toString() {
        String modeText = config.getMode() == QuizMode.OX ? "OX퀴즈" : "초성퀴즈";
        return String.format("[%s] %s | 제한시간(전체/문제) %d/%d초 | 참여 %d/%d",
                modeText,
                config.getRoomName(),
                config.getTotalGameTimeSeconds(),
                config.getQuestionTimeSeconds(),
                currentPlayers,
                config.getMaxPlayers());
    }
}