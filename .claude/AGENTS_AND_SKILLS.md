# Sub-Agent & Skill 가이드

## 개념 정리

| | Sub-Agent | Skill |
|--|-----------|-------|
| 실행 위치 | 독립 컨텍스트 (메인 대화 오염 없음) | 메인 대화 내 실행 |
| 주요 용도 | 복잡한 멀티스텝 작업 위임 | 재사용 프롬프트 / 슬래시 커맨드 |
| 호출 방법 | `@"이름 (agent)"` 또는 Claude 자동 위임 | `/스킬명 인자` |
| 툴 제한 | 가능 | 가능 |
| 모델 지정 | 가능 (sonnet / opus / haiku) | 가능 |

---

## 파일 위치

```
프로젝트 전용 (git 공유 가능)
.claude/agents/{이름}/SKILL.md
.claude/skills/{이름}/SKILL.md

전체 프로젝트 공통 (개인)
~/.claude/agents/{이름}/SKILL.md
~/.claude/skills/{이름}/SKILL.md
```

---

## Sub-Agent 파일 형식

```markdown
---
name: 에이전트명
description: 언제 이 에이전트를 쓸지 설명 (Claude 자동 위임 판단에 사용)
tools: Read, Grep, Glob, Bash, Edit, Write   # 허용 툴 명시 (생략 시 전체)
model: sonnet                                # sonnet | opus | haiku
permissionMode: plan                         # default | acceptEdits | dontAsk | plan
maxTurns: 20
---

에이전트 시스템 프롬프트
```

### 주요 frontmatter 옵션

| 키 | 설명 |
|----|------|
| `tools` | 허용할 툴만 명시 (allowlist) |
| `disallowedTools` | 차단할 툴 명시 (denylist) |
| `model` | 이 에이전트에서만 사용할 모델 |
| `permissionMode` | `plan`: 실행 전 계획만 보여줌 |
| `maxTurns` | 최대 agentic 턴 수 |
| `isolation` | `worktree`: 독립 git worktree에서 실행 |

---

## Skill 파일 형식

```markdown
---
name: 스킬명
description: 언제 이 스킬을 쓸지 설명
disable-model-invocation: true   # true → /슬래시로만 호출 (Claude 자동 호출 차단)
user-invocable: false            # false → Claude만 호출 가능 (메뉴에 안 보임)
---

프롬프트 내용.
$ARGUMENTS     → 전달된 전체 인자 문자열
$ARGUMENTS[0]  → 첫 번째 인자
$0, $1         → $ARGUMENTS[N] 단축 표기
```

### 동적 컨텍스트 주입

```markdown
현재 git 변경사항: !`git diff`
PR 내용: !`gh pr view`
```

백틱 명령어는 스킬 실행 시 자동으로 실행되어 결과가 삽입된다.

---

## settings.json 훅 설정

`.claude/settings.json` 에서 에이전트 생명주기 훅 설정:

```json
{
  "hooks": {
    "SubagentStart": [
      {
        "matcher": "에이전트명",
        "hooks": [{ "type": "command", "command": "echo '시작' >&2" }]
      }
    ],
    "SubagentStop": [
      {
        "matcher": "에이전트명",
        "hooks": [{ "type": "command", "command": "echo '완료' >&2" }]
      }
    ]
  }
}
```

---

## 이 프로젝트의 에이전트/스킬 목록

### Agents

| 이름 | 파일 | 용도 |
|------|------|------|
| `game-logic-reviewer` | `.claude/agents/game-logic-reviewer/SKILL.md` | 코드 컨벤션·게임 로직 리뷰 |
| `rule-checker` | `.claude/agents/rule-checker/SKILL.md` | ruleBook 기준 구현 검증 |
| `test-writer` | `.claude/agents/test-writer/SKILL.md` | JUnit5 단위 테스트 작성 |
| `security-reviewer` | `.claude/agents/security-reviewer/SKILL.md` | security-issues.md 기준 보안 취약점 리뷰 |
| `code-quality-reviewer` | `.claude/agents/code-quality-reviewer/SKILL.md` | 네이밍·설계·중복·성능 등 코드 품질 리뷰 |

### Skills

| 이름 | 파일 | 호출 |
|------|------|------|
| `impl-next` | `.claude/skills/impl-next/SKILL.md` | `/impl-next` |
| `round-flow` | `.claude/skills/round-flow/SKILL.md` | `/round-flow` |

---

## MCP 서버

설정 파일: `.mcp.json` (프로젝트 루트)
적용 확인: `/mcp`

| 서버 | npm 패키지 | 용도 |
|------|-----------|------|
| `sequential-thinking` | `@modelcontextprotocol/server-sequential-thinking` | 복잡한 게임 로직 단계별 추론 |
| `playwright` | `@playwright/mcp` | WebSocket E2E 테스트 |
| `git` | `@modelcontextprotocol/server-git` | 커밋 히스토리·diff 분석 |

### 추후 추가 고려

| 서버 | 시점 | 이유 |
|------|------|------|
| `postgres` | DB 도입 시 | 게임 전적·점수 영속화 |
| `github` | PR 관리 필요 시 | 이슈·PR 자동화 |