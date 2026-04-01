# CLAUDE.md

이 파일은 Claude Code(claude.ai/code)가 이 저장소에서 작업할 때 참고하는 가이드입니다.

## 언어 설정

- **사용자와의 대화**: 한국어로 응답한다.
- **에이전트 간 통신**: 서브에이전트(Agent 툴)를 호출하거나 서브에이전트로부터 응답을 받을 때는 반드시 **영어**를 사용한다.

## 빌드 및 실행 명령어

```bash
# 빌드
./gradlew build

# 애플리케이션 실행 (기본 포트 8080)
./gradlew bootRun

# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "org.doubt.doubtpoker.DoubtPokerApplicationTests"

# 특정 테스트 메서드 실행
./gradlew test --tests "org.doubt.doubtpoker.DoubtPokerApplicationTests.contextLoads"
```

## 아키텍처 개요

**DoubtPoker**는 거짓말 훌라(Doubt Hula) 카드 게임의 Spring Boot + STOMP over WebSocket 기반 백엔드다. 외부 DB 없이 모든 상태를 인메모리로 관리한다. 게임 규칙은 `ruleBook` 파일 참조.

### 메시지 흐름

```
Client → /app/game/join          → GameController.processJoinRoom()
Client → /app/game/bet           → GameController.processBat()
Client → /app/chat/message       → ChatController.sendMessage()

Server → /topic/room/{roomId}    (방 전체 브로드캐스트)
Server → /topic/chat             (글로벌 채팅)
Server → /queue/errors           (개별 유저 에러 전달)
```

WebSocket 엔드포인트: `/websocket` (SockJS 폴백, CORS 전체 허용)

### 레이어별 역할

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

### DTO 구조

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

### 주요 상수

| 상수 | 값 |
|------|----|
| `Suit` | SPADE, HEART, DIAMOND, CLUB |
| `Rank` | ACE(1) ~ KING(13), 7은 핸드에 남으면 14점 |
| `MeldType` | SET, STRAIGHT, SOLO_SEVEN |
| `GameAction` | DRAW, MELD, EXTEND, DISCARD, THANK_YOU, STOP, DOUBT, REVEAL_BLUFF, READY, CHAT |
| `GameStatus` | WAITING, DEALING, IN_PROGRESS, ROUND_END, TOURNAMENT_END |
| `TurnPhase` | DRAW → ACTION → DISCARD |
| `RoundEndCondition` | GOING_OUT, STOP, STOCK_DEPLETED, BANKRUPTCY |
| `PlayerStatus` | ACTIVE, DISCONNECTED, AI_CONTROLLED, ELIMINATED |

### 기술 스택

- **Spring Boot 4.0.3**, Java 17
- **spring-boot-starter-websocket** (STOMP 메시징)
- **spring-boot-starter-actuator**
- **Lombok** (`@Slf4j`, `@RequiredArgsConstructor`, `@Getter`/`@Setter` 전반 사용)
- Gradle Wrapper (v9.3.1), JUnit 5

---

## 코드 패턴 및 컨벤션

### Lombok

- `@Data`, `@AllArgsConstructor` **사용 금지** — 선택적으로 `@Getter`, `@Setter`, `@RequiredArgsConstructor` 만 사용
- 클래스 어노테이션 순서: `@Slf4j` → `@Service`/`@Controller` 등 스테레오타입 → `@RequiredArgsConstructor`

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class SomeService { ... }
```

### Record vs Class

- **record**: 불변 값 객체 (요청 페이로드, 카드, 에러 메시지 등)
- **class**: 변경 가능한 DTO나 도메인 객체 (`PokerRoom`, `RoundState`, `Meld` 등)

### 의존성 주입

- 필드 `@Autowired` **사용 금지**
- 모든 의존성은 `private final` + `@RequiredArgsConstructor` 로 생성자 주입

```java
private final PokerRoomRepository pokerRoomRepository;
private final SimpMessagingTemplate messagingTemplate;
```

### 예외 처리

- 예외는 반드시 `GameException(ErrorCode)` 형태로 던진다
- `Optional.orElseThrow()` 패턴 사용

```java
pokerRoomRepository.findById(roomId)
    .orElseThrow(() -> new GameException(ErrorCode.ROOM_NOT_FOUND));
```

### 브로드캐스트

- `SimpMessagingTemplate.convertAndSend()` 사용
- 라우팅: `/topic/room/{roomId}` (방 전체), `/queue/errors` (개별 에러)
- 메시지 타입은 `GameMessage` 공통 봉투 사용

### 인메모리 동시성

- 공유 상태는 `ConcurrentHashMap` 사용 (`HashMap` 사용 금지)

### 참고 사항

- `handler/WebSocketHandler`, `handler/ChatHandler` — 레거시 raw WebSocket 코드, 주석 처리 상태. 복구하지 않는다.
- 재시작 시 모든 데이터 초기화 (영속성 없음).
