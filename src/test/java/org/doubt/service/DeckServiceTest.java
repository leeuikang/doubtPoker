package org.doubt.service;

import org.doubt.constant.Rank;
import org.doubt.constant.Suit;
import org.doubt.dto.Card;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeckServiceTest {

    private DeckService deckService;

    @BeforeEach
    void setUp() {
        deckService = new DeckService();
    }

    @Nested
    @DisplayName("createShuffledDeck")
    class CreateShuffledDeck {

        @Test
        @DisplayName("덱은 정확히 52장을 반환한다")
        void returns_52_cards() {
            List<Card> deck = deckService.createShuffledDeck();
            assertEquals(52, deck.size());
        }

        @Test
        @DisplayName("모든 (Suit x Rank) 조합이 정확히 1장씩 포함된다")
        void contains_all_suit_rank_combinations_exactly_once() {
            List<Card> deck = deckService.createShuffledDeck();
            Set<Card> cardSet = new HashSet<>(deck);

            assertEquals(52, cardSet.size(), "중복 카드가 없어야 한다");

            for (Suit suit : Suit.values()) {
                for (Rank rank : Rank.values()) {
                    assertTrue(cardSet.contains(new Card(suit, rank)),
                            "카드가 포함되어야 한다: " + suit + " " + rank);
                }
            }
        }

        @Test
        @DisplayName("덱이 섞여있는지 확인 (여러 번 호출 시 순서가 다를 확률이 매우 높다)")
        void deck_is_shuffled() {
            // 100번 생성해서 단 한 번이라도 순서가 다르면 섞인 것으로 판단
            List<Card> first = deckService.createShuffledDeck();
            boolean foundDifference = false;
            for (int i = 0; i < 100; i++) {
                List<Card> another = deckService.createShuffledDeck();
                if (!first.equals(another)) {
                    foundDifference = true;
                    break;
                }
            }
            assertTrue(foundDifference, "100번 시도 중 한 번이라도 다른 순서가 나와야 한다");
        }
    }

    @Nested
    @DisplayName("deal")
    class Deal {

        @Test
        @DisplayName("각 플레이어에게 정확히 cardsPerPlayer장을 배분한다")
        void each_player_gets_exact_cards_per_player() {
            List<Card> deck = deckService.createShuffledDeck();
            List<String> players = List.of("p1", "p2", "p3", "p4");
            int cardsPerPlayer = 7;

            Map<String, List<Card>> result = deckService.deal(deck, players, cardsPerPlayer);

            for (String playerId : players) {
                assertTrue(result.containsKey(playerId));
                assertEquals(cardsPerPlayer, result.get(playerId).size(),
                        playerId + "의 패가 정확히 " + cardsPerPlayer + "장이어야 한다");
            }
        }

        @Test
        @DisplayName("남은 카드는 STOCK 키로 저장된다")
        void remaining_cards_stored_under_STOCK_key() {
            List<Card> deck = deckService.createShuffledDeck();
            List<String> players = List.of("p1", "p2", "p3");
            int cardsPerPlayer = 7;

            Map<String, List<Card>> result = deckService.deal(deck, players, cardsPerPlayer);

            assertTrue(result.containsKey("STOCK"));
            int expectedStock = 52 - (players.size() * cardsPerPlayer);
            assertEquals(expectedStock, result.get("STOCK").size());
        }

        @Test
        @DisplayName("배분된 모든 카드의 합계는 52장이다")
        void total_cards_equal_52() {
            List<Card> deck = deckService.createShuffledDeck();
            List<String> players = List.of("p1", "p2", "p3", "p4");
            int cardsPerPlayer = 7;

            Map<String, List<Card>> result = deckService.deal(deck, players, cardsPerPlayer);

            int total = result.values().stream().mapToInt(List::size).sum();
            assertEquals(52, total);
        }

        @Test
        @DisplayName("손패와 스톡에 중복 카드가 없다")
        void no_duplicate_cards_across_all_hands() {
            List<Card> deck = deckService.createShuffledDeck();
            List<String> players = List.of("p1", "p2", "p3", "p4");

            Map<String, List<Card>> result = deckService.deal(deck, players, 7);

            List<Card> allCards = new ArrayList<>();
            result.values().forEach(allCards::addAll);

            Set<Card> uniqueCards = new HashSet<>(allCards);
            assertEquals(allCards.size(), uniqueCards.size(), "중복 카드가 없어야 한다");
        }

        @Test
        @DisplayName("플레이어가 없을 때 전체 덱이 STOCK으로 반환된다")
        void no_players_all_cards_go_to_stock() {
            List<Card> deck = deckService.createShuffledDeck();

            Map<String, List<Card>> result = deckService.deal(deck, List.of(), 7);

            assertTrue(result.containsKey("STOCK"));
            assertEquals(52, result.get("STOCK").size());
        }

        @Test
        @DisplayName("4명 플레이어에게 7장씩 배분 시 스톡은 24장이다")
        void four_players_seven_cards_stock_is_24() {
            List<Card> deck = deckService.createShuffledDeck();
            List<String> players = List.of("p1", "p2", "p3", "p4");

            Map<String, List<Card>> result = deckService.deal(deck, players, 7);

            assertEquals(24, result.get("STOCK").size());
        }

        @Test
        @DisplayName("2명 플레이어에게 10장씩 배분 시 스톡은 32장이다")
        void two_players_ten_cards_stock_is_32() {
            List<Card> deck = deckService.createShuffledDeck();
            List<String> players = List.of("p1", "p2");

            Map<String, List<Card>> result = deckService.deal(deck, players, 10);

            assertEquals(32, result.get("STOCK").size());
        }
    }
}
