package org.doubt.config;

import lombok.RequiredArgsConstructor;
import org.doubt.constant.AppConstants;
import org.doubt.interceptor.ChatRateLimitInterceptor;
import org.doubt.interceptor.StompAuthInterceptor;
import org.doubt.interceptor.StompLogInterceptor;
import org.doubt.interceptor.StompOutboundLogInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor stompAuthInterceptor;
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
        registry.addEndpoint("/websocket").setAllowedOrigins(AppConstants.ALLOWED_ORIGINS).withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 순서: 인증 → 로깅 → 레이트리밋
        registration.interceptors(stompAuthInterceptor, stompLogInterceptor, chatRateLimitInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompOutboundLogInterceptor);
    }

    @Bean
    public StompSubProtocolErrorHandler stompSubProtocolErrorHandler() {
        return new AuthStompErrorHandler();
    }
}
