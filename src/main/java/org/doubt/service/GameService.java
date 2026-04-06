package org.doubt.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.doubt.constant.ErrorCode;
import org.doubt.constant.GameStatus;
import org.doubt.dto.GameMessage;
import org.doubt.dto.PokerCard;
import org.doubt.dto.PokerRoom;
import org.doubt.exception.GameException;
import org.doubt.repository.PokerRoomRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final PokerRoomRepository pokerRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public void startGame(String roomId){
        PokerRoom room = pokerRoomRepository.findById(roomId)
                .orElseThrow(() -> new GameException(ErrorCode.ROOM_NOT_FOUND));

        List<PokerCard> cardList = createCardList();
        Collections.shuffle(cardList);

        int playerCount = room.getPlayerList().size();
        if (playerCount < 2) throw new GameException(ErrorCode.NOT_ENOUGH_PLAYERS);

        for(int i = 0; i < cardList.size(); i++){
            room.getPlayerList().get(i % playerCount).getHand().add(cardList.get(i));
        }

        room.setStatus(GameStatus.IN_PROGRESS);
        room.updateActivityTime();
        broadcastRoom(roomId, new GameMessage("START", roomId, "SYSTEM", "게임이 시작되었습니다!"));

    }

    private List<PokerCard> createCardList() {
        List<PokerCard> cardList = new ArrayList<>();
        String[] suits = {"SPADE", "HEART", "DIAMOND", "CLUB"};
        String[] ranks = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};

        for (String suit : suits) {
            for (String rank : ranks) {
                cardList.add(new PokerCard(suit, rank));
            }
        }

        return cardList;
    }

    private void broadcastRoom(String roomId, GameMessage message) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
    }
}
