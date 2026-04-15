package org.doubt.service;

import org.doubt.constant.GameConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CQ-I4, CQ-I5 수정 검증 테스트
 *
 * <p>CQ-I4: {@code DeckService.deal()}의 {@code "STOCK"} 매직 스트링을
 * {@code GameConstants.STOCK_KEY}로 추출.</p>
 *
 * <p>CQ-I5: {@code RoomManagerService}의 하드코딩된 {@code 10}(분)을
 * {@code GameConstants.ROOM_INACTIVE_TIMEOUT_MINUTES}로 추출.</p>
 */
@DisplayName("CQ-I4, CQ-I5 수정 검증: 매직 상수 → GameConstants 추출")
class CqI4I5FixTest {

    // ----------------------------------------------------------------
    // CQ-I4: STOCK_KEY 상수 존재 및 값 검증
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("CQ-I4: GameConstants.STOCK_KEY 존재 검증")
    class StockKey {

        @Test
        @DisplayName("STOCK_KEY 상수가 존재하고 값이 'STOCK'이다")
        void stock_key_constant_is_stock() {
            assertThat(GameConstants.STOCK_KEY).isEqualTo("STOCK");
        }

        @Test
        @DisplayName("DeckService.deal()이 STOCK_KEY 키로 스톡을 반환한다")
        void deck_service_deal_uses_stock_key() {
            DeckService deckService = new DeckService();
            List<org.doubt.dto.Card> deck = deckService.createShuffledDeck();
            List<String> playerIds = List.of("Alice", "Bob");

            Map<String, List<org.doubt.dto.Card>> dealt = deckService.deal(deck, playerIds, 7);

            assertThat(dealt).containsKey(GameConstants.STOCK_KEY);
            // Alice(7) + Bob(7) + Stock(52-14=38)
            assertThat(dealt.get(GameConstants.STOCK_KEY)).hasSize(52 - 7 * 2);
        }

        @Test
        @DisplayName("DeckService.deal() 결과에 'STOCK' 리터럴 키가 존재한다 (하위 호환)")
        void deal_result_contains_literal_stock_key() {
            DeckService deckService = new DeckService();
            List<org.doubt.dto.Card> deck = deckService.createShuffledDeck();
            Map<String, List<org.doubt.dto.Card>> dealt = deckService.deal(deck, List.of("P1"), 7);

            // STOCK_KEY == "STOCK" 이므로 두 조회가 동일한 결과
            assertThat(dealt.get("STOCK"))
                    .isEqualTo(dealt.get(GameConstants.STOCK_KEY));
        }
    }

    // ----------------------------------------------------------------
    // CQ-I5: ROOM_INACTIVE_TIMEOUT_MINUTES 상수 존재 및 값 검증
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("CQ-I5: GameConstants.ROOM_INACTIVE_TIMEOUT_MINUTES 존재 검증")
    class RoomInactiveTimeout {

        @Test
        @DisplayName("ROOM_INACTIVE_TIMEOUT_MINUTES 상수가 존재하고 값이 10이다")
        void room_inactive_timeout_minutes_is_10() {
            assertThat(GameConstants.ROOM_INACTIVE_TIMEOUT_MINUTES).isEqualTo(10);
        }
    }
}
