package org.doubt.dto;

import lombok.Getter;
import lombok.Setter;
import org.doubt.constant.MeldType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 테이블 위의 멜드 한 세트
 * - actualCards  : 실제 카드 목록 (서버에서만 알고 있음)
 * - declaredCards: 플레이어가 선언한 카드 목록 (클라이언트에 공개)
 * - extensions   : 다른 플레이어가 붙인 카드 목록 (playerId → 카드 목록)
 * - isBluff      : 거짓말 멜드 여부
 */
@Getter
@Setter
public class Meld {

    private String id;
    private String ownerId;
    private MeldType type;
    private List<Card> actualCards;
    private List<DeclaredCard> declaredCards;
    private Map<String, List<Card>> extensions; // playerId → 붙인 카드들
    private boolean isBluff;

    /** 신규 멜드 생성 팩토리. extensions 는 빈 맵으로 초기화된다. */
    public static Meld create(String id, String ownerId, MeldType type,
                               List<Card> actualCards, List<DeclaredCard> declaredCards,
                               boolean isBluff) {
        Meld meld = new Meld();
        meld.id = id;
        meld.ownerId = ownerId;
        meld.type = type;
        meld.actualCards = actualCards;
        meld.declaredCards = declaredCards;
        meld.isBluff = isBluff;
        meld.extensions = new LinkedHashMap<>();
        return meld;
    }
}