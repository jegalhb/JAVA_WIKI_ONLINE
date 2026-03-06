package Reproject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * 지식 추가 및 수정을 담당하는 입력 전용 프레임이다.
 * 사용자의 텍스트 데이터를 받아 연산을 거쳐 저장소로 보낸다.
 */
public class ConceptEditFrame extends JFrame {
    private MainWikiFrame mainFrame;
    private ConceptRepository repository;

    private JTextField idField, titleField;
    private JComboBox<String> categoryCombo;
    private JTextArea contentArea;

    public ConceptEditFrame(MainWikiFrame mainFrame, ConceptRepository repository) {
        // 메인 창의 리스트를 갱신하고 데이터 창고에 접근하기 위해 주소를 전달받는다.
        this.mainFrame = mainFrame;
        this.repository = repository;

        setTitle("자바 지식 추가 및 수정");
        setSize(500, 600);
        setLayout(new BorderLayout(10, 10));

        // 사용자로부터 데이터를 받기 위한 입력 폼 구성 (UI)
        JPanel formPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        formPanel.setBorder(new EmptyBorder(20, 20, 10, 20));

        idField = new JTextField();
        titleField = new JTextField();
        String[] cats = {"기초", "중급", "고급", "메소드"};
        categoryCombo = new JComboBox<>(cats);

        formPanel.add(new JLabel("ID (예: B51, M101):"));
        formPanel.add(idField);
        formPanel.add(new JLabel("제목:"));
        formPanel.add(titleField);
        formPanel.add(new JLabel("카테고리:"));
        formPanel.add(categoryCombo);

        add(formPanel, BorderLayout.NORTH);

        // 상세 내용을 여러 줄로 입력받기 위한 텍스트 영역
        contentArea = new JTextArea();
        contentArea.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        JScrollPane scroll = new JScrollPane(contentArea);
        scroll.setBorder(BorderFactory.createTitledBorder("상세 내용 (한 줄씩 입력)"));
        add(scroll, BorderLayout.CENTER);

        // 작성이 완료된 데이터를 시스템에 반영하기 위한 실행 버튼.
        JButton saveBtn = new JButton("데이터 저장하기");
        saveBtn.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        saveBtn.addActionListener(e -> saveAction());
        add(saveBtn, BorderLayout.SOUTH);

        setLocationRelativeTo(mainFrame);
        setVisible(true);
    }

    /**
     * [연산] 입력된 텍스트를 분석하여 객체로 변환하고 [저장]을 수행한다.
     */
    private void saveAction() {
        // 1. 입력 (Input): 화면의 컴포넌트들로부터 날것의 데이터를 가져온다.
        String id = idField.getText().trim();
        String title = titleField.getText().trim();
        String category = categoryCombo.getSelectedItem().toString();
        String content = contentArea.getText();

        if (id.isEmpty() || title.isEmpty()) {
            JOptionPane.showMessageDialog(this, "ID와 제목은 필수입니다.");
            return;
        }

        // 2. 처리 (Process): 개별 텍스트를 Concept 객체 규격에 맞춰 조립한다.
        // 문자열 데이터가 Concept 인스턴스의 필드로 이동한다.
        Concept newConcept = new Concept(id, title, category);
        for (String line : content.split("\n")) {
            if (!line.trim().isEmpty()) {
                newConcept.addLine(line.trim());
            }
        }

        // 3. 저장 (Storage): 조립된 객체를 데이터 창고(Map)에 최종 반영한다.
        // [영향] 동일한 ID가 있다면 수정(Update), 없다면 새로운 지식이 추가(Insert)된다.
        // 저장/전파 경로를 한 곳(onDataAdded)으로 통일해 로컬 반영과 소켓 전송이 항상 같이 일어난다.
        mainFrame.onDataAdded(newConcept);
        JOptionPane.showMessageDialog(this, "성공적으로 반영되었습니다.");
        dispose(); // [이동] 작업 완료 후 입력 프레임 메모리 해제 및 종료
    }
}