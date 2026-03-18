package Reproject;

import java.util.*;
import java.util.stream.Collectors;

public class SearchService {
    private final ConceptRepository repository;
    private static final double SCORE_TITLE_CONTAINS = 10.0;
    private static final double SCORE_TITLE_PART_SIMILARITY_WEIGHT = 8.0;
    private static final double SCORE_TAG_CONTAINS = 5.0;
    private static final double SCORE_TOTAL_SIMILARITY_WEIGHT = 3.0;
    private static final double SCORE_SUGGEST_TITLE_STARTS_WITH = 20.0;
    private static final double SCORE_SUGGEST_TITLE_CONTAINS = 8.0;
    private static final double THRESHOLD_TITLE_PART_SIMILARITY = 0.5;
    private static final double THRESHOLD_BEST_MATCH_SCORE = 0.5;

    public SearchService(ConceptRepository repository) {
        this.repository = repository;
    }

    /**
     * 메인 검색.
     * - query가 비어 있으면 전체 반환
     * - calculateScore() 기준으로 점수화 후 내림차순 정렬
     */
    public List<Concept> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return repository.findAll();
        }

        String lowerQuery = normalizeQuery(query);

        return repository.findAll().stream()
                .map(concept -> new SearchResult(concept, calculateScore(concept, lowerQuery)))
                .filter(result -> result.getScore() > 0)
                .sorted(Comparator.comparingDouble((SearchResult r) -> r.getScore()).reversed())
                .map(result -> result.getConcept())
                .collect(Collectors.toList());
    }
    /**
     * 키워드 포함 검색(정확/부분일치 중심).
     * - 제목/태그 contains 기준으로만 필터링
     * - 유사도(레벤슈타인) 확장 없이 사용자가 입력한 단어 중심 결과를 반환
     */
    public List<Concept> searchByKeyword(String query) {
        if (query == null || query.trim().isEmpty()) {
            return repository.findAll();
        }

        String lowerQuery = normalizeQuery(query);

        return repository.findAll().stream()
                .map(concept -> {
                    String title = normalizeText(concept.getTitle());
                    double score = 0.0;

                    if (title.startsWith(lowerQuery)) {
                        score += 100.0;
                    }
                    if (title.contains(lowerQuery)) {
                        score += 50.0;
                    }

                    List<String> tags = concept.getTags();
                    if (tags != null) {
                        for (String tag : tags) {
                            if (normalizeText(tag).contains(lowerQuery)) {
                                score += 10.0;
                            }
                        }
                    }
                    return new SearchResult(concept, score);
                })
                .filter(result -> result.getScore() > 0)
                .sorted(Comparator.comparingDouble((SearchResult r) -> r.getScore()).reversed())
                .map(SearchResult::getConcept)
                .collect(Collectors.toList());
    }
    /**
     * 자동완성 추천 전용 API.
     *
     * 설계 포인트
     * 1) 입력 도중에는 "정확 검색"보다 "빠른 후보 제시"가 중요해서 startsWith 가중치를 크게 준다.
     * 2) 제목에 query가 포함되면 가중치를 추가한다.
     * 3) 최종 fallback으로 기존 유사도 점수(calculateScore)를 더해 오타/부분일치도 살린다.
     */
    public List<Concept> suggest(String query, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String lowerQuery = normalizeQuery(query);

        return repository.findAll().stream()
                .map(concept -> {
                    String title = normalizeText(concept.getTitle());
                    double score = calculateScore(concept, lowerQuery);

                    // 자동완성에서는 prefix 매칭이 사용자 기대와 가장 잘 맞는다.
                    if (title.startsWith(lowerQuery)) {
                        score += SCORE_SUGGEST_TITLE_STARTS_WITH;
                    }
                    if (title.contains(lowerQuery)) {
                        score += SCORE_SUGGEST_TITLE_CONTAINS;
                    }
                    return new SearchResult(concept, score);
                })
                .filter(result -> result.getScore() > 0)
                .sorted(Comparator.comparingDouble((SearchResult r) -> r.getScore()).reversed())
                .limit(limit)
                .map(result -> result.getConcept())
                .collect(Collectors.toList());
    }

    public Concept getBestMatch(String query) {
        if (query == null || query.trim().isEmpty()) return null;

        String lowerQuery = normalizeQuery(query);

        return repository.findAll().stream()
                .map(concept -> new SearchResult(concept, calculateScore(concept, lowerQuery)))
                .filter(result -> result.getScore() > THRESHOLD_BEST_MATCH_SCORE)
                .max(Comparator.comparingDouble(r -> r.getScore()))
                .map(r -> r.getConcept())
                .orElse(null);
    }

    /**
     * 제목/태그/문자열 유사도 기반 점수 계산.
     * 점수는 검색 정렬 기준이며, 자동완성 suggest()에서도 기본점수로 재활용한다.
     */
    private double calculateScore(Concept concept, String query) {
        if (concept == null || query == null) {
            return 0.0;
        }

        // 리팩토링 설명:
        // 검색 점수 계산 전에 query를 한 번만 정규화한다.
        // 이렇게 하면 null/공백/대소문자 처리가 모든 분기에서 동일하게 적용되어
        // 조건식마다 중복 변환을 하지 않아도 되고, 비교 결과의 일관성이 보장된다.
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isBlank()) {
            return 0.0;
        }
        String title = normalizeText(concept.getTitle());

        double score = 0.0;

        if (title.contains(normalizedQuery)) {
            score += SCORE_TITLE_CONTAINS;
        } else {
            String[] titleParts = title.split("\\s+");
            for (String part : titleParts) {
                double partSim = getSimilarityRatio(part, normalizedQuery);
                if (partSim > THRESHOLD_TITLE_PART_SIMILARITY) {
                    score += partSim * SCORE_TITLE_PART_SIMILARITY_WEIGHT;
                }
            }
        }

        List<String> tags = concept.getTags();
        if (tags != null) {
            for (String tag : tags) {
                if (normalizeText(tag).contains(normalizedQuery)) {
                    score += SCORE_TAG_CONTAINS;
                }
            }
        }

        double totalSim = getSimilarityRatio(title, normalizedQuery);
        score += totalSim * SCORE_TOTAL_SIMILARITY_WEIGHT;

        return score;
    }


    // 리팩토링 설명:
    // query 정규화를 공통 메서드로 분리했다.
    // search/suggest/getBestMatch/calculateScore가 같은 규칙(trim + 소문자)을 사용하므로
    // 입력 전처리 기준이 한 곳에서 관리되고, 정책 변경 시 수정 지점도 1곳으로 줄어든다.
    private String normalizeQuery(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    // 리팩토링 설명:
    // 일반 텍스트 정규화를 공통화했다.
    // title/tag 같은 문자열에 대해 null 안전 처리와 소문자 변환을 동일하게 적용해서
    // NPE를 예방하고, 필드별 비교 로직의 동작 차이를 없앤다.
    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private double getSimilarityRatio(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.isEmpty() && s2.isEmpty()) return 1.0;
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;

        int distance = computeLevenshteinDistance(normalizeText(s1), normalizeText(s2));
        return 1.0 - ((double) distance / Math.max(s1.length(), s2.length()));
    }

    private int computeLevenshteinDistance(String lhs, String rhs) {
        if (lhs == null || rhs == null) {
            throw new IllegalArgumentException("메서드 입력값이 잘못되었습니다.");
        }

        int[][] distance = new int[lhs.length() + 1][rhs.length() + 1];

        for (int i = 0; i <= lhs.length(); i++) distance[i][0] = i;
        for (int j = 1; j <= rhs.length(); j++) distance[0][j] = j;

        for (int i = 1; i <= lhs.length(); i++) {
            for (int j = 1; j <= rhs.length(); j++) {
                distance[i][j] = Math.min(
                        Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1),
                        distance[i - 1][j - 1] + (lhs.charAt(i - 1) == rhs.charAt(j - 1) ? 0 : 1)
                );
            }
        }
        return distance[lhs.length()][rhs.length()];
    }

    // 리팩토링 설명: SearchResult는 검색 정렬 중간 계산값을 담는 불변 객체다.
    private static class SearchResult {
        private final Concept concept;
        private final double score;

        SearchResult(Concept concept, double score) {
            this.concept = concept;
            this.score = score;
        }

        public Concept getConcept() {
            return concept;
        }

        public double getScore() {
            return score;
        }
    }
}
