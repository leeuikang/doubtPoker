# WebSocket API 명세

## 인증 (H-2)

WebSocket 연결 전 Guest 토큰을 발급받아야 한다.

### POST /auth/guest

**요청**
```json
{ "nickname": "홍길동" }
```
- `nickname`: 1~20자, 필수

**응답**
```json
{ "token": "<token>", "guestId": "<uuid>", "nickname": "홍길동" }
```
- 토큰 유효 시간: 1시간

**STOMP CONNECT 헤더**
```
Authorization: Bearer <token>
```
인증 실패 시 서버는 STOMP ERROR 프레임을 전송하고 연결을 종료한다.

---

## 연결 엔드포인트

- **URL**: `/websocket`
- **프로토콜**: STOMP over WebSocket (SockJS 폴백 지원)
- **CORS**: 전체 허용 (배포 시 도메인 제한 예정)

## 클라이언트 → 서버 (Publish)

| 목적지 | 핸들러 | 설명 |
|--------|--------|------|
| `/app/game/join` | `GameController.processJoinRoom()` | 방 입장 |
| `/app/game/bet` | `GameController.processBat()` | 베팅/액션 처리 |
| `/app/chat/message` | `ChatController.sendMessage()` | 채팅 메시지 전송 |

## 서버 → 클라이언트 (Subscribe)

| 목적지 | 대상 | 설명 |
|--------|------|------|
| `/topic/room/{roomId}` | 방 전체 | 게임 상태 브로드캐스트 |
| `/topic/chat` | 전체 | 글로벌 채팅 |
| `/queue/errors` | 개별 유저 | 에러 메시지 전달 |

## 메시지 구조

모든 서버 발신 메시지는 `GameMessage` 공통 봉투를 사용한다.

### 클라이언트 요청 페이로드 (request/)

| 클래스 | 용도 |
|--------|------|
| `DrawRequest` | 카드 드로우 |
| `MeldRequest` | 멜드 제출 |
| `ExtendRequest` | 기존 멜드 확장 |
| `DiscardRequest` | 카드 버리기 |
| `ThankYouRequest` | 감사 선언 |
| `StopRequest` | 스톱 선언 |
| `DoubtRequest` | 다우트(의심) 선언 |
| `RevealBluffRequest` | 블러프 공개 |