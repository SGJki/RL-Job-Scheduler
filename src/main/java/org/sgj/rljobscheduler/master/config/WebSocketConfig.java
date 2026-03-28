package org.sgj.rljobscheduler.master.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用内存消息代理，客户端订阅以 /topic 开头的消息
        config.enableSimpleBroker("/topic");
        // 客户端发送消息的前缀 (本阶段暂时用不到，因为主要是服务器推)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册 STOMP 端点，前端连接这个 URL
        registry.addEndpoint("/ws")
                // 允许跨域 (方便调试，生产环境可限制)
                .setAllowedOriginPatterns("*")
                // 启用 SockJS 支持 (浏览器不支持 WebSocket 时自动降级)
                .withSockJS();
    }
}