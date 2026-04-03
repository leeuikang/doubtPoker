# 보안 이슈 목록

> 발견일: 2026-04-01
> 상태 범례: `[ ]` 미수정 · `[x]` 수정 완료

---

## HIGH

### H-1 CORS 전체 허용 [ ]
- **파일**: `src/main/java/org/doubt/config/WebSocketConfig.java:20`
- **내용**: `setAllowedOrigins("*")` — 모든 출처에서 WebSocket 연결 허용
- **영향**: 악성 사이트에서 WebSocket 연결 후 게임 조작 가능 (CSRF)
- **수정 방향**: 허용할 프론트엔드 도메인만 명시

---

### H-2 인증/인가 없음 [ ]
- **파일**: `src/main/java/org/doubt/config/WebSocketConfig.java`, `src/main/java/org/doubt/controller/GameController.java`
- **내용**: 누구나 임의의 이름으로 방 입장 및 타 플레이어 사칭 가능
- **영향**: 게임 상태 무단 조작, 플레이어 사칭
- **수정 방향**: STOMP CONNECT 시 토큰 검증 (`ChannelInterceptor` 활용)

---

### H-3 PokerRoomRepository — HashMap 사용 [x]
- **파일**: `src/main/java/org/doubt/repository/PokerRoomRepository.java:10`
- **내용**: `new HashMap<>()` 사용 — 멀티스레드 환경에서 thread-unsafe
- **영향**: 동시 요청 시 게임 상태 손상, 데이터 유실
- **수정 방향**: `new ConcurrentHashMap<>()` 으로 교체

---

### H-4 WebSocketEventListener — NPE 크래시 [ ]
- **파일**: `src/main/java/org/doubt/listener/WebSocketEventListener.java:30-31`
- **내용**: `sessionAttributes.get("roomId").toString()` — null 체크 없이 호출
- **영향**: 연결 직후 끊긴 클라이언트로 서버 크래시 (DoS)
- **수정 방향**: null 체크 후 조기 반환 처리

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

### M-4 processBat() roomId 버그 + 방 소속 검증 없음 [ ]
- **파일**: `src/main/java/org/doubt/controller/GameController.java:19-22`
- **내용**: `message.getClass()` 를 roomId로 잘못 사용, 발신자의 방 소속 검증 없음
- **영향**: 메시지가 잘못된 토픽으로 전송됨, 타 방 메시지 조작 가능
- **수정 방향**: 올바른 roomId 필드 사용, 방 소속 검증 추가

---

## LOW

### L-1 로그 문자열 연결 (로그 인젝션) [ ]
- **파일**: `src/main/java/org/doubt/listener/WebSocketEventListener.java:34`
- **내용**: `log.info("User Disconnected: " + userName)` — 문자열 연결 방식
- **영향**: 공격자가 제어하는 userName으로 로그 위조 가능
- **수정 방향**: `log.info("User Disconnected: {}", userName)` 으로 변경

---

### L-2 MeldValidationService / GameService null 안전성 미흡 [ ]
- **파일**: `src/main/java/org/doubt/service/MeldValidationService.java:145-150`, `src/main/java/org/doubt/service/GameService.java:37-39`
- **내용**: `getDeclaredCards()`, `getHand()` 반환값에 null 체크 없음
- **영향**: 특정 게임 상태에서 NPE 크래시
- **수정 방향**: null 체크 또는 방어적 초기화 추가

---

### L-3 WebSocket 메시지 레이트 리밋 없음 [ ]
- **파일**: `src/main/java/org/doubt/controller/ChatController.java`
- **내용**: 메시지 전송 빈도 제한 없음
- **영향**: 채팅 스팸, 메시지 폭탄으로 CPU/메모리 고갈
- **수정 방향**: 세션별 메시지 전송 횟수 제한 (인터셉터 활용)

---

### L-4 턴 순서·페이즈 검증 없음 [ ]
- **파일**: `src/main/java/org/doubt/controller/GameController.java`, `src/main/java/org/doubt/service/RoundService.java`
- **내용**: 액션 처리 시 현재 플레이어 및 턴 페이즈 검증 없음
- **영향**: 순서 외 액션 수행, 게임 흐름 강제 조작
- **수정 방향**: RoundService 각 핸들러에 페이즈/턴 검증 추가

---

### L-5 CSRF 토큰 없음 [ ]
- **파일**: `src/main/java/org/doubt/config/WebSocketConfig.java`
- **내용**: WebSocket 핸드셰이크 시 CSRF 토큰 검증 없음
- **영향**: H-1(CORS 전체 허용)과 결합 시 CSRF 공격 가능
- **수정 방향**: H-1, H-2 수정 후 함께 처리

---

## 수정 우선순위 요약

| 순위 | 이슈 | 상태 | 이유 |
|------|------|------|------|
| 1 | H-3 HashMap | [x] | 코드 1줄, 즉시 수정 가능 |
| 2 | H-4 NPE 크래시 | [ ] | 서버 다운 방지 |
| 3 | M-4 processBat 버그 | [ ] | 명백한 버그 |
| 4 | M-2 인터셉터 등록 | [x] | 이미 만들어진 코드 연결만 |
| 5 | M-1 DTO 검증 | [x] | 게임 로직 안정성 |
| 6 | M-3 예외 정보 노출 | [x] | 로그·응답 구조 정비 |
| 7 | L-1 로그 인젝션 | [ ] | 코드 1줄 수정 |
| 8 | L-2 null 안전성 | [ ] | 크래시 방지 |
| 9 | H-1 CORS | [ ] | 배포 도메인 확정 후 |
| 10 | H-2 인증/인가 | [ ] | 설계 논의 필요 |
| 11 | L-3 레이트 리밋 | [ ] | 운영 단계에서 |
| 12 | L-4 턴 검증 | [ ] | 게임 로직 구현 완료 후 |
| 13 | L-5 CSRF | [ ] | H-1, H-2 해결 후 |