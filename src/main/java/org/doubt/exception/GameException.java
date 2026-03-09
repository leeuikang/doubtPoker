package org.doubt.exception;

import lombok.Getter;
import org.doubt.constant.ErrorCode;

@Getter
public class GameException extends RuntimeException{

    private final ErrorCode errorCode;

    public GameException(ErrorCode errorCode){
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
