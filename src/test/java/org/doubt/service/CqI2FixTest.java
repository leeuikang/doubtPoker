package org.doubt.service;

import org.doubt.constant.PlayerStatus;
import org.doubt.dto.Card;
import org.doubt.dto.PlayerRoundState;
import org.doubt.constant.Rank;
import org.doubt.constant.Suit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CQ-I2 수정 검증 테스트
 *
 * <p>원래 문제(CQ-I2): {@code GameBroadcastService.maskHandsExcept}에서
 * {@code PlayerRoundState} 필드를 9개 직접 복사해 필드 누락 위험이 있었다.
 * 특히 {@code hadMeldsAtTurnStart} 필드가 복사되지 않았다.</p>
 *
 * <p>수정 후: {@code PlayerRoundState.withMaskedHand()}를 추가해 복사 로직을 캡슐화.
 * {@code hadMeldsAtTurnStart}도 복사에 포함.</p>
 */
@DisplayName("CQ-I2 수정 검증: PlayerRoundState.withMaskedHand() 도입")
class CqI2FixTest {

    // ----------------------------------------------------------------
    // withMaskedHand() 기본 동작 검증
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("withMaskedHand() 기본 동작")
    class WithMaskedHand {

        @Test
        @DisplayName("hand가 빈 리스트로 교체된다")
        void hand_is_replaced_with_empty_list() {
            PlayerRoundState original = buildFullState();

            PlayerRoundState masked = original.withMaskedHand();

            assertThat(masked.getHand()).isEmpty();
        }

        @Test
        @DisplayName("원본 PlayerRoundState는 변경되지 않는다")
        void original_is_not_mutated() {
            PlayerRoundState original = buildFullState();
            int originalHandSize = original.getHand().size();

            original.withMaskedHand();

            assertThat(original.getHand()).hasSize(originalHandSize);
        }

        @Test
        @DisplayName("playerId가 복사된다")
        void player_id_is_copied() {
            PlayerRoundState original = buildFullState();
            assertThat(original.withMaskedHand().getPlayerId()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("hasMeldedThisTurn이 복사된다")
        void has_melded_this_turn_is_copied() {
            PlayerRoundState original = buildFullState();
            original.setHasMeldedThisTurn(true);
            assertThat(original.withMaskedHand().isHasMeldedThisTurn()).isTrue();
        }

        @Test
        @DisplayName("hadMeldsAtTurnStart가 복사된다 (기존 수동 복사에서 누락된 필드)")
        void had_melds_at_turn_start_is_copied() {
            PlayerRoundState original = buildFullState();
            original.setHadMeldsAtTurnStart(true);
            assertThat(original.withMaskedHand().isHadMeldsAtTurnStart()).isTrue();
        }

        @Test
        @DisplayName("hasDeclaredStop이 복사된다")
        void has_declared_stop_is_copied() {
            PlayerRoundState original = buildFullState();
            original.setHasDeclaredStop(true);
            assertThat(original.withMaskedHand().isHasDeclaredStop()).isTrue();
        }

        @Test
        @DisplayName("hasBankrupted가 복사된다")
        void has_bankrupted_is_copied() {
            PlayerRoundState original = buildFullState();
            original.setHasBankrupted(true);
            assertThat(original.withMaskedHand().isHasBankrupted()).isTrue();
        }

        @Test
        @DisplayName("status가 복사된다")
        void status_is_copied() {
            PlayerRoundState original = buildFullState();
            original.setStatus(PlayerStatus.ELIMINATED);
            assertThat(original.withMaskedHand().getStatus()).isEqualTo(PlayerStatus.ELIMINATED);
        }

        @Test
        @DisplayName("disconnectCount가 복사된다")
        void disconnect_count_is_copied() {
            PlayerRoundState original = buildFullState();
            original.setDisconnectCount(2);
            assertThat(original.withMaskedHand().getDisconnectCount()).isEqualTo(2);
        }
    }

    // ----------------------------------------------------------------
    // 헬퍼
    // ----------------------------------------------------------------

    private PlayerRoundState buildFullState() {
        PlayerRoundState state = new PlayerRoundState();
        state.setPlayerId("Alice");
        state.setHand(List.of(new Card(Suit.SPADE, Rank.ACE), new Card(Suit.HEART, Rank.KING)));
        state.setStatus(PlayerStatus.ACTIVE);
        return state;
    }
}
