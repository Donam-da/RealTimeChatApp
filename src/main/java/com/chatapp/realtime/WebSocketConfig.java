package com.chatapp.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // Bật tính năng Message Broker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Điểm kết nối để phía Frontend (Client) gõ cửa vào phòng chat
        registry.addEndpoint("/ws").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Tiền tố để client gửi tin nhắn tới (ví dụ: /app/chat)
        registry.setApplicationDestinationPrefixes("/app");
        // Chủ đề (Topic) để server đẩy tin nhắn về cho tất cả mọi người
        registry.enableSimpleBroker("/topic");
    }
}