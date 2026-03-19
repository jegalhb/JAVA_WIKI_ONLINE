package quizapp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class RoomRuntimeState implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String roomId;
    private final QuizRoomConfig config;
    private final List<String> users;
    private final long startSequence;
    private final long gameSeed;
    private final int currentRound;
    private final long roundSequence;
    private final String lastRoundEvent;

    public RoomRuntimeState(String roomId,
                            QuizRoomConfig config,
                            List<String> users,
                            long startSequence,
                            long gameSeed,
                            int currentRound,
                            long roundSequence,
                            String lastRoundEvent) {
        this.roomId = roomId;
        this.config = config;
        this.users = users == null ? new ArrayList<>() : new ArrayList<>(users);
        this.startSequence = startSequence;
        this.gameSeed = gameSeed;
        this.currentRound = currentRound;
        this.roundSequence = roundSequence;
        this.lastRoundEvent = lastRoundEvent == null ? "" : lastRoundEvent;
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

    public long getStartSequence() {
        return startSequence;
    }

    public long getGameSeed() {
        return gameSeed;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public long getRoundSequence() {
        return roundSequence;
    }

    public String getLastRoundEvent() {
        return lastRoundEvent;
    }
}
