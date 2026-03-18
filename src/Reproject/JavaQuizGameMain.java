package Reproject;

import javax.swing.*;

public class JavaQuizGameMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ConceptRepository repository = new ConceptRepository();
            SearchService searchService = new SearchService(repository);
            JavaQuizGameFrame frame = new JavaQuizGameFrame(repository, searchService);
            frame.setVisible(true);
        });
    }
}
