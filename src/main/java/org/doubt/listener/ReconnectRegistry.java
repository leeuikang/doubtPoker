package org.doubt.listener;

import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 게임 중 연결이 끊긴 플레이어의 닉네임 → (방 ID, guestId) 매핑을 보관한다.
 *
 * <p>재접속 감지에 사용한다:
 * <ol>
 *   <li>연결 끊김 시 {@link #track}으로 등록 (guestId 포함)</li>
 *   <li>재접속 시 {@link #findRoom}으로 방 ID 조회</li>
 *   <li>재접속 시 {@link #verifyGuestId}로 guestId 검증 (R2-W6)</li>
 *   <li>복원 완료 후 {@link #clear}로 제거</li>
 * </ol>
 */
@Component
public class ReconnectRegistry {

    private record ReconnectEntry(String roomId, String guestId) {}

    private final ConcurrentHashMap<String, ReconnectEntry> registry = new ConcurrentHashMap<>();

    public void track(String nickname, String roomId, String guestId) {
        registry.put(nickname, new ReconnectEntry(roomId, guestId));
    }

    public Optional<String> findRoom(String nickname) {
        return Optional.ofNullable(registry.get(nickname)).map(ReconnectEntry::roomId);
    }

    /**
     * 재접속 시 저장된 guestId와 일치하는지 검증한다.
     * 등록 항목이 없거나 guestId가 다르면 {@code false}를 반환한다.
     *
     * <p>파라미터 {@code guestId}가 null인 경우에도 안전하게 {@code false}를 반환한다.</p>
     */
    public boolean verifyGuestId(String nickname, String guestId) {
        ReconnectEntry entry = registry.get(nickname);
        return entry != null && Objects.equals(entry.guestId(), guestId);
    }

    public void clear(String nickname) {
        registry.remove(nickname);
    }
}
