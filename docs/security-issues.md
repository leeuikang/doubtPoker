# 보안 이슈 목록

> 발견일: 2026-04-01
> 상태 범례: `[ ]` 미수정 · `[x]` 수정 완료

---

## HIGH

### H-1 CORS 전체 허용 [x]
- **파일**: `src/main/java/org/doubt/config/WebSocketConfig.java:20`
- **내용**: `setAllowedOrigins("*")` — 모든 출처에서 WebSocket 연결 허용
- **영향**: 악성 사이트에서 WebSocket 연결 후 게임 조작 가능 (CSRF)
- **수정 방향**: 허용할 프론트엔드 도메인만 명시
- **수정 내용**:
  - `AppConstants` (신규): `PROD_ORIGIN = "https://doubtpoker.io"`, `DEV_ORIGIN = "http://localhost:5173"`, `ALLOWED_ORIGINS[]` 상수 정의 — 도메인 변경 시 이 파일 한 곳만 수정하면 전체 적용
  - `WebSocketConfig`: `setAllowedOrigins("*")` → `setAllowedOrigins(AppConstants.ALLOWED_ORIGINS)` 교체

---

### H-2 인증/인가 없음 [x]
- **파일**: `src/main/java/org/doubt/config/WebSocketConfig.java`, `src/main/java/org/doubt/controller/GameController.java`
- **내용**: 누구나 임의의 이름으로 방 입장 및 타 플레이어 사칭 가능
- **영향**: 게임 상태 무단 조작, 플레이어 사칭
- **수정 방향**: STOMP CONNECT 시 토큰 검증 (`ChannelInterceptor` 활용)
- **수정 내용**:
  - `POST /auth/guest`: Guest 토큰 발급 엔드포인트 — HMAC-SHA256 서명, 만료 1시간
  - `GuestTokenService`: 외부 라이브러리 없이 `javax.crypto.Mac`으로 토큰 발급/검증
  - `StompAuthInterceptor`: CONNECT 시 `Authorization: Bearer` 헤더 검증 → 세션에 `guestId`, `nickname` 저장
  - `AuthStompErrorHandler`: `preSend()` 예외를 STOMP ERROR 프레임으로 변환 (`@MessageExceptionHandler` 도달 불가 문제 해결)
  - `WebSocketConfig`: 인터셉터 순서 `StompAuth → StompLog → ChatRateLimit`, `AuthStompErrorHandler` Bean 등록
  - `GameController.processJoinRoom()`: `message.sender()` → 세션 `nickname` 으로 교체 (사칭 차단)
  - `GameController.processBat()`: 브로드캐스트 sender도 세션 `nickname` 으로 교체
  - `api-spec.md`: 인증 단계(POST /auth/guest, CONNECT 헤더) 문서 추가

---

### H-3 PokerRoomRepository — HashMap 사용 [x]
- **파일**: `src/main/java/org/doubt/repository/PokerRoomRepository.java:10`
- **내용**: `new HashMap<>()` 사용 — 멀티스레드 환경에서 thread-unsafe
- **영향**: 동시 요청 시 게임 상태 손상, 데이터 유실
- **수정 방향**: `new ConcurrentHashMap<>()` 으로 교체

---

### H-4 WebSocketEventListener — NPE 크래시 [x]
- **파일**: `src/main/java/org/doubt/listener/WebSocketEventListener.java:30-31`
- **내용**: `sessionAttributes.get("roomId").toString()` — null 체크 없이 호출
- **영향**: 연결 직후 끊긴 클라이언트로 서버 크래시 (DoS)
- **수정 방향**: null 체크 후 조기 반환 처리
- **수정 내용**:
  - `getSessionAttributes()` null 체크 후 early return 추가
  - `roomId`, `userName` 각각 null 체크 후 early return — `.toString()` 호출 전 검증
  - 기존 사후 null 체크(`if(roomId != null && userName != null)`) 제거
  - L-1(로그 인젝션) 동시 수정: `"..." + userName` → `"...: {}", userName`

---

## MEDIUM

### M-1 DTO 입력 유효성 검증 없음 [x]
- **파일**: `src/main/java/org/doubt/dto/request/*.java`
- **내용**: 요청 record에 `@NotNull`, `@Size` 등 검증 어노테이션 없음
- **영향**: null 카드 리스트, 비정상 값으로 게임 로직 우회 가능
- **수정 방향**: Jakarta Validation 어노테이션 추가
- **수정 내용**:
  - `build.gradle`: `spring-boot-starter-validation` 의존성 추가
  - `DrawRequest`: `source` → `@NotNull`
  - `MeldRequest`: `actualCards`, `declaredCards` → `@NotNull @NotEmpty @Valid`, `type` → `@NotNull`
  - `ExtendRequest`: `meldId` → `@NotBlank`, `actualCards`, `declaredCards` → `@NotNull @NotEmpty @Valid`
  - `DiscardRequest`: `card` → `@NotNull @Valid`
  - `DoubtRequest`: `meldId` → `@NotBlank`
  - `RevealBluffRequest`: `meldId` → `@NotBlank`
  - `Card`, `DeclaredCard`: `suit`, `rank` 필드 → `@NotNull` (내포 객체 검증)

---

### M-2 StompLogInterceptor 미등록 [x]
- **파일**: `src/main/java/org/doubt/interceptor/StompLogInterceptor.java`, `src/main/java/org/doubt/config/WebSocketConfig.java`
- **내용**: 인터셉터가 빈으로 생성됐지만 WebSocketConfig에 등록되지 않아 미작동
- **영향**: STOMP 메시지 인터셉션 불가, 향후 인증 로직 삽입 불가
- **수정 방향**: `configureClientInboundChannel()`에 인터셉터 등록
- **수정 내용**:
  - `WebSocketConfig`: `@RequiredArgsConstructor`로 `StompLogInterceptor` 생성자 주입, `configureClientInboundChannel()` 오버라이드로 인터셉터 등록
  - `StompLogInterceptor`: `(byte[]) cast` → `instanceof byte[] bytes` 패턴 매칭으로 교체 (ClassCastException 방지), `new String(bytes, StandardCharsets.UTF_8)` Charset 명시, `MESSAGE` 분기(inbound dead code) 제거
  - `StompOutboundLogInterceptor` (신규): `MESSAGE` 커맨드 전용 outbound 로깅 인터셉터 생성
  - `WebSocketConfig`: `configureClientOutboundChannel()` 오버라이드로 `StompOutboundLogInterceptor` 등록

---

### M-3 예외 응답 내부 정보 노출 [x]
- **파일**: `src/main/java/org/doubt/controller/GlobalExceptionControllerAdvice.java`
- **내용**: 일반 예외 처리 시 내부 정보가 클라이언트에 노출될 수 있음
- **영향**: 공격자가 내부 구조 파악에 활용
- **수정 방향**: 클라이언트에는 generic 메시지만, 상세 내용은 서버 로그에만 기록
- **수정 내용**:
  - `handleGameException`: `GameMessage` 봉투로 래핑, `log.warn("...", e.getErrorCode().name())` — 게임 규칙 위반은 WARN 레벨, 스택 트레이스 생략
  - `handleException`: `GameMessage` 봉투로 래핑, `log.error("System Error", e)` — 전체 스택 트레이스 서버 로그 기록
  - 두 핸들러 모두 `ErrorMessage`를 `GameMessage` payload로 감싸 응답 구조 통일 (`api-spec.md` 명세 준수)
  - 하드코딩 메시지 제거 → `ErrorCode.INTERNAL_SERVER_ERROR.getMessage()` 직접 참조
  - `@ControllerAdvice` + `@MessageExceptionHandler` 유지 (전역 예외 처리)
  - `GameException`: cause 생성자 오버로드 추가 (`super(message, cause)`) — 예외 체인 보존

---

### M-4 processBat() roomId 버그 + 방 소속 검증 없음 [x]
- **파일**: `src/main/java/org/doubt/controller/GameController.java:19-22`
- **내용**: `message.getClass()` 를 roomId로 잘못 사용, 발신자의 방 소속 검증 없음
- **영향**: 메시지가 잘못된 토픽으로 전송됨, 타 방 메시지 조작 가능
- **수정 방향**: 올바른 roomId 필드 사용, 방 소속 검증 추가
- **수정 내용**:
  - `processBat()`: `message.getClass()` → `message.roomId()`, 슬래시 누락 수정 (`/topic/room/` + roomId)
  - `processBat()`: `SimpMessageHeaderAccessor` 파라미터 추가, STOMP 세션의 `roomId`와 메시지의 `roomId` 비교 — 불일치 시 `NOT_IN_ROOM` 예외 (사칭·타 방 메시지 조작 차단)

---

## LOW

### L-1 로그 문자열 연결 (로그 인젝션) [x]
- **파일**: `src/main/java/org/doubt/listener/WebSocketEventListener.java:34`
- **내용**: `log.info("User Disconnected: " + userName)` — 문자열 연결 방식
- **영향**: 공격자가 제어하는 userName으로 로그 위조 가능
- **수정 방향**: `log.info("User Disconnected: {}", userName)` 으로 변경
- **수정 내용**: H-4 수정 시 동시 처리 — `{}` 플레이스홀더 방식으로 변경

---

### L-2 MeldValidationService / GameService null 안전성 미흡 [x]
- **파일**: `src/main/java/org/doubt/service/MeldValidationService.java:145-150`, `src/main/java/org/doubt/service/GameService.java:37-39`
- **내용**: `getDeclaredCards()`, `getHand()` 반환값에 null 체크 없음
- **영향**: 특정 게임 상태에서 NPE 크래시
- **수정 방향**: null 체크 또는 방어적 초기화 추가
- **수정 내용**:
  - `PokerPlayer.hand`: `List<PokerCard> hand = new ArrayList<>()` 방어적 초기화 — 필드 선언 시점에 초기화해 `getHand().add()` NPE 원천 차단
  - `MeldValidationService.canExtend()`: `existingMeld == null` 체크 추가
  - `MeldValidationService.canExtendSet()`: `existing == null || existing.isEmpty()` 체크 추가 — `get(0)` 호출 전 빈 리스트 방어
  - `MeldValidationService.canExtendStraight()`: 동일하게 `existing == null || existing.isEmpty()` 체크 추가
  - `GameService.startGame()`: `playerCount < 2` 시 `NOT_ENOUGH_PLAYERS` 예외 — ruleBook 제2조(2~5명) 위반 차단 및 ArithmeticException(0으로 나누기) 방지
  - `ErrorCode.NOT_ENOUGH_PLAYERS` 신규 추가

---

### L-3 WebSocket 메시지 레이트 리밋 없음 [x]
- **파일**: `src/main/java/org/doubt/controller/ChatController.java`
- **내용**: 메시지 전송 빈도 제한 없음
- **영향**: 채팅 스팸, 메시지 폭탄으로 CPU/메모리 고갈
- **수정 방향**: 세션별 메시지 전송 횟수 제한 (인터셉터 활용)
- **수정 내용**:
  - `ChatRateLimitInterceptor` (신규): 세션별 분당 최대 20건 제한 — 슬라이딩 윈도우 방식, `/app/chat/message` destination 한정
  - `ConcurrentHashMap<String, RateLimitBucket>` 세션 버킷 관리, `DISCONNECT` 시 자동 정리
  - 한도 초과 시 `preSend()` → `null` 반환으로 메시지 드롭, `WARN` 로그 기록
  - `WebSocketConfig.configureClientInboundChannel()`: `chatRateLimitInterceptor` 등록 (`stompLogInterceptor` 뒤에 체이닝)

---

### L-4 턴 순서·페이즈 검증 없음 [x]
- **파일**: `src/main/java/org/doubt/controller/GameController.java`, `src/main/java/org/doubt/service/RoundService.java`
- **내용**: 액션 처리 시 현재 플레이어 및 턴 페이즈 검증 없음
- **영향**: 순서 외 액션 수행, 게임 흐름 강제 조작
- **수정 방향**: RoundService 각 핸들러에 페이즈/턴 검증 추가
- **수정 내용**:
  - `GameController.processAction()`: `room.getStatus() == TOURNAMENT_END` 단순 체크 → `room.getStatus() != IN_PROGRESS` 로 강화 — WAITING·ROUND_END 등 모든 비진행 상태 차단
  - `RoundService.validateCurrentPlayer()`: 현재 플레이어 ID 일치 검증에 더해 `PlayerStatus.ACTIVE` 검증 추가 — 탈락(ELIMINATED) 플레이어가 현재 턴인 엣지 케이스 방어

---

### L-5 CSRF 토큰 없음 [x]
- **파일**: `src/main/java/org/doubt/config/WebSocketConfig.java`
- **내용**: WebSocket 핸드셰이크 시 CSRF 토큰 검증 없음
- **영향**: H-1(CORS 전체 허용)과 결합 시 CSRF 공격 가능
- **수정 방향**: H-1, H-2 수정 후 함께 처리
- **수정 내용**:
  - `GuestTokenService`: `issueCsrfToken(guestId)` / `verifyCsrfToken(token)` 추가 — 포맷 `{guestId}.{HMAC(guestId+":csrf")}`, 무상태(저장소 불필요)
  - `GuestTokenResponse`: `csrfToken` 필드 추가 — `POST /auth/guest` 응답에 CSRF 토큰 포함
  - `GuestAuthController`: `issueCsrfToken()` 호출 후 응답에 포함
  - `AppConstants`: `CSRF_PARAM = "csrf"` 상수 추가
  - `CsrfHandshakeInterceptor` (신규): WebSocket 업그레이드 시 `?csrf=<token>` 쿼리 파라미터 검증, guestId를 세션 속성(`csrfGuestId`)에 저장
  - `StompAuthInterceptor`: CONNECT 시 `csrfGuestId`와 auth 토큰 guestId 교차 검증 — CSRF 토큰과 auth 토큰이 같은 게스트 세션임을 보장
  - `WebSocketConfig`: `.addInterceptors(csrfHandshakeInterceptor)` 등록

---

## 수정 우선순위 요약

| 순위 | 이슈 | 상태 | 이유 |
|------|------|------|------|
| 1 | H-3 HashMap | [x] | 코드 1줄, 즉시 수정 가능 |
| 2 | H-4 NPE 크래시 | [x] | 서버 다운 방지 |
| 3 | M-4 processBat 버그 | [x] | 명백한 버그 |
| 4 | M-2 인터셉터 등록 | [x] | 이미 만들어진 코드 연결만 |
| 5 | M-1 DTO 검증 | [x] | 게임 로직 안정성 |
| 6 | M-3 예외 정보 노출 | [x] | 로그·응답 구조 정비 |
| 7 | L-1 로그 인젝션 | [x] | H-4 수정 시 동시 처리 |
| 8 | L-2 null 안전성 | [x] | 크래시 방지 |
| 9 | H-1 CORS | [x] | 배포 도메인 확정 후 |
| 10 | H-2 인증/인가 | [x] | 설계 논의 필요 |
| 11 | L-3 레이트 리밋 | [x] | 운영 단계에서 |
| 12 | L-4 턴 검증 | [x] | 게임 로직 구현 완료 후 |
| 13 | L-5 CSRF | [x] | H-1, H-2 해결 후 |