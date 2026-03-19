
package quizapp;

import Reproject.Concept;
import Reproject.ConceptRepository;
import Reproject.SearchService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

public class JavaQuizGameFrame extends JFrame {
    private static final Color BG_GAME = new Color(76, 165, 79);
    private static final Color BG_PATTERN = new Color(93, 179, 96);
    private static final Color PANEL_DARK = new Color(44, 34, 24);
    private static final Color PANEL_GOLD = new Color(206, 165, 78);
    private static final Color ACCENT = new Color(82, 128, 237);

    private static final char[] CHOSEONG = {
            '\u3131', '\u3132', '\u3134', '\u3137', '\u3138', '\u3139',
            '\u3141', '\u3142', '\u3143', '\u3145', '\u3146', '\u3147',
            '\u3148', '\u3149', '\u314A', '\u314B', '\u314C', '\u314D', '\u314E'
    };

    private static final Icon USER_ICON = new Icon() {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(24, 24, 24));
            g2.fillOval(x + 5, y + 1, 10, 10);
            g2.fillRoundRect(x + 2, y + 10, 16, 9, 8, 8);
            g2.dispose();
        }

        @Override
        public int getIconWidth() { return 20; }

        @Override
        public int getIconHeight() { return 20; }
    };

    private final ConceptRepository repository;
    private final SearchService searchService;
    private final boolean hostMode;

    private final JTextArea gameLogArea = new JTextArea();
    private final JTextField answerField = new JTextField();
    private final JButton submitButton = new JButton("제출");
    private final JTextArea roundHelpArea = new JTextArea();
    private final JTextArea learningNoteArea = new JTextArea();

    private final JLabel gameInfoLabel = new JLabel("", SwingConstants.RIGHT);
    private final JLabel roundLabel = new JLabel("라운드 1 / 10", SwingConstants.CENTER);
    private final JLabel questionLabel = new JLabel("준비 완료! 시작 버튼을 눌러주세요", SwingConstants.CENTER);
    private final JLabel oxModeLabel = new JLabel("OX퀴즈", SwingConstants.CENTER);
    private final JPanel oxChoicePanel = new JPanel(new GridLayout(1, 2, 16, 0));
    private final JButton oxButtonO = new JButton("O");
    private final JButton oxButtonX = new JButton("X");
    private final JLabel configuredTimeLabel = new JLabel("전체: 300초 / 문제: 20초", SwingConstants.CENTER);
    private final JProgressBar totalTimerBar = new JProgressBar(0, 100);
    private final JProgressBar timerBar = new JProgressBar(0, 100);

    private final List<String> connectedUsers = new ArrayList<>();
    private final List<ChoseongQuestion> choseongQuestions = new ArrayList<>();
    private final List<OxQuestion> oxQuestions = new ArrayList<>();

    private Timer roundTimer;
    private Timer totalGameTimer;
    private long gameStartMillis;

    private boolean gameActive;
    private int score;
    private int secondsLeft;
    private int totalSecondsLeft;

    private String currentGameName = "JAVA_WIKI_ONLINE";
    private String currentGameMode = "대기";
    private String currentRoomName = "JAVA 학습방";
    private String localNickname = "로컬 사용자";

    private int currentRound = 1;
    private int totalRounds = 10;
    private int totalGameTimeSeconds = 300;
    private int questionTimeSeconds = 20;
    private int maxPlayers = 8;

    private QuizMode quizMode = QuizMode.CHOSEONG;

    private Runnable startGameAction;
    private Runnable hostConfigUpdatedAction;
    private BiConsumer<String, Integer> answerSubmitAction;
    private IntConsumer roundTimeoutAction;
    private long appliedRoundSequence;
    private Long pendingGameSeed;

    public JavaQuizGameFrame(ConceptRepository repository, SearchService searchService) {
        this(repository, searchService, true);
    }

    public JavaQuizGameFrame(ConceptRepository repository, SearchService searchService, boolean hostMode) {
        this.repository = repository;
        this.searchService = searchService;
        this.hostMode = hostMode;
        this.startGameAction = this::startGame;

        setTitle("Java Quiz Arena");
        setSize(1180, 780);
        setMinimumSize(new Dimension(1000, 680));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        connectedUsers.add(localNickname);

        JPanel root = new PatternPanel();
        root.setLayout(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(10, 12, 12, 12));
        setContentPane(root);

        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(buildCenterPane(), BorderLayout.CENTER);
        root.add(buildBottomPane(), BorderLayout.SOUTH);

        totalSecondsLeft = totalGameTimeSeconds;
        refreshGameInfoBar();
        updateConfiguredTimeLabel();
        updateTotalTimerBar();
    }

    private JComponent buildTopBar() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.setOpaque(false);

        JButton helpButton = createTopButton("?");
        helpButton.addActionListener(e -> showHowToPlayDialog());

        JButton usersButton = createIconTopButton(USER_ICON);
        usersButton.setToolTipText("현재 접속자 보기");
        usersButton.addActionListener(e -> showConnectedUsersDialog());

        JButton settingsButton = createTopButton("설정");
        settingsButton.addActionListener(e -> showGameSettingsDialog());

        JButton dictionaryButton = new JButton("사전");
        styleActionTab(dictionaryButton, new Color(124, 201, 126));
        dictionaryButton.addActionListener(e -> showDictionaryDialog());

        JButton startButton = new JButton("게임 시작");
        styleActionTab(startButton, new Color(125, 180, 255));
        startButton.addActionListener(e -> startGameAction.run());

        JButton exitButton = new JButton("나가기");
        styleActionTab(exitButton, new Color(238, 162, 162));
        exitButton.addActionListener(e -> exitGame());

        top.add(helpButton);
        top.add(usersButton);
        if (hostMode) top.add(settingsButton);
        top.add(dictionaryButton);
        top.add(startButton);
        top.add(exitButton);

        JPanel infoBar = new JPanel(new BorderLayout(10, 0));
        infoBar.setBackground(new Color(231, 234, 239));
        infoBar.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(200, 204, 210), 1, true),
                new EmptyBorder(5, 10, 5, 10)
        ));
        JLabel domain = new JLabel("JAVA_WIKI_ONLINE", SwingConstants.CENTER);
        domain.setFont(new Font("맑은 고딕", Font.BOLD, 12));
        gameInfoLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        infoBar.add(domain, BorderLayout.CENTER);
        infoBar.add(gameInfoLabel, BorderLayout.EAST);

        JPanel wrapper = new JPanel(new BorderLayout(0, 6));
        wrapper.setOpaque(false);
        wrapper.add(top, BorderLayout.NORTH);
        wrapper.add(infoBar, BorderLayout.SOUTH);
        return wrapper;
    }

    private JComponent buildCenterPane() {
        JPanel center = new JPanel(new BorderLayout(12, 10));
        center.setOpaque(false);
        center.add(buildGameBoard(), BorderLayout.CENTER);
        center.add(buildHelpPanel(), BorderLayout.EAST);
        return center;
    }

    private JComponent buildGameBoard() {
        JPanel boardWrap = new JPanel(new BorderLayout());
        boardWrap.setOpaque(false);
        boardWrap.setBorder(new EmptyBorder(18, 12, 10, 12));

        JPanel board = new JPanel(new BorderLayout(0, 10));
        board.setBackground(PANEL_DARK);
        board.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(PANEL_GOLD, 8, true),
                new EmptyBorder(16, 18, 16, 18)
        ));

        roundLabel.setForeground(new Color(240, 225, 160));
        roundLabel.setFont(new Font("맑은 고딕", Font.BOLD, 18));

        oxModeLabel.setForeground(new Color(255, 232, 145));
        oxModeLabel.setFont(new Font("맑은 고딕", Font.BOLD, 22));
        oxModeLabel.setVisible(false);

        questionLabel.setForeground(Color.WHITE);
        questionLabel.setFont(new Font("맑은 고딕", Font.BOLD, 27));
        questionLabel.setVerticalAlignment(SwingConstants.TOP);

        oxButtonO.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        oxButtonX.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        oxButtonO.setFocusable(false);
        oxButtonX.setFocusable(false);
        oxButtonO.setBackground(new Color(116, 181, 255));
        oxButtonX.setBackground(new Color(255, 153, 153));
        oxButtonO.addActionListener(e -> submitOxChoice("O"));
        oxButtonX.addActionListener(e -> submitOxChoice("X"));
        oxChoicePanel.setOpaque(false);
        oxChoicePanel.add(oxButtonO);
        oxChoicePanel.add(oxButtonX);
        oxChoicePanel.setVisible(false);

        JPanel centerQuestion = new JPanel();
        centerQuestion.setOpaque(false);
        centerQuestion.setLayout(new BoxLayout(centerQuestion, BoxLayout.Y_AXIS));
        oxModeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        questionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        oxChoicePanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerQuestion.add(oxModeLabel);
        centerQuestion.add(Box.createVerticalStrut(8));
        centerQuestion.add(questionLabel);
        centerQuestion.add(Box.createVerticalStrut(18));
        centerQuestion.add(oxChoicePanel);

        configuredTimeLabel.setForeground(new Color(218, 226, 245));
        configuredTimeLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 13));

        totalTimerBar.setStringPainted(true);
        totalTimerBar.setBackground(new Color(35, 30, 22));
        totalTimerBar.setForeground(new Color(233, 145, 62));

        timerBar.setStringPainted(true);
        timerBar.setBackground(new Color(24, 31, 46));
        timerBar.setForeground(ACCENT);

        JPanel bottomStatus = new JPanel();
        bottomStatus.setOpaque(false);
        bottomStatus.setLayout(new BoxLayout(bottomStatus, BoxLayout.Y_AXIS));
        bottomStatus.add(configuredTimeLabel);
        bottomStatus.add(Box.createVerticalStrut(4));
        bottomStatus.add(totalTimerBar);
        bottomStatus.add(Box.createVerticalStrut(4));
        bottomStatus.add(timerBar);

        board.add(roundLabel, BorderLayout.NORTH);
        board.add(centerQuestion, BorderLayout.CENTER);
        board.add(bottomStatus, BorderLayout.SOUTH);

        boardWrap.add(board, BorderLayout.CENTER);
        return boardWrap;
    }

    private JComponent buildHelpPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setPreferredSize(new Dimension(380, 0));
        panel.setBackground(new Color(248, 250, 255));
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(189, 202, 227), 1, true),
                new EmptyBorder(10, 10, 10, 10)
        ));

        roundHelpArea.setEditable(false);
        roundHelpArea.setLineWrap(true);
        roundHelpArea.setWrapStyleWord(true);
        learningNoteArea.setEditable(false);
        learningNoteArea.setLineWrap(true);
        learningNoteArea.setWrapStyleWord(true);

        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.setOpaque(false);
        top.add(new JLabel("문제 해설"), BorderLayout.NORTH);
        top.add(new JScrollPane(roundHelpArea), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        bottom.setOpaque(false);
        bottom.add(new JLabel("학습 노트"), BorderLayout.NORTH);
        bottom.add(new JScrollPane(learningNoteArea), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        split.setDividerLocation(250);
        split.setResizeWeight(0.58);

        panel.add(new JLabel("라운드 학습"), BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildBottomPane() {
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.setOpaque(false);

        gameLogArea.setEditable(false);
        gameLogArea.setLineWrap(true);
        gameLogArea.setWrapStyleWord(true);
        gameLogArea.setRows(6);
        gameLogArea.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        gameLogArea.setText("[알림] 게임 대기 중입니다.\n");

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        answerField.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        submitButton.addActionListener(e -> submitAnswer());
        answerField.addActionListener(e -> submitAnswer());
        inputRow.add(answerField, BorderLayout.CENTER);
        inputRow.add(submitButton, BorderLayout.EAST);

        bottom.add(new JScrollPane(gameLogArea), BorderLayout.CENTER);
        bottom.add(inputRow, BorderLayout.SOUTH);
        return bottom;
    }

    private JButton createIconTopButton(Icon icon) {
        JButton button = new JButton(icon);
        button.setPreferredSize(new Dimension(36, 32));
        button.setMargin(new Insets(4, 6, 4, 6));
        button.setFocusable(false);
        button.setBackground(new Color(236, 236, 236));
        return button;
    }

    private JButton createTopButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        button.setMargin(new Insets(6, 10, 6, 10));
        button.setFocusable(false);
        button.setBackground(new Color(236, 236, 236));
        return button;
    }

    private void styleActionTab(JButton button, Color bg) {
        button.setFont(new Font("맑은 고딕", Font.BOLD, 15));
        button.setBackground(bg);
        button.setFocusable(false);
        button.setBorder(new EmptyBorder(10, 24, 10, 24));
    }
    public void applyRoomConfig(QuizRoomConfig config) {
        if (config == null) return;
        currentRoomName = safeText(config.getRoomName(), currentRoomName);
        quizMode = config.getMode() == null ? QuizMode.CHOSEONG : config.getMode();
        totalRounds = Math.max(1, config.getTotalRounds());
        totalGameTimeSeconds = Math.max(1, config.getTotalGameTimeSeconds());
        questionTimeSeconds = Math.max(1, config.getQuestionTimeSeconds());
        maxPlayers = Math.max(1, config.getMaxPlayers());
        currentGameMode = quizMode == QuizMode.OX ? "OX 퀴즈" : "초성 퀴즈";

        if (!gameActive) totalSecondsLeft = totalGameTimeSeconds;

        refreshGameInfoBar();
        updateConfiguredTimeLabel();
        updateTotalTimerBar();
        updateInputModeUI();
    }

    public void setConnectedUsers(List<String> users) {
        connectedUsers.clear();
        if (users != null) {
            for (String u : users) {
                if (u != null && !u.trim().isEmpty()) connectedUsers.add(u.trim());
            }
        }
        if (connectedUsers.isEmpty()) connectedUsers.add("접속자 없음");
        refreshGameInfoBar();
    }

    public void setStartGameAction(Runnable startGameAction) { this.startGameAction = startGameAction == null ? this::startGame : startGameAction; }
    public void setHostConfigUpdatedAction(Runnable hostConfigUpdatedAction) { this.hostConfigUpdatedAction = hostConfigUpdatedAction; }
    public void setAnswerSubmitAction(BiConsumer<String, Integer> answerSubmitAction) { this.answerSubmitAction = answerSubmitAction; }
    public void setRoundTimeoutAction(IntConsumer roundTimeoutAction) { this.roundTimeoutAction = roundTimeoutAction; }
    public void startGameWithSeed(long seed) { pendingGameSeed = seed; startGame(); }
    public QuizRoomConfig getCurrentRoomConfig() { return new QuizRoomConfig(currentRoomName, quizMode, totalRounds, totalGameTimeSeconds, questionTimeSeconds, maxPlayers); }
    public String getLocalNickname() { return localNickname; }

    public void applyRuntimeState(RoomRuntimeState state) {
        if (state == null || state.getRoundSequence() <= appliedRoundSequence) return;
        appliedRoundSequence = state.getRoundSequence();
        if (state.getLastRoundEvent() != null && !state.getLastRoundEvent().isEmpty()) {
            gameLogArea.append("[동기화] " + state.getLastRoundEvent() + "\n");
        }
        if (!gameActive) return;
        if (state.getCurrentRound() > totalRounds) { finishGame(); return; }
        if (state.getCurrentRound() >= 1 && state.getCurrentRound() != currentRound) {
            currentRound = state.getCurrentRound();
            startRound();
        }
    }

    public boolean isAnswerCorrectForRound(String answer, int round) {
        if (answer == null || round < 1 || round > totalRounds) return false;
        if (quizMode == QuizMode.OX) return round <= oxQuestions.size() && oxQuestions.get(round - 1).isCorrect(answer);
        if (round > choseongQuestions.size()) return false;
        ChoseongQuestion q = choseongQuestions.get(round - 1);
        String user = normalizeAnswer(answer);
        String full = normalizeAnswer(q.answer);
        String shortA = normalizeAnswer(stripFromFirstParenthesis(q.answer));
        return user.equals(full) || (!shortA.isEmpty() && user.equals(shortA));
    }

    private void showHowToPlayDialog() {
        JOptionPane.showMessageDialog(this,
                "1) 라운드 문제를 확인하고 제출합니다.\n2) 사전에서 개념을 검색해 복습할 수 있습니다.\n3) 멀티플레이에서는 같은 문제를 동시에 풉니다.",
                "게임 방법",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void showConnectedUsersDialog() {
        JList<String> userList = new JList<>(connectedUsers.toArray(new String[0]));
        userList.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        JOptionPane.showMessageDialog(this, new JScrollPane(userList), "현재 접속자", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showDictionaryDialog() {
        JDialog dialog = new JDialog(this, "자바 위키 사전", false);
        dialog.setSize(520, 620);
        dialog.setLocationRelativeTo(this);

        JTextField searchField = new JTextField();
        DefaultListModel<Concept> model = new DefaultListModel<>();
        JList<Concept> list = new JList<>(model);
        JTextArea detail = new JTextArea();
        detail.setEditable(false);

        Runnable runSearch = () -> {
            String keyword = searchField.getText() == null ? "" : searchField.getText().trim();
            List<Concept> results = keyword.isEmpty() ? repository.findAll() : searchService.searchByKeyword(keyword);
            model.clear();
            results.stream().sorted(Comparator.comparing(Concept::getTitle, String.CASE_INSENSITIVE_ORDER)).forEach(model::addElement);
            if (model.isEmpty()) detail.setText("검색 결과가 없습니다.");
            else list.setSelectedIndex(0);
        };

        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            Concept c = list.getSelectedValue();
            if (c == null) return;
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(c.getCategory()).append("] ").append(c.getTitle()).append("\n\n");
            for (String line : c.getDescriptionLines()) sb.append(line == null ? "" : line).append("\n");
            detail.setText(sb.toString());
        });

        JButton searchButton = new JButton("검색");
        searchButton.addActionListener(e -> runSearch.run());
        searchField.addActionListener(e -> runSearch.run());

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.add(searchField, BorderLayout.CENTER);
        top.add(searchButton, BorderLayout.EAST);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(list), new JScrollPane(detail));
        split.setDividerLocation(220);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.add(top, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        dialog.setContentPane(root);

        runSearch.run();
        dialog.setVisible(true);
    }

    private void showGameSettingsDialog() {
        if (!hostMode) return;
        if (gameActive) {
            JOptionPane.showMessageDialog(this, "게임 진행 중에는 설정 변경이 불가합니다.");
            return;
        }

        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
        JComboBox<String> mode = new JComboBox<>(new String[]{"초성 퀴즈", "OX 퀴즈"});
        mode.setSelectedIndex(quizMode == QuizMode.OX ? 1 : 0);
        JTextField room = new JTextField(currentRoomName);
        JTextField rounds = new JTextField(String.valueOf(totalRounds));
        JTextField total = new JTextField(String.valueOf(totalGameTimeSeconds));
        JTextField per = new JTextField(String.valueOf(questionTimeSeconds));
        JTextField max = new JTextField(String.valueOf(maxPlayers));

        panel.add(new JLabel("방 이름")); panel.add(room);
        panel.add(new JLabel("모드")); panel.add(mode);
        panel.add(new JLabel("라운드")); panel.add(rounds);
        panel.add(new JLabel("전체 시간(초)")); panel.add(total);
        panel.add(new JLabel("문제 시간(초)")); panel.add(per);
        panel.add(new JLabel("최대 인원")); panel.add(max);

        if (JOptionPane.showConfirmDialog(this, panel, "게임 설정", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;

        currentRoomName = safeText(room.getText(), currentRoomName);
        quizMode = mode.getSelectedIndex() == 1 ? QuizMode.OX : QuizMode.CHOSEONG;
        totalRounds = parsePositiveInt(rounds.getText(), totalRounds);
        totalGameTimeSeconds = parsePositiveInt(total.getText(), totalGameTimeSeconds);
        questionTimeSeconds = parsePositiveInt(per.getText(), questionTimeSeconds);
        maxPlayers = parsePositiveInt(max.getText(), maxPlayers);
        currentGameMode = quizMode == QuizMode.OX ? "OX 퀴즈" : "초성 퀴즈";
        totalSecondsLeft = totalGameTimeSeconds;

        refreshGameInfoBar();
        updateConfiguredTimeLabel();
        updateTotalTimerBar();
        updateInputModeUI();

        if (hostConfigUpdatedAction != null) hostConfigUpdatedAction.run();
    }

    private void exitGame() {
        int confirm = JOptionPane.showConfirmDialog(this, "게임을 나가시겠습니까?", "나가기 확인", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }
    private void startGame() {
        if (gameActive) return;

        List<Concept> source = buildCandidateConcepts(repository.findAll());
        if (source.isEmpty()) {
            JOptionPane.showMessageDialog(this, "출제 가능한 데이터가 없습니다.");
            return;
        }

        long seed = pendingGameSeed != null ? pendingGameSeed : System.currentTimeMillis();
        pendingGameSeed = null;

        choseongQuestions.clear();
        oxQuestions.clear();

        if (quizMode == QuizMode.OX) {
            prepareOxGame(source, seed);
            if (oxQuestions.isEmpty()) return;
        } else {
            prepareChoseongGame(source, seed);
            if (choseongQuestions.isEmpty()) return;
        }

        gameActive = true;
        score = 0;
        currentRound = 1;
        gameStartMillis = System.currentTimeMillis();
        totalSecondsLeft = totalGameTimeSeconds;
        updateTotalTimerBar();
        startTotalGameTimer();
        startRound();
    }

    private List<Concept> buildCandidateConcepts(List<Concept> source) {
        List<Concept> filtered = new ArrayList<>();
        for (Concept c : source) {
            if (!isMethodConcept(c)) filtered.add(c);
        }
        filtered.sort(Comparator.comparing((Concept c) -> c.getId() == null ? "" : c.getId())
                .thenComparing(c -> c.getTitle() == null ? "" : c.getTitle()));
        return filtered;
    }

    private void prepareChoseongGame(List<Concept> source, long seed) {
        List<Concept> shuffled = new ArrayList<>(source);
        Collections.shuffle(shuffled, new Random(seed));
        int i = 0;
        while (choseongQuestions.size() < totalRounds && !shuffled.isEmpty()) {
            Concept c = shuffled.get(i % shuffled.size());
            String answer = c.getTitle() == null ? "" : c.getTitle().trim();
            if (!answer.isEmpty()) {
                choseongQuestions.add(new ChoseongQuestion(answer, toHint(answer), buildConceptExplanation(c), stripFromFirstParenthesis(answer)));
            }
            i++;
            if (i > shuffled.size() * 3) break;
        }
        if (!choseongQuestions.isEmpty()) {
            int fill = 0;
            while (choseongQuestions.size() < totalRounds) {
                choseongQuestions.add(choseongQuestions.get(fill % choseongQuestions.size()));
                fill++;
            }
        }
    }

    private void prepareOxGame(List<Concept> source, long seed) {
        List<Concept> shuffled = new ArrayList<>(source);
        Random r = new Random(seed ^ 0x9E3779B97F4A7C15L);
        Collections.shuffle(shuffled, r);
        List<String> cats = List.of("basic", "intermediate", "advanced");

        int i = 0;
        while (oxQuestions.size() < totalRounds && !shuffled.isEmpty()) {
            Concept c = shuffled.get(i % shuffled.size());
            String title = c.getTitle() == null ? "" : c.getTitle().trim();
            String cat = normalizeCategory(c.getCategory());
            if (!title.isEmpty() && !cat.isEmpty()) {
                if (r.nextBoolean()) {
                    oxQuestions.add(new OxQuestion("\"" + title + "\" 는 \"" + cat + "\" 분류다.", true, "O", buildConceptExplanation(c), title));
                } else {
                    String fake = cat;
                    for (int g = 0; g < 8 && fake.equals(cat); g++) fake = cats.get(r.nextInt(cats.size()));
                    if (fake.equals(cat)) fake = "basic".equals(cat) ? "intermediate" : "basic";
                    oxQuestions.add(new OxQuestion("\"" + title + "\" 는 \"" + fake + "\" 분류다.", false, "X", buildConceptExplanation(c), title));
                }
            }
            i++;
            if (i > shuffled.size() * 3) break;
        }
        if (!oxQuestions.isEmpty()) {
            int fill = 0;
            while (oxQuestions.size() < totalRounds) {
                oxQuestions.add(oxQuestions.get(fill % oxQuestions.size()));
                fill++;
            }
        }
    }

    private void startRound() {
        if (!gameActive) return;
        if (currentRound > totalRounds) { finishGame(); return; }

        roundLabel.setText("라운드 " + currentRound + " / " + totalRounds);

        if (quizMode == QuizMode.OX) {
            OxQuestion q = oxQuestions.get(currentRound - 1);
            oxModeLabel.setVisible(true);
            oxChoicePanel.setVisible(true);
            questionLabel.setText(toHtmlMultiline("문제: " + q.statement));
            roundHelpArea.setText(q.explanation);
            learningNoteArea.setText(buildLearningNote(q.keyword));
        } else {
            ChoseongQuestion q = choseongQuestions.get(currentRound - 1);
            oxModeLabel.setVisible(false);
            oxChoicePanel.setVisible(false);
            questionLabel.setText(toHtmlMultiline("초성: " + q.hint));
            roundHelpArea.setText(q.explanation);
            learningNoteArea.setText(buildLearningNote(q.keyword));
        }

        updateInputModeUI();
        secondsLeft = questionTimeSeconds;
        updateTimerBar();
        startRoundTimer();
        refreshGameInfoBar();
    }

    private void updateInputModeUI() {
        boolean oxMode = quizMode == QuizMode.OX;
        answerField.setEnabled(!oxMode);
        submitButton.setEnabled(!oxMode);
        if (oxMode) answerField.setText("");
    }

    private void submitOxChoice(String choice) {
        if (!gameActive || quizMode != QuizMode.OX) return;

        if (answerSubmitAction != null) {
            answerSubmitAction.accept(choice, currentRound);
            return;
        }

        if (isAnswerCorrectForRound(choice, currentRound)) {
            score += 10 + Math.max(0, secondsLeft / 2);
            currentRound++;
            if (currentRound > totalRounds) finishGame(); else startRound();
        }
    }

    private void startRoundTimer() {
        stopRoundTimer();
        roundTimer = new Timer(1000, e -> {
            if (!gameActive) return;
            secondsLeft--;
            updateTimerBar();
            if (secondsLeft <= 0) onRoundTimeUp();
        });
        roundTimer.start();
    }

    private void onRoundTimeUp() {
        stopRoundTimer();
        if (!gameActive) return;
        if (roundTimeoutAction != null) {
            roundTimeoutAction.accept(currentRound);
            return;
        }
        currentRound++;
        if (currentRound > totalRounds) finishGame(); else startRound();
    }

    private void submitAnswer() {
        if (quizMode == QuizMode.OX) return;

        String answer = answerField.getText() == null ? "" : answerField.getText().trim();
        if (answer.isEmpty()) return;
        if (!gameActive || currentRound > totalRounds) { answerField.setText(""); return; }

        if (answerSubmitAction != null) {
            answerSubmitAction.accept(answer, currentRound);
            answerField.setText("");
            return;
        }

        if (isAnswerCorrectForRound(answer, currentRound)) {
            score += 10 + Math.max(0, secondsLeft / 2);
            currentRound++;
            if (currentRound > totalRounds) finishGame(); else startRound();
        }
        answerField.setText("");
    }

    private void finishGame() {
        if (!gameActive) return;
        gameActive = false;
        stopRoundTimer();
        stopTotalGameTimer();
        questionLabel.setText(toHtmlMultiline("게임 종료! 점수: " + score));
        timerBar.setValue(0); timerBar.setString("종료");
        totalTimerBar.setValue(0); totalTimerBar.setString("종료");
        oxModeLabel.setVisible(false);
        oxChoicePanel.setVisible(false);
        updateInputModeUI();
    }

    private void startTotalGameTimer() {
        stopTotalGameTimer();
        totalGameTimer = new Timer(250, e -> {
            if (!gameActive) return;
            long elapsed = (System.currentTimeMillis() - gameStartMillis) / 1000L;
            totalSecondsLeft = (int) Math.max(0, totalGameTimeSeconds - elapsed);
            updateTotalTimerBar();
            if (totalSecondsLeft <= 0) finishGame();
        });
        totalGameTimer.start();
    }

    private void stopRoundTimer() { if (roundTimer != null) { roundTimer.stop(); roundTimer = null; } }
    private void stopTotalGameTimer() { if (totalGameTimer != null) { totalGameTimer.stop(); totalGameTimer = null; } }

    private void updateTimerBar() {
        int b = Math.max(0, secondsLeft);
        timerBar.setValue((int) Math.round((b * 100.0) / Math.max(1, questionTimeSeconds)));
        timerBar.setString("남은 시간 " + b + "초");
    }

    private void updateTotalTimerBar() {
        int b = Math.max(0, totalSecondsLeft);
        totalTimerBar.setValue((int) Math.round((b * 100.0) / Math.max(1, totalGameTimeSeconds)));
        totalTimerBar.setString("전체 남은 시간 " + b + "초");
    }

    private void refreshGameInfoBar() {
        int playerCount = 0;
        for (String user : connectedUsers) {
            if (user != null && !user.trim().isEmpty() && !"접속자 없음".equals(user)) playerCount++;
        }
        gameInfoLabel.setText(String.format("%s | %s | %s | %d/%d | R%d/%d | %d/%ds",
                currentGameName, currentGameMode, currentRoomName, playerCount, maxPlayers,
                currentRound, totalRounds, totalGameTimeSeconds, questionTimeSeconds));
    }

    private String buildConceptExplanation(Concept concept) {
        if (concept == null) return "해설 정보가 없습니다.";
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(concept.getCategory()).append("] ").append(concept.getTitle()).append("\n\n");
        for (String line : concept.getDescriptionLines()) sb.append(line == null ? "" : line).append("\n");
        return sb.toString();
    }

    private String buildLearningNote(String keyword) {
        return "핵심 키워드: " + (keyword == null ? "-" : keyword) + "\n사전에서 키워드를 검색해 복습하세요.";
    }

    private String toHint(String answer) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < answer.length(); i++) {
            char ch = answer.charAt(i);
            if (isHangulSyllable(ch)) sb.append(CHOSEONG[(ch - 0xAC00) / 588]);
            else sb.append(ch);
        }
        return sb.toString();
    }

    private String toHtmlMultiline(String text) {
        if (text == null) return "<html></html>";
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
        return "<html><div style='text-align:center; width:760px;'>" + safe + "</div></html>";
    }

    private boolean isMethodConcept(Concept concept) {
        if (concept == null) return true;
        String id = concept.getId() == null ? "" : concept.getId().trim().toUpperCase(Locale.ROOT);
        if (id.startsWith("M")) return true;
        String cat = concept.getCategory() == null ? "" : concept.getCategory().trim().toLowerCase(Locale.ROOT);
        return cat.contains("method") || cat.contains("메서드");
    }

    private boolean isHangulSyllable(char ch) { return ch >= 0xAC00 && ch <= 0xD7A3; }
    private String normalizeAnswer(String text) { return text == null ? "" : text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }
    private String stripFromFirstParenthesis(String text) { if (text == null) return ""; int i = text.indexOf('('); return i < 0 ? text : text.substring(0, i).trim(); }
    private int parsePositiveInt(String value, int fallback) { try { int v = Integer.parseInt(value.trim()); return v > 0 ? v : fallback; } catch (Exception e) { return fallback; } }
    private String safeText(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value.trim(); }
    private String normalizeCategory(String category) { return category == null ? "" : category.trim().toLowerCase(Locale.ROOT); }
    private void updateConfiguredTimeLabel() { configuredTimeLabel.setText("전체: " + totalGameTimeSeconds + "초 / 문제: " + questionTimeSeconds + "초"); }

    private static class ChoseongQuestion {
        private final String answer;
        private final String hint;
        private final String explanation;
        private final String keyword;
        private ChoseongQuestion(String answer, String hint, String explanation, String keyword) {
            this.answer = answer; this.hint = hint; this.explanation = explanation; this.keyword = keyword;
        }
    }

    private static class OxQuestion {
        private final String statement;
        private final boolean answer;
        private final String answerText;
        private final String explanation;
        private final String keyword;
        private OxQuestion(String statement, boolean answer, String answerText, String explanation, String keyword) {
            this.statement = statement; this.answer = answer; this.answerText = answerText; this.explanation = explanation; this.keyword = keyword;
        }
        private boolean isCorrect(String input) {
            String n = input == null ? "" : input.trim().toUpperCase(Locale.ROOT);
            if ("O".equals(n) || "TRUE".equals(n)) return answer;
            if ("X".equals(n) || "FALSE".equals(n)) return !answer;
            return false;
        }
    }

    private static class PatternPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(BG_GAME);
            g2.fillRect(0, 0, getWidth(), getHeight());
            int tile = 42;
            g2.setColor(BG_PATTERN);
            for (int y = 0; y < getHeight(); y += tile) {
                for (int x = (y / tile % 2 == 0 ? 0 : tile / 2); x < getWidth(); x += tile) {
                    g2.fillRect(x, y, tile / 2, tile / 2);
                }
            }
            g2.dispose();
        }
    }
}
