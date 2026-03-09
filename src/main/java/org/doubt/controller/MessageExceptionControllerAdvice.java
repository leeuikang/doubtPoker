package org.doubt.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.doubt.constant.ErrorCode;
import org.doubt.dto.ErrorMessage;
import org.doubt.dto.GameMessage;
import org.doubt.exception.GameException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class MessageExceptionControllerAdvice {

    private final SimpMessagingTemplate messagingTemplate;

    @MessageExceptionHandler(GameException.class)
    @SendToUser("/queue/errors")
    public GameMessage handleGameException(GameException e){
        log.error("Game Logic Error: {}",e.getMessage());
        return new GameMessage("ERROR", "SYSTEM","SERVER", e.getErrorCode().getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public ErrorMessage handleException(Exception e){
        log.error("System Error: {}",e.getMessage());
        return ErrorMessage.of(String.valueOf(ErrorCode.INTERNAL_SERVER_ERROR), "서버 내부 오류가 발생했습니다.");
    }
}
