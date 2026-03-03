package Reproject;

import java.util.List;
import java.util.ArrayList;

/**
 * 자바 학습 개념을 담는 핵심 데이터 모델
 */
public class Concept {
    private String id;
    private String title;
    private String category;
    private List<String> tags = new ArrayList<>();
    private List<String> descriptionLines = new ArrayList<>();

    public Concept(String id, String title, String category) {
        this.id = id;
        this.title = title;
        this.category = category;
    }

    public Concept addLine(String line) {
        this.descriptionLines.add(line);
        return this;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public List<String> getDescriptionLines() { return descriptionLines; }
    public List<String> getTags() { return tags; }

    @Override
    public String toString() { return title; }
}