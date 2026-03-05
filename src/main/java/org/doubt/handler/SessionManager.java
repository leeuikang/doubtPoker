package org.doubt.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionManager {
    private Map<String, Set<WebSocketSession>> sessionMap = new ConcurrentHashMap<>();

    public void addSession(String roomId, WebSocketSession session) {
        sessionMap.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void removeSession(String roomId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionMap.get(roomId);
        if(sessions != null) {
            sessions.remove(session);
            if(sessions.isEmpty()) {
                sessionMap.remove(roomId);
            }
        }
    }

    public Set<WebSocketSession> getSessions(String roomId) {
        return sessionMap.getOrDefault(roomId, Collections.emptySet());
    }
}
