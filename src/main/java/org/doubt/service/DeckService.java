package org.doubt.service;

import org.doubt.constant.GameConstants;
import org.doubt.constant.Rank;
import org.doubt.constant.Suit;
import org.doubt.dto.Card;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 덱 생성 및 카드 배분 서비스
 */
@Service
public class DeckService {

    /** 52장 표준 덱을 섞어서 반환 */
    public List<Card> createShuffledDeck() {
        List<Card> deck = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(suit, rank));
            }
        }
        Collections.shuffle(deck);
        return deck;
    }

    /**
     * 플레이어별로 카드를 배분하고, 남은 카드(스톡)를 반환
     * @param deck           섞인 덱
     * @param playerIds      턴 순서의 플레이어 ID 목록
     * @param cardsPerPlayer 인당 배분 장수 (기본 7)
     * @return playerId → 손패 (+ "STOCK" 키로 나머지 스톡)
     */
    public Map<String, List<Card>> deal(List<Card> deck, List<String> playerIds, int cardsPerPlayer) {
        List<Card> remaining = new ArrayList<>(deck);
        Map<String, List<Card>> result = new LinkedHashMap<>();

        for (String playerId : playerIds) {
            List<Card> hand = new ArrayList<>(remaining.subList(0, cardsPerPlayer));
            remaining = remaining.subList(cardsPerPlayer, remaining.size());
            result.put(playerId, hand);
        }
        result.put(GameConstants.STOCK_KEY, new ArrayList<>(remaining));
        return result;
    }
}
