package com.ordering.service;

import com.ordering.entity.Order;
import com.ordering.feishu.FeishuBitableClient;
import com.ordering.repository.OrderRepository;
import com.ordering.websocket.OrderWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书 ↔ 后端 双向同步服务
 * 
 * 定时从飞书 Bitable 拉取订单，与 MySQL 比对后：
 *   - 新订单 → 创建到 MySQL
 *   - 状态变更 → 更新 MySQL + WS 推前端
 *   - 反向同步环保护（标记正在同步的订单号）
 */
@Component
public class FeishuSyncService {

    private static final Logger log = LoggerFactory.getLogger(FeishuSyncService.class);

    private final FeishuBitableClient feishuClient;
    private final OrderRepository orderRepository;
    private final OrderWebSocketHandler webSocketHandler;

    private final Set<String> syncingOrders = ConcurrentHashMap.newKeySet();

    public FeishuSyncService(FeishuBitableClient feishuClient, OrderRepository orderRepository,
                             OrderWebSocketHandler webSocketHandler) {
        this.feishuClient = feishuClient;
        this.orderRepository = orderRepository;
        this.webSocketHandler = webSocketHandler;
    }

    @PostConstruct
    public void init() {
        log.info("飞书同步服务启动 (间隔30s)");
    }

    /**
     * 定时从飞书拉取订单，检测状态变更和新订单
     * 可由 Webhook 或手动 /api/webhook/sync/now 触发
     *
     * ⚠️ 不在类级别加 @Transactional，每条订单单独事务避免单条失败滚全部
     */
    @Scheduled(fixedRate = 30000)
    public void syncOrdersFromFeishu() {
        try {
            var feishuOrders = feishuClient.listOrdersFromBitable();
            if (feishuOrders == null || feishuOrders.isEmpty()) return;

            for (var snapshot : feishuOrders) {
                String orderNo = snapshot.orderNo;
                if (orderNo == null) continue;

                // 防止同步环：标记正在同步中
                if (!syncingOrders.add(orderNo)) continue;

                try {
                    processOneOrder(snapshot);
                } catch (Exception e) {
                    log.warn("订单 {} 同步失败，已跳过: {}", orderNo, e.getMessage());
                } finally {
                    syncingOrders.remove(orderNo);
                }
            }
        } catch (Exception e) {
            log.debug("飞书同步扫描异常(若无配置属正常): {}", e.getMessage());
        }
    }

    public void processOneOrder(FeishuBitableClient.OrderSnapshot snapshot) {
        String orderNo = snapshot.orderNo;
        var opt = orderRepository.findByOrderNo(orderNo);
        if (opt.isPresent()) {
            handleStatusChange(opt.get(), snapshot);
        } else {
            handleNewOrder(snapshot);
        }
    }

    private void handleStatusChange(Order dbOrder, FeishuBitableClient.OrderSnapshot snapshot) {
        if (snapshot.status == null) return;
        if (dbOrder.getStatus().equals(snapshot.status)) return;
        if (!isValidTransition(dbOrder.getStatus(), snapshot.status)) {
            log.debug("跳过非法状态转换: {} {} → {}", snapshot.orderNo, dbOrder.getStatus(), snapshot.status);
            return;
        }

        log.info("飞书同步状态变更: {} {} → {}", snapshot.orderNo, dbOrder.getStatus(), snapshot.status);
        dbOrder.setStatus(snapshot.status);
        dbOrder.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(dbOrder);
        webSocketHandler.notifyOrderStatus(snapshot.orderNo, snapshot.status);
    }

    /** 简单校验一个字符串是否为合法 JSON 数组 */
    private boolean isValidJsonArray(String s) {
        if (s == null || s.isBlank()) return false;
        s = s.trim();
        return s.startsWith("[") && s.endsWith("]");
    }

    private void handleNewOrder(FeishuBitableClient.OrderSnapshot snapshot) {
        if (!isValidJsonArray(snapshot.items)) {
            log.warn("飞书订单菜品明细非合法 JSON，跳过创建: {} (items={})", snapshot.orderNo,
                    snapshot.items != null ? snapshot.items.substring(0, Math.min(50, snapshot.items.length())) : "null");
            return;
        }

        log.info("飞书同步发现新订单: {}", snapshot.orderNo);
        Order order = new Order();
        order.setOrderNo(snapshot.orderNo);
        order.setTableNo(snapshot.tableNo != null ? snapshot.tableNo : "");
        order.setItems(snapshot.items);
        order.setTotalPrice(snapshot.totalPrice != null ? snapshot.totalPrice : 0);
        order.setStatus(snapshot.status != null ? snapshot.status : "CREATED");
        order.setNote(snapshot.note != null ? snapshot.note : "");
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        orderRepository.save(order);
        log.info("飞书新订单已同步到 MySQL: {}", snapshot.orderNo);
        webSocketHandler.notifyOrderStatus(snapshot.orderNo, "CREATED");
    }

    /**
     * 校验状态转换合法性
     */
    private boolean isValidTransition(String from, String to) {
        var validNext = Map.of(
                "CREATED", Set.of("PREPARING"),
                "PREPARING", Set.of("PENDING_CONFIRM"),
                "PENDING_CONFIRM", Set.of("CONFIRMED"),
                "CONFIRMED", Set.of("CLOSED", "VERIFIED")
        );
        return validNext.getOrDefault(from, Set.of()).contains(to);
    }
}
