# Reproject Java Wiki

`Reproject`는 Java 학습 개념을 검색/조회/추가/삭제하고, 소켓 기반으로 여러 클라이언트가 실시간 동기화와 채팅을 수행하는 Swing 프로젝트입니다.

## 현재 구현 범위
- `MainWikiFrame`: 검색, 카테고리 필터, 상세 보기, 채팅 UI
- `ConceptRepository`: 개념 데이터 저장소(Map 기반), 전체/교체/삭제
- `SearchService`: 유사도(Levenshtein) 기반 검색 점수 계산
- `ConceptEditFrame`: 개념 추가/수정 입력 창
- `WikiServer` / `WikiClient`: 소켓 통신, 목록 동기화, 채팅 브로드캐스트

## 개발 순서도 초안
```mermaid
flowchart TD
    A[요구사항 정리\n학습용 위키 + 검색 + 카테고리] --> B[도메인 모델 설계\nConcept / Repository]
    B --> C[기본 UI 구축\nMainWikiFrame]
    C --> D[검색 로직 고도화\nSearchService + 유사도]
    D --> E[편집 기능 추가\nConceptEditFrame]
    E --> F[삭제/필터/UX 개선\n버튼, 카테고리, 엔터 검색]
    F --> G[소켓 서버-클라이언트 도입\nWikiServer/WikiClient]
    G --> H[실시간 동기화/채팅 연결\nREFRESH, LIST_DATA, CHAT]
    H --> I[버그 수정 및 안정화\n미반영/문자/출력 이슈]
```

## 시스템 흐름(현재 코드 기준)
```mermaid
flowchart LR
    U[사용자] --> UI[MainWikiFrame]
    UI --> SS[SearchService]
    SS --> REPO[ConceptRepository]
    UI --> EDIT[ConceptEditFrame]
    EDIT --> UI
    UI --> CLIENT[WikiClient]
    CLIENT <-->|소켓| SERVER[WikiServer]
    SERVER --> REPO
    SERVER --> CLIENT
    CLIENT --> UI
```

## 화면설계 반영 현황 (캡처본 분석 기준)
기준 자료: Figma 캡처본(`ai-chat-attachment-1146264767427898113.png`)

### 설계서 구조 요약
- 상단: 화면설계 메타 정보(프로젝트/버전/작성일/작성자/대상)
- 메인 3축:
  - `지식 검색 + 목록 구성` (검색/카테고리/목록/상세)
  - `기능 추가` (입력/저장/검증)
  - `협업` (실시간 동기화/채팅)
- 하단: `Check Point` + `기능 명세서(Functional Specification)`

### 설계서 -> 현재 코드 매핑
- 검색/목록 축: 구현 완료
  - `MainWikiFrame` 검색창, 카테고리 버튼, JList 목록, 상세 패널
- 기능추가 축: 구현 완료
  - `ConceptEditFrame` 입력 -> `onDataAdded()` -> 목록 반영
- 협업 축: 구현 완료
  - `WikiServer/WikiClient`의 `ADD/DELETE/LIST/CHAT`, 브로드캐스트
- 저장소 축: 진행 중(구조 전환)
  - `ConceptRepository` + `data.txt` 파일 중심 운영으로 전환 중

### 반영 상태
- 완료:
  - 검색/필터/상세 보기 기본 UX
  - 추가/삭제 및 목록 갱신
  - 실시간 채팅 및 데이터 동기화
- 진행:
  - 저장 시점 정책 명확화(즉시 저장 vs 종료 저장)
  - 파일 인코딩 일관화(UTF-8)
- 대기:
  - 기능명세서 섹션의 예외 처리 문구와 실제 코드 동작 1:1 매칭

### 수정/보완 권장
1. 서버 모드 저장 책임 일원화
- 온라인 모드에서는 서버에서만 `save()` 실행하도록 규칙 고정
- `ADD/DELETE` 처리 직후 저장 여부 정책 확정(권장: 즉시 저장)

2. `data.txt` 포맷 계약 고정
- README/화면설계서/코드 주석의 포맷 문구를 동일하게 유지
- 파싱 실패 시 처리(스킵/로그) 규칙 명시

3. 체크포인트 운영화
- 설계서의 Check Point를 `완료/진행/대기` 상태표로 유지
- 커밋 메시지와 체크포인트를 연결해 추적성 확보

## 지금까지의 진행 흐름
기준: 최근 커밋 로그(2026-03-03 ~ 2026-03-06)
실제: 2026-02-16 ~ 2026-03-06 ...ing

1. 2026-03-03
- 메소드 개념 데이터 확장
- 프레임 내 메소드 버튼/주석 작업 시작

2. 2026-03-04
- 코드 구조 주석 정리 및 문자 깨짐 이슈 수정
- 검색/카테고리에서 메소드 노출 정책 보정
- 삭제 버튼 및 기능 추가
- 서버 기능 추가 후 소켓 통신 구현 시작

3. 2026-03-05
- 클라이언트 접속용 IP 가이드 보강

4. 2026-03-06
- 엔터 키 검색 동작 개선
- 소켓 통신 미반영 이슈 수정
- 프로그램 시작 시 출력 잘림 이슈 수정

## 파일 기반 저장소 업데이트 (추가)
- `ConceptRepository`는 시작 시 `data.txt`를 읽어서 메모리(Map)로 로드합니다.
- 종료 시 `repository.save()` 호출로 현재 메모리 상태를 `data.txt`에 저장합니다.
- `data.txt`는 메모장에서 직접 편집 가능한 구조입니다.

### data.txt 포맷
```txt
ID
제목
카테고리
설명/코드 여러 줄
---
```

예시:
```txt
M01
System.out.println()
메소드
[설명] 콘솔창에 데이터를 출력하고 줄을 바꾼다.
[코드] System.out.println("Hello Java");
---
```

### 저장 시점 주의
- 단독 실행(`Reproject.Main`)에서는 창 종료 시 저장됩니다.
- 서버/클라이언트 모드에서는 현재 코드 기준으로 서버 종료 시 저장되는 흐름이므로, 즉시 저장이 필요하면 서버의 `ADD/DELETE` 처리 직후 `save()` 호출을 추가하는 것을 권장합니다.

## 실행 가이드
1. 서버 실행: `Reproject.WikiServer`
2. 클라이언트 실행: `Reproject.WikiClient`
3. (단독 UI 확인용) `Reproject.Main` 실행 가능

## 다음 작업 후보
1. `UTF-8` 인코딩 통일 및 한글 깨짐 재점검
2. 데이터 저장 영속화(JSON/파일/DB) 추가
3. 네트워크 예외 재시도/재연결 UX 개선
