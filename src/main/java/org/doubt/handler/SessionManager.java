package org.doubt.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionManager {
    private Map<String, Set<String>> sessionMap = new ConcurrentHashMap<>();

    public void addUserToRoom(String roomId, String userName) {
        sessionMap.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(userName);
    }

    public void removeUserFromRoom(String roomId, String userName) {
        Set<String> users = sessionMap.get(roomId);
        if(users != null) {
            users.remove(userName);
            if(users.isEmpty()) {
                sessionMap.remove(roomId);
            }
        }
    }

    public List<String> getUserList(String roomId) {
        return new ArrayList<>(sessionMap.getOrDefault(roomId, Collections.emptySet()));
    }
}
