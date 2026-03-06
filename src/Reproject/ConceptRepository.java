package Reproject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ConceptRepository {
    private final Map<String, Concept> database = new HashMap<>();
    private static final String DATA_FILE = "data.txt";

    public ConceptRepository() {
        initData();
        initMethod();
        readFile(DATA_FILE, database);

        // 파일이 없거나 비어 있으면 최소 기본값을 넣고 즉시 저장한다.
        if (database.isEmpty()) {
            seedDefaultConcepts();
            save();
        }
    }

    private void initData() {
        // 기존 구조 유지용(현재는 파일 기반 데이터 사용)
    }

    private void initMethod() {
        // 기존 구조 유지용(현재는 파일 기반 데이터 사용)
    }

    public synchronized void save() {
            writeFile(DATA_FILE, database);
    }

    public synchronized List<Concept> findMethodAll() {
        return database.values().stream()
                .filter(c -> "메소드".equals(c.getCategory()))
                .sorted(Comparator.comparing(Concept::getTitle))
                .toList();
    }

    public synchronized void addConcept(Concept c) {
        database.put(c.getId(), c);
    }

    public synchronized void replaceAll(List<Concept> concepts) {
        database.clear();
        for (Concept c : concepts) {
            database.put(c.getId(), c);
        }
    }

    public synchronized void deleteConcept(String id) {
        database.remove(id);
    }

    public synchronized Concept findById(String id) {
        return database.get(id);
    }

    public synchronized List<Concept> findAll() {
        return new ArrayList<>(database.values());
    }

    private void readFile(String filename, Map<String, Concept> database) {
        File file = new File(filename);
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            List<String> block = new ArrayList<>();
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().equals("---")) {
                    parseAndPut(block, database);
                    block.clear();
                    continue;
                }
                block.add(line);
            }

            parseAndPut(block, database);
        } catch (IOException e) {
            System.err.println("readFile 오류: " + e.getMessage());
        }
    }

    private void parseAndPut(List<String> block, Map<String, Concept> database) {
        if (block == null || block.isEmpty()) return;

        List<String> lines = new ArrayList<>();
        for (String raw : block) {
            if (raw != null && !raw.trim().isEmpty()) {
                lines.add(raw.trim());
            }
        }

        if (lines.size() < 3) return;

        String id = lines.get(0);
        String title = lines.get(1);
        String category = normalizeCategory(lines.get(2));
        if (id.isEmpty() || title.isEmpty() || category.isEmpty()) return;

        Concept concept = new Concept(id, title, category);
        for (int i = 3; i < lines.size(); i++) {
            concept.addLine(lines.get(i));
        }

        database.put(id, concept);
    }

    private void writeFile(String filename, Map<String, Concept> database) {
        List<Concept> concepts = new ArrayList<>(database.values());
        concepts.sort(Comparator.comparing(Concept::getId));

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8))) {
            for (Concept concept : concepts) {
                writer.write(concept.getId());
                writer.newLine();
                writer.write(concept.getTitle());
                writer.newLine();
                writer.write(concept.getCategory());
                writer.newLine();

                for (String desc : concept.getDescriptionLines()) {
                    writer.write(desc);
                    writer.newLine();
                }

                writer.write("---");
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("writeFile 오류: " + e.getMessage());
        }
    }

    private String normalizeCategory(String category) {
        if (category == null) return "";
        String c = category.trim();
        if (c.equalsIgnoreCase("method")) return "메소드";
        if (c.equalsIgnoreCase("basic")) return "기초";
        if (c.equalsIgnoreCase("intermediate")) return "중급";
        if (c.equalsIgnoreCase("advanced")) return "고급";
        return c;
    }

    private void seedDefaultConcepts() {
        addConcept(new Concept("M01", "System.out.println()", "메소드")
                .addLine("콘솔창에 데이터를 출력하고 줄을 바꾼다. 가장 기본적인 디버깅 도구이다.")
                .addLine("[코드] System.out.println(\"Hello Java\");"));

        addConcept(new Concept("M02", "Scanner.nextLine()", "메소드")
                .addLine("사용자가 엔터를 칠 때까지 입력한 문자열 전체를 읽어온다.")
                .addLine("[코드] String str = sc.nextLine();"));
    }
}
