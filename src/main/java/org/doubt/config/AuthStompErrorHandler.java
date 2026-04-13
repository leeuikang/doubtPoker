package org.doubt.config;

import lombok.extern.slf4j.Slf4j;
import org.doubt.exception.GameException;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;

/**
 * ChannelInterceptor.preSend()에서 발생한 예외를 STOMP ERROR 프레임으로 변환 (H-2)
 *
 * @MessageExceptionHandler는 @MessageMapping 내부 예외만 처리하므로,
 * StompAuthInterceptor에서 던진 GameException은 이 핸들러로 처리한다.
 * 클라이언트는 STOMP ERROR 프레임으로 인증 실패 원인을 수신한다.
 */
@Slf4j
public class AuthStompErrorHandler extends StompSubProtocolErrorHandler {

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;

        if (cause instanceof GameException gameException) {
            String errorCode = gameException.getErrorCode().name();
            log.warn("STOMP auth error: {}", errorCode);
            // 내부 예외 메시지 대신 ErrorCode 이름만 전송 — 내부 정보 노출 방지 (R2-W7)
            return errorFrame(errorCode);
        }

        log.error("STOMP unexpected error", ex);
        // 예상치 못한 예외는 generic 메시지 반환 — 스택 트레이스 등 내부 정보 노출 방지 (R2-W7)
        return errorFrame("INTERNAL_SERVER_ERROR");
    }

    private Message<byte[]> errorFrame(String message) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(message);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(
                message.getBytes(StandardCharsets.UTF_8),
                accessor.getMessageHeaders()
        );
    }
}
