package org.doubt.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PokerPlayer {
    private String sessionId;
    private String name;
    private int chips;
    private boolean isReady;
}
