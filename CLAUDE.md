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

## 문서 참조

| 문서 | 경로 | 내용 |
|------|------|------|
| 아키텍처 | [`docs/architecture.md`](docs/architecture.md) | 레이어 구조, DTO, 상수, 기술 스택 |
| WebSocket API | [`docs/api-spec.md`](docs/api-spec.md) | 엔드포인트, 메시지 흐름, 페이로드 |
| 코드 컨벤션 | [`docs/conventions.md`](docs/conventions.md) | Lombok, DI, 예외 처리, 동시성 패턴 |
| 게임 규칙 | [`ruleBook`](ruleBook) | 거짓말 훌라 게임 규칙 전문 |

## Sub-Agent & Skill

> 상세 설명: `.claude/AGENTS_AND_SKILLS.md`

### Agents

| 이름 | 호출 | 용도 |
|------|------|------|
| `game-logic-reviewer` | `@"game-logic-reviewer (agent)"` | 코드 컨벤션·게임 로직 리뷰 |
| `rule-checker` | `@"rule-checker (agent)"` | ruleBook 기준 구현 검증 |
| `test-writer` | `@"test-writer (agent)"` | 구현 완료 서비스의 JUnit5 단위 테스트 작성 |
| `security-reviewer` | `@"security-reviewer (agent)"` | security-issues.md 기준 보안 취약점 리뷰 |
| `code-quality-reviewer` | `@"code-quality-reviewer (agent)"` | 네이밍·설계·중복·성능 등 코드 품질 리뷰 |

### Skills

| 이름 | 호출 | 용도 |
|------|------|------|
| `impl-next` | `/impl-next [번호]` | IMPLEMENTATION_ORDER.md 다음 항목 구현 |
| `round-flow` | `/round-flow` | 라운드 흐름·규칙 빠른 참조 |

## MCP 서버

> 설정 파일: `.mcp.json` (프로젝트 루트, git 공유)
> 적용 확인: Claude Code 에서 `/mcp` 실행

| 서버 | 용도 |
|------|------|
| `sequential-thinking` | 복잡한 게임 로직 설계 시 단계별 추론 (멜드 검증, 점수 배율 등) |
| `playwright` | WebSocket 게임 흐름 E2E 테스트 (SockJS 연결 → 방 입장 → 턴 진행) |
