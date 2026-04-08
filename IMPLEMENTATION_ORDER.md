# 구현 순서

## 1단계 — 기반 로직 (의존성 없음)

- [V] 1. `DeckService` — 52장 생성, 셔플, 플레이어별 7장 배분
- [V] 2. `MeldValidationService` — SET·STRAIGHT·SOLO_SEVEN 유효성, 거짓말 규칙(최대 1장), 확장 가능 여부
- [V] 3. `ScoreService` — 핸드 점수 계산(7→14점), 배율 항목(훌라4x·스탑패배2x·7보유2x·파산)

## 2단계 — 라운드 흐름 (1단계 의존)

- [V] 4. `RoundService` - 라운드 시작 — `startRound`: 덱 배분, 초기 `RoundState` 구성, 선 플레이어 결정
- [V] 5. `RoundService` - 드로우 — `handleDraw`: 스톡/버림더미 처리, 스톡 소진 시 재구성(최대 2회)
- [V] 6. `RoundService` - 멜드/확장 — `handleMeld`, `handleExtend`: `MeldValidationService` 연동
- [V] 7. `RoundService` - 버리기 — `handleDiscard`: 버림더미 push, 땡큐 타이머 5초 트리거
- [V] 8. `RoundService` - 특수 선언 — `handleThankYou`, `handleStop`, `handleDoubt`, `handleRevealBluff`
- [V] 9. `RoundService` - 종료 조건 — 고잉아웃·스탑·스톡소진·파산 감지 → `ScoreService` 연동

## 3단계 — 부가 기능 (2단계 의존)

- [V] 10. `TimerService` — 턴 타이머(20초) → `handleTurnTimeout`, 땡큐 타이머
- [V] 11. `AIService` — 연결 끊긴 플레이어 자동 턴 처리

## 4단계 — 컨트롤러/연결 (전체 의존)

- [V] 12. `GameController` 업데이트 — 새 `GameAction` 라우팅, `RoundService` 위임
- [V] 13. `WebSocketEventListener` 업데이트 — 재접속 복원, 반복 끊김 패널티 처리
- [V] 14. `TournamentState` 연동 — 10라운드 관리, 탈락 처리, 토너먼트 종료
