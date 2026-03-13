package Reproject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * [지식 저장소 클래스]
 * 지식 데이터(Concept)를 JSON 포맷으로 관리하며, 텍스트 기반의 레거시 데이터를
 * 자동으로 JSON으로 변환(Migration)하는 기능을 포함합니다.
 */
public class ConceptRepository {
    private static final String DATA_FILE = "data.json";      // 최신 JSON 저장 파일
    private static final String LEGACY_DATA_FILE = "data.txt"; // 이전 버전 텍스트 파일

    // 카테고리 명칭 정의 (코드 내 일관성 유지)
    private static final String CAT_METHOD = "메소드";
    private static final String CAT_BASIC = "기초";
    private static final String CAT_MID = "중급";
    private static final String CAT_ADV = "고급";

    // 메모리 저장소: 고유 ID를 키로 하여 Concept 객체를 관리 (HashMap으로 빠른 검색 보장)
    private final Map<String, Concept> database = new HashMap<>();

    public ConceptRepository() {
        initData(); // 구조 유지용 빈 메서드
        initMethod();

        // 1) 기본 저장 포맷인 JSON 파일을 우선적으로 읽어옵니다.
        readJsonFile(DATA_FILE, database);

        // 2) JSON 파일이 비어있고 기존 txt 파일이 존재한다면, 1회성 마이그레이션을 수행합니다.
        if (database.isEmpty()) {
            boolean migrated = readLegacyTextFile(LEGACY_DATA_FILE, database);
            if (migrated) {
                save(); // 텍스트에서 읽어온 데이터를 즉시 JSON으로 변환 저장합니다.
            }
        }

        // 3) 모든 데이터가 없을 경우 기본 샘플 데이터를 생성합니다.
        if (database.isEmpty()) {
            seedDefaultConcepts();
            save();
        }
    }

    private void initData() {}
    private void initMethod() {}

    /**
     * 현재 메모리에 있는 모든 데이터를 파일에 기록합니다.
     */
    public synchronized void save() {
        writeJsonFile(DATA_FILE, database);
    }

    // --- 데이터 필터링 및 조작 메서드 (동기화 처리) ---

    public synchronized List<Concept> findMethodAll() {
        return database.values().stream()
                .filter(c -> CAT_METHOD.equals(c.getCategory()))
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

    /**
     * [JSON 로드]
     * 파일 전체를 읽어 수동 파서(parseConceptArray)를 통해 객체 맵을 구성합니다.
     */
    private void readJsonFile(String filename, Map<String, Concept> db) {
        Path path = Paths.get(filename);
        if (!Files.exists(path)) return;

        try {
            // 파일을 UTF-8 형식의 문자열로 로드
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (json == null || json.trim().isEmpty()) return;

            // 문자열을 객체 리스트로 변환
            List<Concept> concepts = parseConceptArray(json);
            for (Concept c : concepts) {
                db.put(c.getId(), c);
            }
        } catch (Exception e) {
            System.err.println("JSON 파일 로드 실패: " + e.getMessage());
        }
    }

    /**
     * [레거시 텍스트 읽기]
     * 기존 '---' 구분자 방식의 파일을 읽어 JSON 체계로 전환하기 위한 브릿지 로직입니다.
     */
    private boolean readLegacyTextFile(String filename, Map<String, Concept> db) {
        File file = new File(filename);
        if (!file.exists()) return false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            List<String> block = new ArrayList<>();
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().equals("---")) {
                    parseLegacyBlockAndPut(block, db);
                    block.clear();
                    continue;
                }
                block.add(line);
            }
            parseLegacyBlockAndPut(block, db); // 마지막 블록 처리

            return !db.isEmpty();
        } catch (IOException e) {
            System.err.println("기존 텍스트 파일 로드 실패: " + e.getMessage());
            return false;
        }
    }

    /**
     * 텍스트 블록(ID, 제목, 카테고리 등)을 분석하여 Concept 객체로 변환합니다.
     */
    private void parseLegacyBlockAndPut(List<String> block, Map<String, Concept> db) {
        if (block == null || block.isEmpty()) return;

        List<String> lines = new ArrayList<>();
        for (String raw : block) {
            if (raw != null && !raw.trim().isEmpty()) {
                lines.add(raw.trim());
            }
        }

        if (lines.size() < 3) return; // 필수 정보(ID, 제목, 카테고리) 부족 시 무시

        String id = lines.get(0);
        String title = lines.get(1);
        String category = normalizeCategory(lines.get(2), id);

        if (id.isEmpty() || title.isEmpty() || category.isEmpty()) return;

        Concept concept = new Concept(id, title, category);
        for (int i = 3; i < lines.size(); i++) {
            concept.addLine(lines.get(i));
        }

        db.put(id, concept);
    }

    /**
     * [JSON 저장]
     * 정렬된 데이터를 수동으로 JSON 포맷화하여 저장합니다.
     * 안정성을 위해 임시파일(.tmp)을 생성한 후 순간적으로 교체(Atomic Move)합니다.
     */
    private void writeJsonFile(String filename, Map<String, Concept> db) {
        List<Concept> concepts = new ArrayList<>(db.values());
        // ID 순으로 정렬하여 저장하면 파일 내용 추적이 용이합니다.
        concepts.sort(Comparator.comparing(Concept::getId));

        StringBuilder json = new StringBuilder();
        json.append("[\n"); // 배열 시작

        for (int i = 0; i < concepts.size(); i++) {
            Concept c = concepts.get(i);
            json.append("  {\n");
            json.append("    \"id\": \"").append(escape(c.getId())).append("\",\n");
            json.append("    \"title\": \"").append(escape(c.getTitle())).append("\",\n");
            json.append("    \"category\": \"").append(escape(c.getCategory())).append("\",\n");
            json.append("    \"descriptionLines\": [\n");

            List<String> lines = c.getDescriptionLines();
            for (int j = 0; j < lines.size(); j++) {
                json.append("      \"").append(escape(lines.get(j))).append("\"");
                if (j < lines.size() - 1) json.append(","); // 마지막 요소가 아니면 쉼표 추가
                json.append("\n");
            }

            json.append("    ]\n");
            json.append("  }");
            if (i < concepts.size() - 1) json.append(","); // 객체 간 구분 쉼표
            json.append("\n");
        }
        json.append("]\n");

        Path target = Paths.get(filename);
        Path temp = Paths.get(filename + ".tmp");

        try {
            // 1. 임시 파일에 기록 (파일 쓰기 중 에러 발생 시 원본 보호)
            Files.writeString(temp, json.toString(), StandardCharsets.UTF_8);
            try {
                // 2. 임시 파일을 원본 파일로 원자적(Atomic) 교체 (데이터 유실 방지 핵심 로직)
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignore) {
                // OS가 원자적 이동을 지원하지 않을 경우 일반 교체
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("JSON 저장 실패: " + e.getMessage());
        }
    }

    /**
     * [문자열 특수문자 이스케이프]
     * JSON 문법 기호와 데이터 문자열이 충돌하지 않도록 치환합니다.
     */
    private String escape(String raw) {
        if (raw == null) return "";

        StringBuilder sb = new StringBuilder(); // 0x20 미만의 제어문자는 유니코드(XXXX) 형태로 이스케이프
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;   // " -> \"
                case '\\': sb.append("\\\\"); break;  // \ -> \\
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:

                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * [JSON 파싱 시작]
     * 대괄호([ ])로 감싸진 배열 구조를 분석합니다.
     */
    private List<Concept> parseConceptArray(String json) {
        JsonReader reader = new JsonReader(json);
        List<Concept> result = new ArrayList<>();

        reader.skipWhitespace();
        reader.expect('['); // 여는 대괄호 확인
        reader.skipWhitespace();

        // 빈 배열([]) 처리
        if (reader.consumeIf(']')) return result;

        while (true) {
            // 중괄호({ }) 객체 하나를 분석
            Concept concept = readConceptObject(reader);
            if (concept != null) result.add(concept);

            reader.skipWhitespace();
            // 쉼표(,)가 있으면 다음 객체로 진행
            if (reader.consumeIf(',')) {
                reader.skipWhitespace();
                continue;
            }

            // 쉼표가 없으면 닫는 대괄호(]) 확인 후 종료
            reader.expect(']');
            break;
        }

        // 끝에 불필요한 내용이 남아있는지 확인 (보안 및 문법 검증)
        reader.skipWhitespace();
        if (!reader.isEnd()) {
            throw new IllegalArgumentException("JSON 배열 종료 후 예기치 않은 데이터 발견");
        }

        return result;
    }

    /**
     * [JSON 객체 분석]
     * 키:값 쌍으로 구성된 중괄호 내부를 읽어 Concept 객체를 만듭니다.
     */
    private Concept readConceptObject(JsonReader reader) {
        reader.skipWhitespace();
        reader.expect('{');
        reader.skipWhitespace();

        String id = "";
        String title = "";
        String category = "";
        List<String> descriptionLines = new ArrayList<>();

        if (reader.consumeIf('}')) return null;

        while (true) {
            // 1. 키 이름 읽기
            String key = reader.readString();
            reader.skipWhitespace();
            reader.expect(':');
            reader.skipWhitespace();

            // 2. 키 값에 따른 필드 매핑
            if ("id".equals(key)) {
                id = reader.readNullableString();
            } else if ("title".equals(key)) {
                title = reader.readNullableString();
            } else if ("category".equals(key)) {
                category = reader.readNullableString();
            } else if ("descriptionLines".equals(key)) {
                descriptionLines = readStringArray(reader);
            } else {
                // 모르는 키(예: 훗날 확장된 데이터)가 오면 해당 값을 안전하게 건너뜁니다.
                reader.skipValue();
            }

            reader.skipWhitespace();
            // 다음 필드가 있으면 쉼표 처리
            if (reader.consumeIf(',')) {
                reader.skipWhitespace();
                continue;
            }

            // 닫는 중괄호 확인
            reader.expect('}');
            break;
        }

        // 데이터 정규화 및 필수값 체크
        id = id == null ? "" : id.trim();
        title = title == null ? "" : title.trim();
        category = normalizeCategory(category, id);

        if (id.isEmpty() || title.isEmpty()) return null;

        Concept concept = new Concept(id, title, category);
        for (String line : descriptionLines) {
            if (line != null) concept.addLine(line);
        }

        return concept;
    }

    /**
     * [문자열 배열 분석]
     * 설명문 라인들이 담긴 대괄호 배열을 리스트로 변환합니다.
     */
    private List<String> readStringArray(JsonReader reader) {
        List<String> list = new ArrayList<>();
        reader.expect('[');
        reader.skipWhitespace();

        if (reader.consumeIf(']')) return list;

        while (true) {
            String value = reader.readNullableString();
            if (value != null) list.add(value);

            reader.skipWhitespace();
            if (reader.consumeIf(',')) {
                reader.skipWhitespace();
                continue;
            }

            reader.expect(']');
            break;
        }

        return list;
    }

    /**
     * 카테고리 명칭을 한글 규격으로 통일합니다. (대소문자 및 영문 혼용 처리)
     */
    private String normalizeCategory(String category, String id) {
        String c = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);

        if (c.contains("method") || c.contains("메소드")) return CAT_METHOD;
        if (c.contains("basic") || c.contains("기초")) return CAT_BASIC;
        if (c.contains("intermediate") || c.contains("중급")) return CAT_MID;
        if (c.contains("advanced") || c.contains("고급")) return CAT_ADV;

        return categoryById(id);
    }

    /**
     * 카테고리 정보가 없을 시 ID의 앞 글자를 통해 추측합니다. (B:기초, I:중급, A:고급 등)
     */
    private String categoryById(String id) {
        if (id == null || id.isEmpty()) return CAT_METHOD;
        char p = Character.toUpperCase(id.charAt(0));
        if (p == 'B') return CAT_BASIC;
        if (p == 'I') return CAT_MID;
        if (p == 'A') return CAT_ADV;
        return CAT_METHOD;
    }

    /**
     * 데이터 유실 시 시스템 초기 구동을 위한 샘플 데이터를 주입합니다.
     */
    private void seedDefaultConcepts() {
        addConcept(new Concept("M01", "System.out.println()", CAT_METHOD)
                .addLine("[DESC] 콘솔에 데이터를 출력하고 줄을 바꿉니다.")
                .addLine("[CODE] System.out.println(\"안녕 자바\");"));

        addConcept(new Concept("M02", "Scanner.nextLine()", CAT_METHOD)
                .addLine("[DESC] 엔터를 칠 때까지 한 줄 전체를 읽습니다.")
                .addLine("[CODE] String str = sc.nextLine();"));
    }

    /**
     * [수동 파싱 엔진: JsonReader]
     * 한 글자씩 문자를 검사(Scanning)하며 JSON 문법 구조를 해석합니다.
     */
    private static class JsonReader {
        private final String src; // 분석할 JSON 원문
        private int idx;          // 현재 글자 위치(포인터)

        JsonReader(String src) {
            this.src = src == null ? "" : src;
            this.idx = 0;
        }

        boolean isEnd() { return idx >= src.length(); }

        // 유의미한 데이터 사이의 공백(Space, Tab, Newline)을 건너뜁니다.
        void skipWhitespace() {
            while (!isEnd() && Character.isWhitespace(src.charAt(idx))) idx++;
        }

        // 해당 문자가 오기를 기대하고, 맞으면 한 칸 전진합니다. 틀리면 에러를 던집니다.
        void expect(char expected) {
            if (isEnd() || src.charAt(idx) != expected) {
                throw new IllegalArgumentException("JSON 파싱 에러: '" + expected + "' 위치=" + idx);
            }
            idx++;
        }

        // 특정 문자가 맞으면 소비하고 true, 아니면 false를 반환합니다.
        boolean consumeIf(char expected) {
            if (!isEnd() && src.charAt(idx) == expected) {
                idx++;
                return true;
            }
            return false;
        }

        // null 값이 올 수 있는 문자열을 읽습니다.
        String readNullableString() {
            skipWhitespace();
            if (matchLiteral("null")) {
                idx += 4;
                return null;
            }
            return readString();
        }

        // 따옴표 사이의 문자열을 읽고, 이스케이프(\n, \t 등)를 원래 문자로 복원합니다.
        String readString() {
            skipWhitespace();
            expect('"');

            StringBuilder sb = new StringBuilder();
            while (!isEnd()) {
                char ch = src.charAt(idx++);
                if (ch == '"') return sb.toString(); // 닫는 따옴표 발견

                if (ch == '\\') { // 이스케이프 시작
                    if (isEnd()) throw new IllegalArgumentException("문자열 끝에서 잘못된 에스케이프 발견");
                    char esc = src.charAt(idx++);

                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (idx + 4 > src.length()) throw new IllegalArgumentException("유니코드 형식 오류");
                            String hex = src.substring(idx, idx + 4);
                            idx += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                            break;
                        default:
                            throw new IllegalArgumentException("알 수 없는 에스케이프 문자: \\" + esc);
                    }
                } else {
                    sb.append(ch);
                }
            }
            throw new IllegalArgumentException("닫히지 않은 JSON 문자열");
        }

        /**
         * 모르는 필드나 복잡한 값(객체/배열)을 안전하게 건너뛰는 핵심 로직입니다.
         * 재귀적으로 괄호 짝을 맞춰가며 점프합니다.
         */
        void skipValue() {
            skipWhitespace();
            if (isEnd()) throw new IllegalArgumentException("JSON 값이 누락되었습니다.");

            char ch = src.charAt(idx);
            if (ch == '"') { readString(); return; } // 문자열 건너뛰기

            if (ch == '{' || ch == '[') {
                // 중괄호와 대괄호는 짝이 맞을 때까지 깊이를 계산하며 건너뜁니다.
                int depth = 0;
                do {
                    char c = src.charAt(idx++);
                    if (c == '{' || c == '[') depth++;
                    else if (c == '}' || c == ']') depth--;
                } while (depth > 0 && !isEnd());
                return;
            }

            // 불리언, null 처리
            if (matchLiteral("true")) { idx += 4; return; }
            if (matchLiteral("false")) { idx += 5; return; }
            if (matchLiteral("null")) { idx += 4; return; }

            // 숫자 리터럴 처리 (공백이나 쉼표가 나올 때까지 인덱스 전진)
            int start = idx;
            while (!isEnd()) {
                char c = src.charAt(idx);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    idx++;
                } else {
                    break;
                }
            }
            if (start == idx) throw new IllegalArgumentException("알 수 없는 데이터 타입 발견 위치=" + idx);
        }

        private boolean matchLiteral(String literal) {
            if (idx + literal.length() > src.length()) return false;
            return src.regionMatches(idx, literal, 0, literal.length());
        }
    }
}