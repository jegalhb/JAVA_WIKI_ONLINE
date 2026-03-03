package Reproject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class MainWikiFrame extends JFrame {
    private SearchService searchService;
    private ConceptRepository repository;

    private JScrollPane scrollPane;
    private JList<Concept> resultList;
    private DefaultListModel<Concept> listModel;
    private JTextField searchField;
    private String currentCategory;

    public MainWikiFrame(SearchService searchService, ConceptRepository repository) {
        this.searchService = searchService;
        this.repository = repository;

        setTitle("Java Wiki - Responsive UI");
        setSize(1100, 750); // 초기 가로 크기를 조금 더 여유 있게 설정
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        initTopPanel();
        initCenterPanel();
        initStatusBar();

        updateList(repository.findAll());
        setLocationRelativeTo(null);
    }

    private void initTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        topPanel.setBackground(new Color(236, 240, 241));

        searchField = new JTextField();
        searchField.setFont(new Font("맑은 고딕", Font.PLAIN, 16));

        JButton searchButton = new JButton("검색");
        searchButton.addActionListener(e -> performSearch());
        searchField.addActionListener(e -> performSearch());

        topPanel.add(new JLabel("지식 검색: "), BorderLayout.WEST);
        topPanel.add(searchField, BorderLayout.CENTER);
        topPanel.add(searchButton, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);
    }

    private void initCenterPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout());

        // [수정] 5개의 버튼이 균등한 비율로 배치되도록 GridLayout(1, 5) 설정
        JPanel filterPanel = new JPanel(new GridLayout(1, 5, 5, 5));
        filterPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        // [개선] 반복문을 통해 버튼 생성 및 글자 잘림 방지 로직 적용
        String[] categories = {"전체", "기초", "중급", "고급", "메소드 집합"};
        for (String cat : categories) {
            JButton btn = new JButton(cat);

            // 폰트 크기를 살짝 줄이고 여백을 최소화하여 '...' 현상 방지
            btn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
            btn.setMargin(new Insets(2, 2, 2, 2));

            // 메소드 집합 버튼만 특별 색상 적용
            if (cat.equals("메소드 집합")) {
                btn.setBackground(new Color(100, 149, 237));
                btn.setForeground(Color.WHITE);
            }

            btn.addActionListener(e -> {
                currentCategory = cat;
                if (cat.equals("메소드 집합")) {
                    updateList(repository.findMethodAll());
                } else {
                    filterList(cat);
                }
            });
            filterPanel.add(btn);
        }
        leftPanel.add(filterPanel, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        resultList = new JList<>(listModel);
        resultList.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        resultList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) displayDetail(resultList.getSelectedValue());
        });

        JScrollPane leftScroll = new JScrollPane(resultList);
        leftScroll.setBorder(BorderFactory.createTitledBorder("지식 인덱스"));
        leftPanel.add(leftScroll, BorderLayout.CENTER);

        // 상세 내용 영역
        JPanel welcomePanel = new JPanel(new GridBagLayout());
        welcomePanel.setBackground(Color.WHITE);
        welcomePanel.add(new JLabel("카테고리를 선택하거나 검색해 주세요."));
        scrollPane = new JScrollPane(welcomePanel);
        scrollPane.setBorder(BorderFactory.createTitledBorder("상세 지식"));

        // [핵심 수정] JSplitPane 설정
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, scrollPane);

        // 창 크기가 변할 때 왼쪽 패널(리스트)이 30%, 오른쪽이 70% 비율로 커지게 설정 (0.3)
        // 이 설정이 있어야 전체화면 시 버튼들이 '...'에서 벗어나 정상적으로 펼쳐짐
        splitPane.setResizeWeight(0.3);
        splitPane.setDividerLocation(320); // 초기 구분선 위치

        add(splitPane, BorderLayout.CENTER);
    }

    private void initStatusBar() {
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
        statusPanel.add(new JLabel("자바 학습 도우미가 준비되었습니다."));
        add(statusPanel, BorderLayout.SOUTH);
    }

    private void displayDetail(Concept selected) {
        if (selected == null) return;

        JPanel detailPanel = new JPanel();
        detailPanel.setLayout(new BoxLayout(detailPanel, BoxLayout.Y_AXIS));
        detailPanel.setBackground(Color.WHITE);
        detailPanel.setBorder(BorderFactory.createEmptyBorder(20, 40, 30, 40));

        JLabel titleLabel = new JLabel(selected.getTitle());
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 28));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailPanel.add(titleLabel);

        detailPanel.add(Box.createVerticalStrut(2));

        JLabel catLabel = new JLabel("분류: " + selected.getCategory());
        catLabel.setForeground(new Color(52, 152, 219));
        catLabel.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        catLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailPanel.add(catLabel);

        detailPanel.add(Box.createVerticalStrut(8));

        JSeparator sep = new JSeparator(JSeparator.HORIZONTAL);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailPanel.add(sep);

        detailPanel.add(Box.createVerticalStrut(10));

        for (String line : selected.getDescriptionLines()) {
            JLabel label = new JLabel();
            label.setAlignmentX(Component.LEFT_ALIGNMENT);

            if (line.startsWith("[H2]")) {
                label.setText(line.replace("[H2] ", ""));
                label.setFont(new Font("맑은 고딕", Font.BOLD, 19));
                label.setForeground(new Color(44, 62, 80));
                detailPanel.add(Box.createVerticalStrut(10));
            } else if (line.startsWith("[코드]")) {
                label.setText(line);
                label.setForeground(Color.BLUE);
                label.setFont(new Font("Consolas", Font.BOLD, 15));
            } else {
                label.setText(line);
                label.setFont(new Font("맑은 고딕", Font.PLAIN, 15));
            }

            detailContentPanel(detailPanel, label); // 헬퍼 메서드로 대체 가능하나 여기서는 직접 추가
            detailPanel.add(label);
            detailPanel.add(Box.createVerticalStrut(7));
        }

        detailPanel.add(Box.createVerticalGlue());
        scrollPane.setViewportView(detailPanel);
        SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(0));
        scrollPane.revalidate();
        scrollPane.repaint();
    }

    private void detailContentPanel(JPanel panel, JLabel label) {
        // 상세 구현 생략 (기존 로직 유지)
    }

    private void filterList(String category) {
        List<Concept> all = repository.findAll();
        listModel.clear();
        for (Concept c : all) {
            if (category.equals("전체") || c.getCategory().equals(category)) {
                listModel.addElement(c);
            }
        }
    }

    private void performSearch() {
        String query = searchField.getText();
        updateList(searchService.search(query));
    }

    private void updateList(List<Concept> concepts) {
        listModel.clear();
        for (Concept c : concepts) {
            listModel.addElement(c);
        }
    }
}