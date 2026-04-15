package org.doubt.service;

import org.doubt.constant.MeldType;
import org.doubt.constant.Rank;
import org.doubt.constant.Suit;
import org.doubt.dto.Card;
import org.doubt.dto.DeclaredCard;
import org.doubt.dto.Meld;
import org.doubt.handler.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-M5 보안 취약점 수정 검증 테스트
 *
 * <p>원래 취약점(SEC-M5): {@code maskMeldsFor()} 에서 비소유자에게 전달되는 마스킹된 멜드의
 * {@code extensions} 맵 값(카드 목록)에 실제 카드가 그대로 노출되었다.</p>
 *
 * <p>수정 후: 확장자 ID(키)는 유지하되, 카드 목록(값)은 빈 리스트({@code List.of()})로 교체한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SEC-M5 수정 검증: 비소유자에게 extensions 실제 카드가 노출되지 않는다")
class SecM5ExtensionMaskTest {

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @Mock
    private SessionManager sessionManager;

    @InjectMocks
    private GameBroadcastService gameBroadcastService;

    /** 테스트용 멜드를 생성하는 헬퍼. ownerId = "Alice", Bob이 ACE of SPADE로 확장. */
    private Meld buildMeldWithExtension() {
        List<Card> actualCards = List.of(new Card(Suit.SPADE, Rank.ACE));
        List<DeclaredCard> declaredCards = List.of(new DeclaredCard(Suit.SPADE, Rank.ACE));

        Meld meld = Meld.create("meld-1", "Alice", MeldType.SET, actualCards, declaredCards, false);
        meld.getExtensions().put("Bob", List.of(new Card(Suit.SPADE, Rank.ACE)));
        return meld;
    }

    /** private maskMeldsFor 를 ReflectionTestUtils 로 호출하는 헬퍼. */
    @SuppressWarnings("unchecked")
    private List<Meld> invokeMaskMeldsFor(List<Meld> melds, String viewerNickname) {
        return (List<Meld>) ReflectionTestUtils.invokeMethod(
                gameBroadcastService, "maskMeldsFor", melds, viewerNickname);
    }

    // ----------------------------------------------------------------
    // 비소유자 마스킹 검증
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("비소유자(Charlie)로 마스킹")
    class NonOwnerMasking {

        @Test
        @DisplayName("비소유자에게 전달된 extensions 카드 목록은 빈 리스트이다")
        void non_owner_extensions_cards_are_empty() {
            Meld meld = buildMeldWithExtension();
            List<Meld> masked = invokeMaskMeldsFor(List.of(meld), "Charlie");

            assertThat(masked).hasSize(1);
            List<Card> bobCards = masked.get(0).getExtensions().get("Bob");
            assertThat(bobCards)
                    .as("비소유자에게는 Bob의 extensions 카드 목록이 비어있어야 한다")
                    .isEmpty();
        }

        @Test
        @DisplayName("비소유자에게 전달된 extensions 에서 확장자 ID(키)는 유지된다")
        void non_owner_extension_keys_are_preserved() {
            Meld meld = buildMeldWithExtension();
            List<Meld> masked = invokeMaskMeldsFor(List.of(meld), "Charlie");

            assertThat(masked).hasSize(1);
            assertThat(masked.get(0).getExtensions().keySet())
                    .as("확장자 ID 'Bob' 키는 마스킹 후에도 보존되어야 한다")
                    .contains("Bob");
        }

        @Test
        @DisplayName("비소유자에게 전달된 actualCards 는 빈 리스트이다")
        void non_owner_cannot_see_actualCards() {
            List<Card> actualCards = List.of(new Card(Suit.HEART, Rank.KING));
            List<DeclaredCard> declaredCards = List.of(new DeclaredCard(Suit.HEART, Rank.KING));
            Meld meld = Meld.create("meld-2", "Alice", MeldType.SET, actualCards, declaredCards, false);

            List<Meld> masked = invokeMaskMeldsFor(List.of(meld), "Charlie");

            assertThat(masked).hasSize(1);
            assertThat(masked.get(0).getActualCards())
                    .as("비소유자에게는 actualCards 가 빈 리스트로 마스킹되어야 한다")
                    .isEmpty();
        }
    }

    // ----------------------------------------------------------------
    // 소유자 마스킹 검증
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("소유자(Alice)로 조회 — 원본 반환")
    class OwnerViewMasking {

        @Test
        @DisplayName("소유자가 조회하면 extensions 의 실제 카드가 그대로 반환된다")
        void owner_sees_real_extension_cards() {
            Meld meld = buildMeldWithExtension();
            Card extensionCard = new Card(Suit.SPADE, Rank.ACE);

            List<Meld> masked = invokeMaskMeldsFor(List.of(meld), "Alice");

            assertThat(masked).hasSize(1);
            List<Card> bobCards = masked.get(0).getExtensions().get("Bob");
            assertThat(bobCards)
                    .as("소유자에게는 Bob의 extensions 실제 카드가 노출되어야 한다")
                    .isNotEmpty()
                    .contains(extensionCard);
        }
    }

    // ----------------------------------------------------------------
    // 방어적 복사 검증
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("마스킹된 extensions 맵은 원본과 독립적이다")
    class DefensiveCopyMasking {

        @Test
        @DisplayName("원본 extensions 를 수정해도 마스킹된 멜드의 extensions 에 영향이 없다")
        void masked_extensions_map_is_independent_copy() {
            Meld meld = buildMeldWithExtension();
            List<Meld> maskedList = invokeMaskMeldsFor(List.of(meld), "Bob");

            // 원본 extensions 에 새 항목 추가
            meld.getExtensions().put("Eve", List.of(new Card(Suit.CLUB, Rank.QUEEN)));

            Map<String, List<Card>> maskedExtensions = maskedList.get(0).getExtensions();
            assertThat(maskedExtensions)
                    .as("원본 extensions 를 수정해도 마스킹 결과 맵에 영향이 없어야 한다")
                    .doesNotContainKey("Eve");
        }
    }
}
