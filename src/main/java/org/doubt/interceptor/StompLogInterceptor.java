package org.doubt.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StompLogInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        String destination = accessor.getDestination();

        if(StompCommand.SEND.equals(command)){
            String sessionId = accessor.getSessionId();
            String payload = new String((byte[]) message.getPayload());
            log.info("Client connected: sessionId={}, destination={}, payload={}", sessionId, destination, payload);
        }else if(StompCommand.MESSAGE.equals(command)){
            log.info("OutBound Dest:{}", destination);
        }

        return message;
    }

}
