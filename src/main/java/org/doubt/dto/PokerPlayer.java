package org.doubt.dto;

import lombok.Getter;
import org.doubt.constant.ChipStatus;

import java.util.List;

@Getter
public class PokerPlayer {
    String sessionId;
    String name;
    int chips;
    boolean isReady;
    List<PokerCard> hand;
}
