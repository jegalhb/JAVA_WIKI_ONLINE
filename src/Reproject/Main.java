package Reproject;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {

            try {
                // 1. 데이터 전체를 저장할 곳을 만들자
                ConceptRepository repository = new ConceptRepository();

                // 2. 검색 서비스 용도
                // UI와 데이터 사이에서 검색 기능을 독립적으로 처리하기 위해 사용함
                SearchService searchService = new SearchService(repository);

                // 3. 메인 화면 생성 및 실행
                // 실제 사용자가 상호작용할 인터페이스를 띄우기 위한 기능
                MainWikiFrame frame = new MainWikiFrame(searchService, repository);

                // 4. 화면 표시
                frame.setVisible(true);

            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("프로그램 실행 중 오류가 발생했습니다: " + e.getMessage());
            }
        });
    }
}