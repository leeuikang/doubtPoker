package org.doubt.dto;

import org.doubt.constant.Rank;
import org.doubt.constant.Suit;

/**
 * 실제 카드 (52장 표준 덱)
 */
public record Card(Suit suit, Rank rank) {
}