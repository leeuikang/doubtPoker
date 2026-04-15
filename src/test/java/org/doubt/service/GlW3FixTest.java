package org.doubt.service;

import org.doubt.constant.MeldType;
import org.doubt.constant.PlayerStatus;
import org.doubt.constant.Rank;
import org.doubt.constant.Suit;
import org.doubt.constant.TurnPhase;
import org.doubt.dto.Card;
import org.doubt.dto.GameMessage;
import org.doubt.dto.Meld;
import org.doubt.dto.PlayerRoundState;
import org.doubt.dto.RoundState;
import org.doubt.handler.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GL-W3 버그 수정 검증 테스트
 *
 * maskMeldsFor() 내 extensions 방어 복사 검증.
 * masked meld 의 extensions Map 및 내부 카드 List 를 변이해도
 * 원본 Meld 의 상태가 훼손되지 않음을 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GL-W3: maskMeldsFor — extensions 방어 복사")
class GlW3FixTest {

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @Mock
    private SessionManager sessionManager;

    @InjectMocks
    private GameBroadcastService gameBroadcastService;

    private static final String ROOM_ID = "room1";
    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final Card ACE_OF_HEARTS = new Card(Suit.HEART, Rank.ACE);

    /**
     * alice 소유, extensions = {"bob" → [ACE_OF_HEARTS]} 인 멜드를 반환한다.
     */
    private Meld buildMeldWithExtensions() {
        Meld meld = Meld.create("meld-1", ALICE, MeldType.SET, List.of(), List.of(), false);
        meld.getExtensions().put(BOB, new ArrayList<>(List.of(ACE_OF_HEARTS)));
        return meld;
    }

    /**
     * 두 플레이어(alice, bob)를 포함하는 기본 RoundState 를 반환한다.
     */
    private RoundState buildRoundState(Meld meld) {
        RoundState state = new RoundState();
        state.setStockPile(new ArrayList<>());
        state.setDiscardPile(new ArrayList<>());
        state.setTableMelds(new ArrayList<>(List.of(meld)));
        state.setTurnOrder(new ArrayList<>(List.of(ALICE, BOB)));
        state.setCurrentPlayerIndex(0);
        state.setTurnPhase(TurnPhase.ACTION);
        state.setStockRefillCount(0);
        state.setLastDoubtableMeldId(null);
        state.setThankYouTimerSec(0);
        state.setEndCondition(null);

        Map<String, PlayerRoundState> playerStates = new HashMap<>();
        playerStates.put(ALICE, buildPlayerState(ALICE));
        playerStates.put(BOB, buildPlayerState(BOB));
        state.setPlayerStates(playerStates);

        return state;
    }

    private PlayerRoundState buildPlayerState(String playerId) {
        PlayerRoundState prs = new PlayerRoundState();
        prs.setPlayerId(playerId);
        prs.setHand(new ArrayList<>());
        prs.setStatus(PlayerStatus.ACTIVE);
        prs.setHasMeldedThisTurn(false);
        prs.setHadMeldsAtTurnStart(false);
        prs.setHasDeclaredStop(false);
        prs.setHasBankrupted(false);
        prs.setDisconnectCount(0);
        return prs;
    }

    /**
     * broadcastRoundState 를 호출하고 bob 에게 전송된 GameMessage 를 캡처하여 반환한다.
     */
    private RoundState captureRoundStateSentToBob(RoundState state) {
        when(sessionManager.getUserList(ROOM_ID)).thenReturn(List.of(BOB));

        gameBroadcastService.broadcastRoundState(ROOM_ID, "ROUND_STATE", ALICE, state);

        ArgumentCaptor<GameMessage> captor = ArgumentCaptor.forClass(GameMessage.class);
        verify(messagingTemplate).convertAndSend(anyString(), captor.capture());
        GameMessage sent = captor.getValue();
        return (RoundState) sent.payload();
    }

    // ================================================================
    // Nested: 비소유자 — extensions Map 방어 복사
    // ================================================================

    @Nested
    @DisplayName("비소유자(bob)에게 전송된 masked meld")
    class NonOwnerMaskedMeld {

        @Test
        @DisplayName("extensions Map 변이 시 원본 meld 의 extensions Map 은 변경되지 않는다")
        void maskMeldsFor_nonOwner_extensionsIsDefensiveCopy_mapMutationDoesNotAffectOriginal() {
            Meld originalMeld = buildMeldWithExtensions();
            RoundState state = buildRoundState(originalMeld);

            RoundState sentState = captureRoundStateSentToBob(state);
            Meld sentMeld = sentState.getTableMelds().get(0);
            Map<String, List<Card>> sentExtensions = sentMeld.getExtensions();

            // masked meld 의 Map 을 변이 (새 엔트리 추가)
            sentExtensions.put("charlie", new ArrayList<>());

            // 원본 meld extensions 는 bob 키만 존재해야 함
            assertThat(originalMeld.getExtensions()).containsOnlyKeys(BOB);
        }

        @Test
        @DisplayName("extensions 내부 카드 List 변이 시 원본 meld 의 카드 List 는 변경되지 않는다")
        void maskMeldsFor_nonOwner_extensionsCardListIsDefensiveCopy_listMutationDoesNotAffectOriginal() {
            Meld originalMeld = buildMeldWithExtensions();
            RoundState state = buildRoundState(originalMeld);

            RoundState sentState = captureRoundStateSentToBob(state);
            Meld sentMeld = sentState.getTableMelds().get(0);
            List<Card> sentCardList = sentMeld.getExtensions().get(BOB);

            // masked meld 의 카드 리스트를 비워버림
            sentCardList.clear();

            // 원본 meld 의 bob 리스트는 카드 1장을 그대로 유지해야 함
            assertThat(originalMeld.getExtensions().get(BOB)).hasSize(1);
        }

        @Test
        @DisplayName("masked meld 의 extensions 는 원본과 동일한 카드 데이터를 포함한다")
        void maskMeldsFor_nonOwner_extensionsContainsSameCards() {
            Meld originalMeld = buildMeldWithExtensions();
            RoundState state = buildRoundState(originalMeld);

            RoundState sentState = captureRoundStateSentToBob(state);
            Meld sentMeld = sentState.getTableMelds().get(0);
            List<Card> sentCardList = sentMeld.getExtensions().get(BOB);

            assertThat(sentCardList).hasSize(1);
            assertThat(sentCardList.get(0)).isEqualTo(ACE_OF_HEARTS);
        }
    }

    // ================================================================
    // Nested: 소유자(alice) — 원본 인스턴스 그대로 반환
    // ================================================================

    @Nested
    @DisplayName("소유자(alice)에게 전송된 meld")
    class OwnerMeld {

        @Test
        @DisplayName("소유자에게 전송된 meld 는 원본과 동일한 인스턴스이다")
        void maskMeldsFor_owner_returnsSameMeldInstance() {
            Meld originalMeld = buildMeldWithExtensions();
            RoundState state = buildRoundState(originalMeld);

            // alice 에게 전송되도록 설정
            when(sessionManager.getUserList(ROOM_ID)).thenReturn(List.of(ALICE));

            gameBroadcastService.broadcastRoundState(ROOM_ID, "ROUND_STATE", ALICE, state);

            ArgumentCaptor<GameMessage> captor = ArgumentCaptor.forClass(GameMessage.class);
            verify(messagingTemplate).convertAndSend(anyString(), captor.capture());
            RoundState sentState = (RoundState) captor.getValue().payload();
            Meld sentMeld = sentState.getTableMelds().get(0);

            // 소유자에게는 복사본이 아닌 원본 인스턴스가 전달된다
            assertThat(sentMeld).isSameAs(originalMeld);
        }
    }
}
