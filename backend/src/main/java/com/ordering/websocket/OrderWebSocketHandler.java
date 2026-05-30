package com.ordering.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 处理器
 * 客户端连接后订阅订单状态变更通知
 * 连接路径: /ws/order?orderNo=ORDxxx
 */
@Component
public class OrderWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderWebSocketHandler.class);

    // orderNo -> session
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String query = session.getUri().getQuery();
        String orderNo = parseParam(query, "orderNo");
        if (orderNo != null) {
            sessions.put(orderNo, session);
            log.debug("WS连接: orderNo={}", orderNo);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.entrySet().removeIf(e -> e.getValue().equals(session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端发来的心跳，忽略
    }

    /**
     * 对外推送：当订单状态变更时调用此方法
     */
    public void notifyOrderStatus(String orderNo, String status) {
        WebSocketSession session = sessions.get(orderNo);
        if (session != null && session.isOpen()) {
            try {
                String payload = "{\"orderNo\":\"" + orderNo + "\",\"status\":\"" + status + "\"}";
                session.sendMessage(new TextMessage(payload));
                log.info("WS推送成功: {} -> {}", orderNo, status);
            } catch (IOException e) {
                log.warn("WS推送失败: {}", e.getMessage());
                sessions.remove(orderNo);
            }
        } else {
            log.debug("WS无连接客户端，跳过推送: {} -> {}", orderNo, status);
        }
    }

    private String parseParam(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return kv[1];
            }
        }
        return null;
    }
}
