---
name: impl-next
description: IMPLEMENTATION_ORDER.md에서 다음 미완료 항목을 찾아 구현한다. 구현 순서대로 진행할 때 사용.
user-invocable: true
---

IMPLEMENTATION_ORDER.md 파일을 읽고 `- [ ]` 체크박스 중 가장 첫 번째 항목을 찾아라.

$ARGUMENTS 가 있으면 해당 번호의 항목을 구현한다. (예: `/impl-next 3` → 3번 항목)
없으면 첫 번째 미완료 항목을 구현한다.

## 구현 전 확인사항

1. CLAUDE.md 의 코드 패턴 및 컨벤션 섹션을 반드시 확인한다.
2. ruleBook 파일에서 해당 기능과 관련된 규칙을 찾아 구현에 반영한다.
3. 이미 존재하는 스켈레톤 클래스에 로직을 채운다 (새 파일 생성 최소화).

## 구현 후

- 구현한 메서드에 대해 단위 테스트 작성 여부를 사용자에게 물어본다.
- IMPLEMENTATION_ORDER.md 의 해당 항목을 `- [x]` 로 체크한다.