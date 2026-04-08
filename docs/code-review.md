# DoubtPoker 전체 코드 리뷰 보고서

- **리뷰 일자:** 2026-04-08
- **리뷰 범위:** `src/main/java` 전체, `docs/`, `ruleBook`
- **리뷰어:** game-logic-reviewer agent + rule-checker agent

---

## 목차

1. [Critical — 버그 / 규칙 불일치 / 런타임 위험](#1-critical)
2. [Warning — 컨벤션 위반 / 설계 문제](#2-warning)
3. [Suggestion — 선택적 개선](#3-suggestion)
4. [수정 우선순위 요약](#4-수정-우선순위-요약)

---

## 1. Critical

### C-1 `handleStop()` — 스탑 선언 가능 시점이 ruleBook과 불일치

- **파일:** `src/main/java/org/doubt/service/RoundService.java`
- **문제:** `handleStop()`이 `validatePhase(state, TurnPhase.ACTION)`을 요구하므로 드로우 이후에만 스탑 선언이 가능하다. ruleBook §8은 "핸드 점수가 **턴 시작 시** 10점 이하면 스탑을 선언할 수 있다"고 명시하므로, DRAW 페이즈(드로우 전)에도 선언이 가능해야 한다.
- **수정 방향:** `validatePhase` 조건을 `DRAW || ACTION` 양쪽에서 허용하도록 변경하거나, 스탑 선언을 별도 페이즈 분기 없이 처리한다.

---

### C-2 `TurnPhase.DISCARD` 미사용

- **파일:** `src/main/java/org/doubt/constant/TurnPhase.java`, `src/main/java/org/doubt/service/RoundService.java`
- **문제:** `TurnPhase.DISCARD`가 enum에 정의되어 있으나 `state.setTurnPhase(TurnPhase.DISCARD)`를 호출하는 코드가 없다. 버리기는 현재 ACTION 페이즈에서 처리되고 이후 `advanceTurn()`으로 바로 넘어간다. ruleBook §5.3에서 버리기는 필수 단계이므로 DISCARD 페이즈를 실제로 강제하거나, 사용하지 않는다면 enum 값을 삭제해야 한다.
- **수정 방향:** (A) `handleDiscard()` 진입 전 `ACTION` 페이즈에서 `DISCARD` 페이즈로 전환하고 버리기를 강제하거나, (B) `TurnPhase.DISCARD`를 삭제하고 문서에 설계 의도를 명시한다.

---

### C-3 `handleDoubt()` — 지목 가능 시점이 ruleBook과 불일치 가능성

- **파일:** `src/main/java/org/doubt/service/RoundService.java`
- **문제:** `handleDoubt()`가 `validatePhase(state, TurnPhase.ACTION)`을 요구한다. ruleBook §7은 "지목자는 **자신의 턴에** 직전 멜드를 호출할 수 있다"고 명시한다. 이 문장은 드로우 전(DRAW 페이즈)에도 지목이 가능한 것으로 해석될 수 있다.
- **수정 방향:** 지목이 드로우 전에도 가능한지 팀 내에서 명확히 정의하고, 필요하다면 `DRAW || ACTION` 양쪽에서 허용하도록 변경한다.

---

### C-4 `processJoinRoom()` — 방 존재 여부 미검증, 정원 초과 검사 없음

- **파일:** `src/main/java/org/doubt/controller/GameController.java` (processJoinRoom)
- **문제:** `message.roomId()`를 그대로 세션 속성에 저장하지만, 해당 방이 실제로 존재하는지 확인하지 않는다. 존재하지 않는 방 ID로 입장하면 세션에 잘못된 `roomId`가 저장된다. 방 정원(`MAX_PLAYERS`) 초과 검사도 없다.
- **수정 방향:**
  ```java
  PokerRoom room = findRoom(message.roomId()); // ROOM_NOT_FOUND 예외 발생
  synchronized (room) {
      if (room.getPlayerList().size() >= GameConstants.MAX_PLAYERS)
          throw new GameException(ErrorCode.ROOM_FULL); // 또는 적절한 ErrorCode
      // ... 세션 저장 로직
  }
  ```

---

### C-5 `processAction()` — 라운드 종료 후 두 번째 `synchronized` 블록 사이의 경쟁 조건

- **파일:** `src/main/java/org/doubt/controller/GameController.java` (processAction)
- **문제:** `determineWinners()` + `resolveRound()` 호출 후 두 번째 `synchronized(room)` 블록에서 `room.setStatus(ROUND_END)` 및 토너먼트 결과를 반영한다. 첫 번째 `synchronized` 블록이 끝나는 시점부터 두 번째 블록이 시작되는 짧은 구간에 타이머 콜백이 `room.getStatus() == IN_PROGRESS`를 읽고 또 다른 액션을 처리할 가능성이 있다.
- **수정 방향:** 라운드 종료 감지 시 첫 번째 `synchronized` 블록 안에서 즉시 `room.setStatus(ROUND_END)`를 설정하거나, 종료 이후 처리 전체를 단일 `synchronized` 블록으로 묶는다.

---

### C-6 `RoomManagerService` — `@Scheduled` 딜레이 값 오류 (16.7시간)

- **파일:** `src/main/java/org/doubt/service/RoomManagerService.java`
- **문제:**
  ```java
  @Scheduled(fixedDelay = 60 * 1000 * 1000)
  ```
  `60 × 1000 × 1000 = 60,000,000ms = 16.67시간`. 의도는 60초(1분)마다 실행이었을 것이다.
- **수정 방향:**
  ```java
  @Scheduled(fixedDelay = 60_000L)  // 60초
  ```

---

### C-7 `handleRevealBluff()` — 자진 공개 시 확장자 카드 회수 로직이 ruleBook과 불일치

- **파일:** `src/main/java/org/doubt/service/RoundService.java` (handleRevealBluff)
- **문제:**
  ```java
  meld.getExtensions().forEach((extenderId, cards) -> {
      state.getPlayerStates().get(extenderId).getHand().addAll(cards); // 카드 회수
      drawOneFromStock(state, extenderId);                              // 드로우 패널티
  });
  ```
  ruleBook §7 "자진 공개": "멜드에 붙인 사람들은 카드 1장 드로우." 확장자의 카드 **회수**는 ruleBook에 명시되어 있지 않다. 카드 회수는 **지목 성공** 시에만 해당한다.
- **수정 방향:** `handleRevealBluff()`에서 `getHand().addAll(cards)` 라인을 제거하고 `drawOneFromStock()`만 남긴다.

---

## 2. Warning

### W-1 `ScoreService` — `@Slf4j` 누락

- **파일:** `src/main/java/org/doubt/service/ScoreService.java`
- **문제:** 다른 모든 서비스에는 `@Slf4j`가 있으나 `ScoreService`에는 없다. 점수 반영 중 이상 상황이 발생해도 로그 추적이 불가능하다.
- **수정 방향:** 클래스 상단에 `@Slf4j` 추가.

---

### W-2 `PokerPlayer` — 필드 접근 제한자 `private` 누락

- **파일:** `src/main/java/org/doubt/dto/PokerPlayer.java`
- **문제:** 모든 필드가 `private` 선언 없이 패키지 프라이빗으로 선언되어 있다. 레거시 DTO이므로 삭제도 검토 필요.
- **수정 방향:** 필드에 `private` 추가 또는 레거시 정리 시 삭제.

---

### W-3 `ChatMessage` — `import java.awt.*` (AWT 의존성)

- **파일:** `src/main/java/org/doubt/dto/ChatMessage.java`
- **문제:** `TrayIcon.MessageType`을 사용하기 위해 `java.awt.*`를 임포트하고 있다. 서버 headless 환경(컨테이너 등)에서 문제가 발생할 수 있다. 내부에 `ChatMessage.MessageType` enum이 별도로 정의되어 있어 AWT 타입 사용이 불필요하다.
- **수정 방향:** `import java.awt.*` 제거, `TrayIcon.MessageType` → `ChatMessage.MessageType`으로 교체.

---

### W-4 `WebSocketEventListener` — `@Controller` 대신 `@Component`가 적합

- **파일:** `src/main/java/org/doubt/listener/WebSocketEventListener.java`
- **문제:** `@MessageMapping` 핸들러가 없는 순수 이벤트 리스너에 `@Controller`가 사용되고 있다.
- **수정 방향:** `@Controller` → `@Component`

---

### W-5 `SessionManager` — `sessionMap` 필드에 `final` 누락

- **파일:** `src/main/java/org/doubt/handler/SessionManager.java`
- **문제:** `private Map<String, Set<String>> sessionMap = new ConcurrentHashMap<>()`에 `final`이 없어 재할당이 가능하다.
- **수정 방향:** `private final Map<String, Set<String>> sessionMap = new ConcurrentHashMap<>()`

---

### W-6 `TournamentState` — 컬렉션 필드 null 초기화

- **파일:** `src/main/java/org/doubt/dto/TournamentState.java`
- **문제:** `scores`, `eliminatedPlayers`, `roundHistory`가 생성자에서 초기화되지 않아 `null`이다. `initTournament()` 없이 직접 생성하면 NPE 발생 가능.
- **수정 방향:** 생성자에서 빈 컬렉션으로 초기화.
  ```java
  public TournamentState(String roomId) {
      this.roomId = roomId;
      this.scores = new ConcurrentHashMap<>();
      this.eliminatedPlayers = new ArrayList<>();
      this.roundHistory = new ArrayList<>();
  }
  ```

---

### W-7 `ChatController` — 인증 검증 없음

- **파일:** `src/main/java/org/doubt/controller/ChatController.java`
- **문제:** 채팅 메시지 전송 시 `nickname` 세션 속성 검증이 없어 인증되지 않은 세션도 채팅을 전송할 수 있다.
- **수정 방향:** `GameController.processJoinRoom()`처럼 `attrs.get("nickname")` null 검사 추가.

---

### W-8 `GuestAuthController` — `@Slf4j` 누락

- **파일:** `src/main/java/org/doubt/controller/GuestAuthController.java`
- **문제:** 토큰 발급 이력 추적을 위한 로깅이 불가능하다.
- **수정 방향:** 클래스 상단에 `@Slf4j` 추가.

---

### W-9 `GameController.processStartRound()` — `PokerRoom.playerList`가 항상 비어있을 가능성

- **파일:** `src/main/java/org/doubt/controller/GameController.java` (processStartRound)
- **문제:** `processJoinRoom()`은 `sessionManager.addUserToRoom()`만 호출하고 `PokerRoom.playerList`에 `PokerPlayer`를 추가하지 않는다. 따라서 `room.getPlayerList().stream().map(p -> p.getName())`이 항상 빈 리스트를 반환할 가능성이 높다.
- **수정 방향:** `processJoinRoom()`에서 `PokerRoom.playerList`에 플레이어를 추가하거나, `SessionManager.getUserList(roomId)`를 활용하도록 변경.

---

### W-10 `PokerRoom` — 미사용 dead field

- **파일:** `src/main/java/org/doubt/dto/PokerRoom.java`
- **문제:** `totalPot`, `currentIndex` 필드가 레거시 모델에 속하며 현재 게임 로직에서 전혀 사용되지 않는다.
- **수정 방향:** 레거시 정리 시 삭제.

---

### W-11 `ChipStatus` — 미사용 레거시 enum

- **파일:** `src/main/java/org/doubt/constant/ChipStatus.java`
- **문제:** `ChipStatus.DEFAULT(1000)`이 코드베이스 어디서도 참조되지 않는다.
- **수정 방향:** 삭제.

---

## 3. Suggestion

### S-1 `handleDoubt()` 성공 후 지목자의 `hasMeldedThisTurn` 갱신 여부 미정의

- **파일:** `src/main/java/org/doubt/service/RoundService.java`
- **내용:** 지목 성공 후 지목자가 확장을 할 수 있는지 여부를 정의하고, 그에 따라 `hasMeldedThisTurn` 갱신 여부를 결정해야 한다.

---

### S-2 `advanceTurn()` — 모든 플레이어 ELIMINATED 시 무한 루프 가능성

- **파일:** `src/main/java/org/doubt/service/RoundService.java` (advanceTurn)
- **내용:** 모든 플레이어가 ELIMINATED인 경우 루프가 종료된 후 잘못된 `nextIndex`를 사용하게 된다. `advanceTurn()` 진입 시 `endCondition != null` 가드를 추가하는 것이 안전하다.

---

### S-3 `handleTurnTimeout()` — `selectAutoDiscardCard()` null 반환 시 턴 미진행

- **파일:** `src/main/java/org/doubt/service/RoundService.java` (handleTurnTimeout)
- **내용:** `selectAutoDiscardCard()`가 null을 반환하면 `return state`로 조용히 종료되어 `advanceTurn()`이 호출되지 않고 게임이 멈출 수 있다. null 반환 시에도 `advanceTurn()`을 호출하도록 처리해야 한다.

---

### S-4 `StompLogInterceptor` — 손패 정보가 INFO 레벨로 로깅

- **파일:** `src/main/java/org/doubt/interceptor/StompLogInterceptor.java`
- **내용:** 인바운드 페이로드 전체를 INFO로 출력하면 프로덕션 환경에서 손패 정보 등이 로그에 노출될 수 있다. DEBUG 레벨로 낮추거나 민감 필드를 마스킹하는 처리가 필요하다.

---

### S-5 레거시 파일 일괄 삭제

다음 파일들은 현재 게임 시스템과 연결되지 않은 레거시 코드다.

| 파일 | 비고 |
|------|------|
| `src/main/java/org/doubt/dto/PokerCard.java` | 레거시 카드 DTO |
| `src/main/java/org/doubt/dto/PokerPlayer.java` | `PokerRoom.playerList`와 함께 정리 필요 |
| `src/main/java/org/doubt/service/GameService.java` | 레거시 게임 서비스 |
| `src/main/java/org/doubt/constant/ChipStatus.java` | 미사용 enum |
| `src/main/java/org/doubt/handler/WebSocketHandler.java` | 주석 처리 상태 |
| `src/main/java/org/doubt/handler/ChatHandler.java` | 주석 처리 상태 |

---

### S-6 `ValidationConfig` — `ObjectMapper` 빈 수동 등록 주의

- **파일:** `src/main/java/org/doubt/config/ValidationConfig.java`
- **내용:** Spring Boot `JacksonAutoConfiguration`이 이미 `ObjectMapper` 빈을 등록한다. 수동 등록 시 자동 구성의 커스터마이징(날짜 포맷, 모듈 등)이 무시될 수 있다. `@ConditionalOnMissingBean` 추가를 권장한다.

---

### S-7 `api-spec.md` — 엔드포인트 경로 불일치

- **파일:** `docs/api-spec.md`
- **내용:** 문서에 `/app/game/bet`으로 명시되어 있으나 실제 구현은 `/app/game/action`이다. 문서를 수정해야 한다.

---

### S-8 `TournamentState.maxRounds` — `GameConstants` 상수 직접 참조 검토

- **파일:** `src/main/java/org/doubt/dto/TournamentState.java`
- **내용:** `private final int maxRounds = 10`은 `GameConstants.MAX_ROUNDS` 상수가 없어 직접 하드코딩되어 있다. `GameConstants`에 `MAX_ROUNDS = 10`을 추가하고 참조하는 방식을 검토한다.

---

## 4. 수정 우선순위 요약

### 즉각 수정 권장 (버그 / 런타임 위험)

| 항목 | 파일 | 이유 |
|------|------|------|
| C-6 | `RoomManagerService` | `@Scheduled` 딜레이 16.7시간 — 방 정리가 사실상 동작하지 않음 |
| C-7 | `RoundService.handleRevealBluff()` | 자진 공개 시 카드 회수 로직이 ruleBook 위반 |
| W-3 | `ChatMessage` | `java.awt.*` — headless 환경 런타임 오류 가능 |
| W-9 | `GameController.processStartRound()` | `playerList`가 비어있으면 라운드 시작 자체가 불가능 |

### 단기 수정 권장 (규칙 불일치 / 컨벤션)

| 항목 | 파일 |
|------|------|
| C-1 | `RoundService.handleStop()` — DRAW 페이즈에서도 스탑 허용 |
| C-5 | `GameController.processAction()` — 경쟁 조건 해소 |
| W-6 | `TournamentState` — 컬렉션 null 초기화 |
| W-7 | `ChatController` — 인증 검증 추가 |

### 중기 정리 권장 (레거시 / 개선)

| 항목 | 내용 |
|------|------|
| S-5 | 레거시 파일 일괄 삭제 |
| C-2 | `TurnPhase.DISCARD` 삭제 또는 실제 구현 |
| S-3 | `handleTurnTimeout()` null 반환 시 턴 미진행 수정 |
