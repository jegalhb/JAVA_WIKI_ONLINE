# JAVA_WIKI_ONLINE GAME README

## 패키지 구조
- `quizapp` (퀴즈 관련 클래스 전부)
  - 실행: `JavaQuizGameMain`(방장), `JavaClientMain`(참가자)
  - UI: `JavaQuizGameFrame`
  - 네트워크: `QuizLobbyServer`, `QuizLobbyClient`
  - 모델: `QuizMode`, `QuizRoomConfig`, `QuizRoomInfo`, `QuizSessionInit`, `RoomRuntimeState`
- `Reproject`
  - 위키 데이터/도메인 클래스 (`Concept`, `ConceptRepository`, `SearchService`)
  - 기존 실행명 호환용 래퍼(main)

## 실행 순서
1. 서버: `quizapp.QuizLobbyServer`
2. 방장: `quizapp.JavaQuizGameMain`
3. 참가자: `quizapp.JavaClientMain`

## 기존 실행명 호환
- `Reproject.QuizLobbyServer`
- `Reproject.JavaQuizGameMain`
- `Reproject.JavaClientMain`

## 동기화
- 방장 시작: `START_GAME`
- 참가자 상태 조회: `GET_ROOM_STATE` 폴링
- 동일 `gameSeed`로 문제 순서 동기화
- 방장 설정 변경: `UPDATE_ROOM_CONFIG`