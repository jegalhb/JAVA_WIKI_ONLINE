package Reproject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ConceptEditFrame extends JFrame {
    private MainWikiFrame mainFrame;
    private ConceptRepository repository;

    private JTextField idField, titleField;
    private JComboBox<String> categoryCombo;
    private JTextArea contentArea;

    public ConceptEditFrame(MainWikiFrame mainFrame, ConceptRepository repository) {
        this.mainFrame = mainFrame;
        this.repository = repository;

        setTitle("자바 지식 추가 및 수정");
        setSize(500, 600);
        setLayout(new BorderLayout(10, 10));

        // 입력 폼 구성
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

        // 본문 입력 영역
        contentArea = new JTextArea();
        contentArea.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        JScrollPane scroll = new JScrollPane(contentArea);
        scroll.setBorder(BorderFactory.createTitledBorder("상세 내용 (한 줄씩 입력)"));
        add(scroll, BorderLayout.CENTER);

        // 저장 버튼
        JButton saveBtn = new JButton("데이터 저장하기");
        saveBtn.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        saveBtn.addActionListener(e -> saveAction());
        add(saveBtn, BorderLayout.SOUTH);

        setLocationRelativeTo(mainFrame);
        setVisible(true);
    }

    private void saveAction() {
        // 1. 입력 (Input)
        String id = idField.getText().trim();
        String title = titleField.getText().trim();
        String category = categoryCombo.getSelectedItem().toString();
        String content = contentArea.getText();

        if (id.isEmpty() || title.isEmpty()) {
            JOptionPane.showMessageDialog(this, "ID와 제목은 필수입니다.");
            return;
        }

        // 2. 처리 (Process)
        Concept newConcept = new Concept(id, title, category);
        for (String line : content.split("\n")) {
            if (!line.trim().isEmpty()) {
                newConcept.addLine(line.trim());
            }
        }

        // 3. 저장 (Storage) - 기존 ID면 수정, 새 ID면 추가가 된다.
        repository.addConcept(newConcept);

        // 4. 출력 (Output) - 메인 화면 갱신 후 종료
        mainFrame.refreshList();
        JOptionPane.showMessageDialog(this, "성공적으로 반영되었습니다.");
        dispose();
    }
}