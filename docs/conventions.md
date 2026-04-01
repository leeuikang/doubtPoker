# 코드 패턴 및 컨벤션

## Lombok

- `@Data`, `@AllArgsConstructor` **사용 금지** — 선택적으로 `@Getter`, `@Setter`, `@RequiredArgsConstructor` 만 사용
- 클래스 어노테이션 순서: `@Slf4j` → `@Service`/`@Controller` 등 스테레오타입 → `@RequiredArgsConstructor`

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class SomeService { ... }
```

## Record vs Class

- **record**: 불변 값 객체 (요청 페이로드, 카드, 에러 메시지 등)
- **class**: 변경 가능한 DTO나 도메인 객체 (`PokerRoom`, `RoundState`, `Meld` 등)

## 의존성 주입

- 필드 `@Autowired` **사용 금지**
- 모든 의존성은 `private final` + `@RequiredArgsConstructor` 로 생성자 주입

```java
private final PokerRoomRepository pokerRoomRepository;
private final SimpMessagingTemplate messagingTemplate;
```

## 예외 처리

- 예외는 반드시 `GameException(ErrorCode)` 형태로 던진다
- `Optional.orElseThrow()` 패턴 사용

```java
pokerRoomRepository.findById(roomId)
    .orElseThrow(() -> new GameException(ErrorCode.ROOM_NOT_FOUND));
```

## 브로드캐스트

- `SimpMessagingTemplate.convertAndSend()` 사용
- 라우팅: `/topic/room/{roomId}` (방 전체), `/queue/errors` (개별 에러)
- 메시지 타입은 `GameMessage` 공통 봉투 사용

## 인메모리 동시성

- 공유 상태는 `ConcurrentHashMap` 사용 (`HashMap` 사용 금지)