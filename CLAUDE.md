/# CLAUDE.md

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

**DoubtPoker**는 Spring Boot + STOMP over WebSocket 기반의 실시간 멀티플레이어 카드 게임 백엔드다. 외부 데이터베이스 없이 모든 상태를 인메모리로 관리한다.

### 메시지 흐름

```
Client → /app/game/join    → GameController.processJoinRoom()
Client → /app/game/bet     → GameController.processBat()
Client → /app/chat/message → ChatController.sendMessage()

Server → /topic/room/{roomId}  (방 전체 브로드캐스트)
Server → /topic/chat           (글로벌 채팅)
Server → /queue/errors         (개별 유저 에러 전달)
```

WebSocket 엔드포인트: `/websocket` (SockJS 폴백 활성화, CORS: 전체 허용)

### 레이어별 역할

| 레이어 | 주요 클래스 | 역할 |
|--------|-------------|------|
| Controller | `GameController`, `ChatController` | `@MessageMapping` 핸들러 — STOMP 메시지 수신 후 서비스에 위임 |
| Service | `RoomManagerService` | 비즈니스 로직; 10분 이상 비활성 방 자동 정리(스케줄러) |
| Handler | `SessionManager` | `sessionId → roomId` 매핑을 `ConcurrentHashMap`으로 관리 |
| Listener | `WebSocketEventListener` | 연결/해제 이벤트 처리; 접속 해제 시 플레이어 제거 |
| Repository | `PokerRoomRepository` | `ConcurrentHashMap<String, PokerRoom>` 기반 인메모리 CRUD |
| Exception | `GlobalExceptionControllerAdvice`, `GameException`, `ErrorCode` | `GameException` 포착 후 `/queue/errors`로 `ErrorMessage` 전송 |
| Interceptor | `StompLogInterceptor` | STOMP SEND/MESSAGE 커맨드 로깅 |

### 주요 DTO

- `PokerRoom` — 방 상태: 플레이어 목록, 게임 상태, 팟, 마지막 활동 시각
- `PokerPlayer` — 플레이어 세션 ID, 이름, 칩 수, 준비 여부
- `PokerCard` — 모양(shape) + 숫자(Number)
- `GameMessage` — 공통 봉투: `type(GameAction)`, `roomId`, `sender`, `payload`
- `GameAction` enum: `PLAY_CARD`, `DOUBT`, `CHAT`, `READY`
- `GameStatus` enum: `READY`, `PLAYING`, `FINISHED`
- `ErrorCode` — 한국어 에러 메시지

### 기술 스택

- **Spring Boot 4.0.3**, Java 17
- **spring-boot-starter-websocket** (STOMP 메시징)
- **spring-boot-starter-actuator**
- **Lombok** (`@Slf4j`, `@RequiredArgsConstructor`, `@Getter`/`@Setter` 전반 사용)
- Gradle Wrapper (v9.3.1)
- JUnit 5

### 참고 사항

- `handler/` 내 `WebSocketHandler`, `ChatHandler`는 레거시 raw WebSocket 방식 코드로 주석 처리되어 있다. 참고용으로 보존하며 복구하지 않는다.
- 애플리케이션 재시작 시 모든 데이터가 초기화된다(영속성 없음).
- 게임 규칙은 README.md의 Notion 링크 참조.