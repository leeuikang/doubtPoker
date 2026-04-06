# H-1 / H-2 인증·인가·CORS 설계 제안

> 관련 이슈: `security-issues.md` H-1, H-2  
> 전제: 외부 DB 없는 인메모리 구조, STOMP over WebSocket

---

## 현재 문제 요약

| 이슈 | 증상 |
|------|------|
| H-1 CORS 전체 허용 | `setAllowedOrigins("*")` — 악성 사이트에서 WebSocket 연결 가능 |
| H-2 인증 없음 | `message.sender()` 값을 그대로 신뢰 — 임의 닉네임으로 사칭 가능 |
| H-2 인가 없음 | 방 입장 시 아무 검증 없이 `sessionManager.addUserToRoom()` 호출 |

---

## 설계 결정 사항

### 결정 1 — 토큰 발급 방식

게임 특성상 별도 계정 시스템이 없으므로 **서버 발급 Guest 토큰** 방식을 제안한다.

**흐름**
```
클라이언트                        서버
    │                               │
    │  POST /auth/guest             │
    │  { "nickname": "홍길동" }     │
    │ ─────────────────────────── ► │  닉네임 유효성 검사
    │                               │  UUID 기반 guestId 생성
    │                               │  서명된 토큰 발급
    │ ◄─────────────────────────── │
    │  { "token": "...", "guestId": "..." }
    │                               │
    │  STOMP CONNECT                │
    │  Authorization: Bearer <token>│
    │ ─────────────────────────── ► │  StompAuthInterceptor 검증
    │                               │  세션에 guestId, nickname 저장
```

**토큰 구조 (HMAC-SHA256 서명)**
```
header.payload.signature
payload = { guestId, nickname, issuedAt, expiresAt }
```

**대안과 비교**

| 방식 | 장점 | 단점 | 적합성 |
|------|------|------|------|
| **Guest 토큰 (권장)** | 서버가 identity 관리, DB 불필요 | REST 엔드포인트 1개 추가 | ✅ |
| UUID 클라이언트 생성 | 구현 간단 | 서버가 identity 검증 불가 | ❌ |
| OAuth (Google 등) | 실제 사용자 인증 | 과도한 복잡성, 게임 용도 부적합 | ❌ |
| Spring Security JWT | 표준적 | 설정 복잡, 인메모리 구조와 마찰 | △ |

---

### 결정 2 — 토큰 검증 위치

**`StompAuthInterceptor` 신규 생성** (기존 `StompLogInterceptor`와 분리)

```
STOMP CONNECT 수신
    │
    ▼
StompAuthInterceptor.preSend()
    ├─ Authorization 헤더 추출
    ├─ 토큰 서명·만료 검증
    ├─ 실패 → null 반환 (연결 거부) + 로그
    └─ 성공 → 세션에 guestId, nickname 저장
              → SEND/SUBSCRIBE 커맨드는 통과
```

**인터셉터 체인 순서** (`configureClientInboundChannel`)
```
StompAuthInterceptor  →  StompLogInterceptor  →  ChatRateLimitInterceptor
(인증 먼저)              (인증 후 로깅)            (인증된 세션만 제한)
```

---

### 결정 3 — identity 사용 방식 변경

현재 `message.sender()`(클라이언트 입력값)를 그대로 쓰는 구조를 **세션 저장값**으로 교체한다.

**현재 (취약)**
```java
// processJoinRoom(): 클라이언트가 보낸 sender를 그대로 userName으로 저장
sessionManager.addUserToRoom(message.roomId(), message.sender()); // 사칭 가능
```

**변경 후**
```java
// CONNECT 시 세션에 저장된 nickname 사용
String nickname = (String) headerAccessor.getSessionAttributes().get("nickname");
sessionManager.addUserToRoom(message.roomId(), nickname); // 서버 검증 identity
```

`GameMessage.sender()` 필드는 브로드캐스트 표시용으로만 유지하되, **서버 로직에서는 세션 identity를 우선**한다.

---

### 결정 4 — 인가 범위

게임 특성상 복잡한 역할 기반 인가(RBAC)보다 아래 두 가지면 충분하다.

| 인가 규칙 | 검증 위치 | 현재 상태 |
|------|------|------|
| 방 멤버만 게임 액션 가능 | `GameController.processBat()` | M-4에서 세션 roomId 비교로 구현됨 ✅ |
| 방 멤버만 방 구독 가능 | `StompAuthInterceptor` SUBSCRIBE 분기 | 미구현 |

SUBSCRIBE 구독 인가 (선택 구현):
```java
// SUBSCRIBE 커맨드 처리
if (StompCommand.SUBSCRIBE.equals(command)) {
    String dest = accessor.getDestination(); // "/topic/room/{roomId}"
    if (dest != null && dest.startsWith("/topic/room/")) {
        String roomId = dest.substring("/topic/room/".length());
        String sessionRoomId = (String) accessor.getSessionAttributes().get("roomId");
        if (!roomId.equals(sessionRoomId)) return null; // 타 방 구독 차단
    }
}
```

---

### 결정 5 — H-1 CORS 도메인 관리

허용 도메인을 `application.yml`에서 환경별로 관리한다.

**application.yml 구조**
```yaml
app:
  cors:
    allowed-origins:
      - "http://localhost:3000"   # 개발 프론트

# application-prod.yml
app:
  cors:
    allowed-origins:
      - "https://doubtpoker.example.com"
```

**WebSocketConfig 적용**
```java
@Value("${app.cors.allowed-origins}")
private List<String> allowedOrigins;

registry.addEndpoint("/websocket")
    .setAllowedOrigins(allowedOrigins.toArray(String[]::new))
    .withSockJS();
```

CORS 수정은 **배포 도메인 확정 후** 진행하되, `application.yml` 구조는 미리 잡아두는 것을 권장한다.

---

## 구현 순서 제안

```
1. POST /auth/guest 엔드포인트 + 토큰 발급 (GuestAuthController, TokenService)
2. StompAuthInterceptor 구현 — CONNECT 검증, 세션 저장
3. WebSocketConfig 인터셉터 체인 재배치
4. GameController.processJoinRoom() — message.sender() → 세션 nickname으로 교체
5. application.yml CORS 설정 구조 추가 (배포 도메인 확정 후 값 채움)
6. (선택) StompAuthInterceptor SUBSCRIBE 인가 추가
```

---

## 미결 사항 결정 내역

| 항목 | 결정 | 구현 |
|------|------|------|
| 토큰 서명 키 관리 | `application.yml`에서 관리 | `app.auth.secret` 프로퍼티 |
| 재시작 시 기존 토큰 무효화 | 무효화 | `GuestTokenService` 기동 시 `instanceId` 생성, payload에 포함 — 재시작 후 다른 instanceId로 TOKEN_INVALID |
| 토큰 만료 시간 | 1시간 | `app.auth.expiry-hours: 1` |
| 닉네임 중복 허용 여부 | 전체 중복 금지 | `NicknameRegistry` — CONNECT 시 등록, DISCONNECT 시 해제 → 중복 시 DUPLICATE_NICKNAME |
| 배포 도메인 | 미확정 | H-1 CORS 수정 선행 조건 (application.yml 구조는 준비됨)
