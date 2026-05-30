package com.ordering.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class OrderWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderWebSocketHandler.class);

    // 所有已连接会话（线程安全）
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WS连接: {} (当前在线: {})", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WS断开: {} (当前在线: {})", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端发来的消息（预留）
    }

    /**
     * 广播订单状态变更给所有连接的客户端
     */
    public void notifyOrderStatus(String orderNo, String status) {
        String payload = "{\"orderNo\":\"" + orderNo + "\",\"status\":\"" + status + "\"}";
        TextMessage msg = new TextMessage(payload);
        int sent = 0;

        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(msg);
                    sent++;
                } catch (IOException e) {
                    log.warn("WS推送失败: session={}, {}", session.getId(), e.getMessage());
                    sessions.remove(session);
                }
            } else {
                sessions.remove(session);
            }
        }

        if (sent > 0) {
            log.info("WS广播: {} -> {} (送达{}个客户端)", orderNo, status, sent);
        } else {
            log.debug("WS跳过广播: 无在线客户端");
        }
    }
}
