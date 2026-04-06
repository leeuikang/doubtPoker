package org.doubt.auth;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 서버 전역 닉네임 중복 방지 레지스트리 (H-2)
 *
 * - STOMP CONNECT 시 등록 (StompAuthInterceptor)
 * - STOMP DISCONNECT 시 해제 (WebSocketEventListener)
 * - 인메모리 관리 — 서버 재시작 시 초기화됨
 */
@Component
public class NicknameRegistry {

    private final Set<String> activeNicknames = ConcurrentHashMap.newKeySet();

    /**
     * 닉네임을 등록한다.
     *
     * @return true: 등록 성공, false: 이미 사용 중인 닉네임
     */
    public boolean register(String nickname) {
        return activeNicknames.add(nickname);
    }

    /** 닉네임을 해제한다. */
    public void release(String nickname) {
        activeNicknames.remove(nickname);
    }
}
