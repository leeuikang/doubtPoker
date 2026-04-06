package org.doubt.config;

import lombok.RequiredArgsConstructor;
import org.doubt.interceptor.ChatRateLimitInterceptor;
import org.doubt.interceptor.StompLogInterceptor;
import org.doubt.interceptor.StompOutboundLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompLogInterceptor stompLogInterceptor;
    private final StompOutboundLogInterceptor stompOutboundLogInterceptor;
    private final ChatRateLimitInterceptor chatRateLimitInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");

        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry){
        registry.addEndpoint("/websocket").setAllowedOrigins("*").withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompLogInterceptor, chatRateLimitInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompOutboundLogInterceptor);
    }
}
