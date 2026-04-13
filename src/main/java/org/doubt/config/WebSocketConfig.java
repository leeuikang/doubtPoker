package org.doubt.config;

import lombok.RequiredArgsConstructor;
import org.doubt.constant.AppConstants;
import org.springframework.beans.factory.annotation.Value;
import org.doubt.interceptor.ChatRateLimitInterceptor;
import org.doubt.interceptor.CsrfHandshakeInterceptor;
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

    /** 개발 환경 추가 허용 출처 (프로덕션에서는 빈 문자열) — application[-prod].yml 에서 제어 (R2-I2) */
    @Value("${app.cors.extra-origins:}")
    private String corsExtraOrigins;

    private final StompAuthInterceptor stompAuthInterceptor;
    private final StompLogInterceptor stompLogInterceptor;
    private final StompOutboundLogInterceptor stompOutboundLogInterceptor;
    private final ChatRateLimitInterceptor chatRateLimitInterceptor;
    private final CsrfHandshakeInterceptor csrfHandshakeInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");

        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry){
        registry.addEndpoint("/websocket")
                .setAllowedOrigins(buildAllowedOrigins())
                .addInterceptors(csrfHandshakeInterceptor)
                .withSockJS();
    }

    /**
     * 허용 출처 배열을 구성한다.
     * 프로덕션({@code app.cors.extra-origins} 빈 값)이면 {@link AppConstants#PROD_ORIGIN}만 포함한다.
     */
    private String[] buildAllowedOrigins() {
        if (corsExtraOrigins == null || corsExtraOrigins.isBlank()) {
            return new String[]{ AppConstants.PROD_ORIGIN };
        }
        return new String[]{ AppConstants.PROD_ORIGIN, corsExtraOrigins };
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
