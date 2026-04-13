package org.doubt.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReconnectRegistry")
class ReconnectRegistryTest {

    private ReconnectRegistry reconnectRegistry;

    @BeforeEach
    void setUp() {
        reconnectRegistry = new ReconnectRegistry();
    }

    // ----------------------------------------------------------------
    // track + verifyGuestId
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("verifyGuestId")
    class VerifyGuestId {

        @Test
        @DisplayName("track 후 동일한 guestId 로 verifyGuestId 하면 true 를 반환한다")
        void track_then_verify_with_matching_guestId_returns_true() {
            String nickname = "홍길동";
            String roomId = "room-1";
            String guestId = "guest-uuid-abc";

            reconnectRegistry.track(nickname, roomId, guestId);

            assertThat(reconnectRegistry.verifyGuestId(nickname, guestId)).isTrue();
        }

        @Test
        @DisplayName("track 후 다른 guestId 로 verifyGuestId 하면 false 를 반환한다")
        void track_then_verify_with_wrong_guestId_returns_false() {
            String nickname = "홍길동";
            String roomId = "room-1";
            String guestId = "guest-uuid-abc";
            String wrongGuestId = "guest-uuid-XYZ";

            reconnectRegistry.track(nickname, roomId, guestId);

            assertThat(reconnectRegistry.verifyGuestId(nickname, wrongGuestId)).isFalse();
        }

        @Test
        @DisplayName("track 하지 않은 닉네임으로 verifyGuestId 하면 false 를 반환한다")
        void verify_without_track_returns_false() {
            assertThat(reconnectRegistry.verifyGuestId("미등록유저", "any-guest-id")).isFalse();
        }

        @Test
        @DisplayName("clear 후 verifyGuestId 하면 false 를 반환한다")
        void after_clear_verifyGuestId_returns_false() {
            String nickname = "홍길동";
            String guestId = "guest-uuid-abc";

            reconnectRegistry.track(nickname, "room-1", guestId);
            reconnectRegistry.clear(nickname);

            assertThat(reconnectRegistry.verifyGuestId(nickname, guestId)).isFalse();
        }
    }

    // ----------------------------------------------------------------
    // findRoom
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("findRoom")
    class FindRoom {

        @Test
        @DisplayName("track 후 findRoom 하면 저장된 roomId 를 반환한다")
        void track_then_findRoom_returns_roomId() {
            String nickname = "alice";
            String roomId = "room-42";

            reconnectRegistry.track(nickname, roomId, "some-guest-id");

            assertThat(reconnectRegistry.findRoom(nickname)).contains(roomId);
        }

        @Test
        @DisplayName("clear 후 findRoom 하면 empty 를 반환한다")
        void after_clear_findRoom_returns_empty() {
            String nickname = "alice";

            reconnectRegistry.track(nickname, "room-42", "some-guest-id");
            reconnectRegistry.clear(nickname);

            assertThat(reconnectRegistry.findRoom(nickname)).isEmpty();
        }

        @Test
        @DisplayName("등록하지 않은 닉네임으로 findRoom 하면 empty 를 반환한다")
        void untracked_nickname_findRoom_returns_empty() {
            assertThat(reconnectRegistry.findRoom("미등록유저")).isEmpty();
        }
    }
}
