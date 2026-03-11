package org.doubt.constant;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChipStatus {
    DEFAULT(1000);

    private final int value;
}
