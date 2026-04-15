# 아키텍처 개요

**DoubtPoker**는 거짓말 훌라(Doubt Hula) 카드 게임의 Spring Boot + STOMP over WebSocket 기반 백엔드다. 외부 DB 없이 모든 상태를 인메모리로 관리한다. 게임 규칙은 `ruleBook` 파일 참조.

## 기술 스택

- **Spring Boot 4.0.3**, Java 17
- **spring-boot-starter-websocket** (STOMP 메시징)
- **spring-boot-starter-actuator**
- **Lombok** (`@Slf4j`, `@RequiredArgsConstructor`, `@Getter`/`@Setter` 전반 사용)
- Gradle Wrapper (v9.3.1), JUnit 5

## 레이어별 역할

| 레이어 | 주요 클래스 | 역할 |
|--------|-------------|------|
| Controller | `GameController`, `ChatController` | `@MessageMapping` 핸들러 |
| Service | `RoundService`, `GameService`, `RoomManagerService` | 라운드 오케스트레이션, 룸 관리 |
| Service | `DeckService`, `MeldValidationService`, `ScoreService` | 덱·멜드·점수 도메인 로직 |
| Service | `TimerService`, `AIService` | 타이머 관리, AI 대체 진행 |
| Handler | `SessionManager` | `sessionId → roomId` 매핑 (`ConcurrentHashMap`) |
| Listener | `WebSocketEventListener` | 연결/해제 이벤트 → 플레이어 제거 |
| Repository | `PokerRoomRepository` | `ConcurrentHashMap<String, PokerRoom>` 인메모리 CRUD |
| Exception | `GlobalExceptionControllerAdvice`, `GameException`, `ErrorCode` | `GameException` 포착 → `/queue/errors` 전송 |

## DTO 구조

```
dto/
├── Card              - (Suit, Rank) record — 실제 카드
├── DeclaredCard      - (Suit, Rank) record — 거짓말 선언 카드
├── Meld              - 테이블 위 멜드 (actualCards, declaredCards, extensions 맵, isBluff)
├── RoundState        - 라운드 전체 상태 (스톡, 버림더미, 턴 순서, 타이머 등)
├── PlayerRoundState  - 라운드별 플레이어 상태 (손패, 멜드 여부, PlayerStatus)
├── TournamentState   - 10판 토너먼트 점수 및 탈락 관리
└── request/          - 클라이언트 요청 페이로드 (record)
    ├── DrawRequest, MeldRequest, ExtendRequest, DiscardRequest
    └── ThankYouRequest, StopRequest, DoubtRequest, RevealBluffRequest
```

## 주요 상수

| 상수 | 값 |
|------|----|
| `Suit` | SPADE, HEART, DIAMOND, CLUB |
| `Rank` | ACE(1) ~ KING(13), 7은 핸드에 남으면 14점 |
| `MeldType` | SET, STRAIGHT, SOLO_SEVEN |
| `GameAction` | DRAW, MELD, EXTEND, DISCARD, THANK_YOU, STOP, DOUBT, REVEAL_BLUFF, READY, CHAT |
| `GameStatus` | WAITING, DEALING, IN_PROGRESS, ROUND_END, TOURNAMENT_END |
| `TurnPhase` | DRAW → ACTION |
| `RoundEndCondition` | GOING_OUT, STOP, STOCK_DEPLETED, BANKRUPTCY |
| `PlayerStatus` | ACTIVE, DISCONNECTED, AI_CONTROLLED, ELIMINATED |

## 참고 사항

- `handler/WebSocketHandler`, `handler/ChatHandler` — 레거시 raw WebSocket 코드, 주석 처리 상태. 복구하지 않는다.
- 재시작 시 모든 데이터 초기화 (영속성 없음).
