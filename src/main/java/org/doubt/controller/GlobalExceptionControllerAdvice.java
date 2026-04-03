package org.doubt.controller;

import lombok.extern.slf4j.Slf4j;
import org.doubt.constant.ErrorCode;
import org.doubt.dto.ErrorMessage;
import org.doubt.dto.GameMessage;
import org.doubt.exception.GameException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@Slf4j
@ControllerAdvice
public class GlobalExceptionControllerAdvice {

    @MessageExceptionHandler(GameException.class)
    @SendToUser("/queue/errors")
    public GameMessage handleGameException(GameException e){
        log.warn("Game Logic Error: {}", e.getErrorCode().name());
        return new GameMessage("ERROR", null, "SERVER",
                ErrorMessage.of(e.getErrorCode().name(), e.getErrorCode().getMessage()));
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public GameMessage handleException(Exception e){
        log.error("System Error", e);
        return new GameMessage("ERROR", null, "SERVER",
                ErrorMessage.of(ErrorCode.INTERNAL_SERVER_ERROR.name(), ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
