package org.doubt.listener;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 게임 중 연결이 끊긴 플레이어의 닉네임 → 방 ID 매핑을 보관한다.
 *
 * <p>재접속 감지에 사용한다:
 * <ol>
 *   <li>연결 끊김 시 {@link #track}으로 등록</li>
 *   <li>재접속 시 {@link #findRoom}으로 조회</li>
 *   <li>복원 완료 후 {@link #clear}로 제거</li>
 * </ol>
 */
@Component
public class ReconnectRegistry {

    private final ConcurrentHashMap<String, String> nickToRoom = new ConcurrentHashMap<>();

    public void track(String nickname, String roomId) {
        nickToRoom.put(nickname, roomId);
    }

    public Optional<String> findRoom(String nickname) {
        return Optional.ofNullable(nickToRoom.get(nickname));
    }

    public void clear(String nickname) {
        nickToRoom.remove(nickname);
    }
}
